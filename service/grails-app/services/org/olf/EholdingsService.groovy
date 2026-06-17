package org.olf

import com.k_int.okapi.OkapiClient
import groovyx.net.http.HttpException

import org.olf.erm.Entitlement

import grails.gorm.transactions.Transactional
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import static groovy.transform.TypeCheckingMode.SKIP

@Slf4j
@CompileStatic
class EholdingsService {

  // mod-kb-ebsco bulk endpoints cap at 20 ids per request.
  private static final int BULK_CHUNK_SIZE = 20

  // mod-kb-ebsco enforces JSON:API spec
  private static final String JSON_API_CONTENT_TYPE = 'application/vnd.api+json'

  // mod-kb-ebsco-java's RequestHeadersUtil.userIdFuture requires X-Okapi-User-Id to be present
  private static final String SYNTHETIC_USER_ID = '00000000-0000-0000-0000-000000000001'

  private static final String BULK_URI_PACKAGES = '/eholdings/packages/bulk/fetch'
  private static final String BULK_URI_RESOURCES = '/eholdings/resources/bulk/fetch'

  private static final String BULK_REQUEST_KEY_PACKAGES = 'packages'
  private static final String BULK_REQUEST_KEY_RESOURCES = 'resources'

  // Thrown by the http client when okapi has no module providing the optional eholdings interface.
  private static final int HTTP_NOT_FOUND = 404

  OkapiClient okapiClient

  List<Entitlement> findEholdingsEntitlementsWithoutResourceName() {
    return Entitlement.executeQuery("""
          SELECT ent FROM Entitlement AS ent
          WHERE ent.type = :externalType
          AND ent.authority IN (:authorities)
          AND ent.reference IS NOT NULL
          AND ent.resourceName IS NULL""".toString(),
          [
            externalType: 'external',
            authorities: [Entitlement.EKB_PACKAGE_AUTHORITY, Entitlement.EKB_TITLE_AUTHORITY]
          ]) as List<Entitlement>
  }

  @Transactional
  void processEHoldingsEntitlements() {
    List<Entitlement> entitlements = findEholdingsEntitlementsWithoutResourceName()

    if (!entitlements) {
      log.info("No EXTERNAL Entitlements were found without resourceName")
      return
    }

    Map<String, List<Entitlement>> entitlementsByAuthority = entitlements.groupBy { it.authority }

    processBulkEholdings(
      entitlementsByAuthority[Entitlement.EKB_PACKAGE_AUTHORITY] ?: [],
      BULK_URI_PACKAGES,
      BULK_REQUEST_KEY_PACKAGES
    )

    processBulkEholdings(
      entitlementsByAuthority[Entitlement.EKB_TITLE_AUTHORITY] ?: [],
      BULK_URI_RESOURCES,
      BULK_REQUEST_KEY_RESOURCES
    )
  }

  @CompileStatic(SKIP)
  private void processBulkEholdings(List<Entitlement> entitlements, String bulkUri, String requestKey) {
    if (!entitlements) {
      return
    }

    String type = resourceLabel(requestKey)

    // 1. Chunk — mod-kb-ebsco caps the bulk endpoint at BULK_CHUNK_SIZE ids per request.
    entitlements.collate(BULK_CHUNK_SIZE).each { List<Entitlement> chunk ->
      // 2. Fetch metadata for this chunk. null == chunk-level failure (errors already logged).
      def response = fetchBulkFromKbEbsco(chunk, bulkUri, requestKey)
      if (response == null) {
        return
      }

      // 3. Index returned names by EKB id (== Entitlement.reference) for O(1) lookup.
      Map<String, String> nameByReference = (response?.included ?: [])
        .findAll { it?.id && it?.attributes?.name }
        .collectEntries { [(it.id): it.attributes.name] }

      // 4. Collect ids that kb explicitly failed on (meta.failed.<requestKey>).
      Set<String> failedSet = (response?.meta?.failed?.getAt(requestKey) ?: [])
        .collect { it.toString() } as Set

      // 5. For each entitlement decide its fate.
      chunk.each { Entitlement ent ->
        String fetchedName = nameByReference[ent.reference]
        if (fetchedName) {
          ent.resourceName = fetchedName
          try {
            if (ent.save(flush: true)) {
              log.info("resourceName for ${type} entitlement ${ent.id} with EKB ID: ${ent.reference} updated to ${fetchedName}")
            } else {
              log.error("Update failed on ${type} entitlement ${ent.id} with EKB ID: ${ent.reference}. Error: ${ent.errors}")
            }
          } catch (Exception e) {
            log.error("Update failed on ${type} entitlement ${ent.id} with EKB ID: ${ent.reference}. Error: ${e.message}")
          }
        } else if (failedSet.contains(ent.reference)) {
          // kb explicitly rejected this id.
          log.error("Update failed on ${type} entitlement ${ent.id} with EKB ID: ${ent.reference}. Error: returned in meta.failed by /eholdings bulk fetch")
        } else {
          // No name and no failure entry — kb stayed silent. Try again next run.
          log.info("resourceName for ${type} entitlement ${ent.id} with EKB ID: ${ent.reference} not updated")
        }
      }
    }
  }

  @CompileStatic(SKIP)
  private def fetchBulkFromKbEbsco(List<Entitlement> chunk, String bulkUri, String requestKey) {
    String type = resourceLabel(requestKey)
    List<String> references = chunk*.reference
    try {
      return okapiClient.post(bulkUri, [(requestKey): references], null) {
        request.headers['Content-Type'] = JSON_API_CONTENT_TYPE
        request.headers['X-Okapi-User-Id'] = SYNTHETIC_USER_ID
      }
    } catch (HttpException e) {
      // The eholdings interface is optional. If no module provides it, every bulk POST 404s.
      // Log once per chunk at a higher level and skip — no point logging per entitlement.
      if (e.statusCode == HTTP_NOT_FOUND) {
        log.error("Skipping ${requestKey} bulk fetch: ${bulkUri} returned 404. eholdings interface is likely not provided by any module.")
        return null
      }
      String detail = "Status: ${e.statusCode}, Body: ${e.body?.toString()}, Error: ${e.message}"
      chunk.each { Entitlement ent ->
        log.error("Update failed on ${type} entitlement ${ent.id} with EKB ID: ${ent.reference}. ${detail}")
      }
      return null
    } catch (Exception e) {
      chunk.each { Entitlement ent ->
        log.error("Update failed on ${type} entitlement ${ent.id} with EKB ID: ${ent.reference}. Error: ${e.message}")
      }
      return null
    }
  }

  private static String resourceLabel(String requestKey) {
    return requestKey == BULK_REQUEST_KEY_PACKAGES ? 'package' : 'title'
  }
}