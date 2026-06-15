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

  private static final String RESOURCE_TYPE_PACKAGE = 'package'
  private static final String RESOURCE_TYPE_TITLE = 'title'

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
  void processExternalEntitlementsEholdings() {
    List<Entitlement> entitlements = findEholdingsEntitlementsWithoutResourceName()

    if (!entitlements) {
      log.info("No EXTERNAL Entitlements were found without resourceName")
      return
    }

    Map<String, List<Entitlement>> entitlementsByAuthority = entitlements.groupBy { it.authority }

    processBulkEholdings(
      entitlementsByAuthority[Entitlement.EKB_PACKAGE_AUTHORITY] ?: [],
      BULK_URI_PACKAGES,
      BULK_REQUEST_KEY_PACKAGES,
      RESOURCE_TYPE_PACKAGE
    )

    processBulkEholdings(
      entitlementsByAuthority[Entitlement.EKB_TITLE_AUTHORITY] ?: [],
      BULK_URI_RESOURCES,
      BULK_REQUEST_KEY_RESOURCES,
      RESOURCE_TYPE_TITLE
    )
  }

  @CompileStatic(SKIP)
  private void processBulkEholdings(List<Entitlement> entitlements, String bulkUri, String requestKey, String resourceType) {
    if (!entitlements) {
      return
    }

    entitlements.collate(BULK_CHUNK_SIZE).each { List<Entitlement> chunk ->
      def response = fetchBulkFromKbEbsco(chunk, bulkUri, requestKey, resourceType)
      if (response == null) {
        return // skip to next chunk; errors already logged
      }

      Map<String, String> nameByReference = (response?.included ?: [])
        .findAll { it?.id && it?.attributes?.name }
        .collectEntries { [(it.id): it.attributes.name] }

      Set<String> failedSet = (response?.meta?.failed?.getAt(requestKey) ?: [])
        .collect { it.toString() } as Set

      chunk.each { Entitlement ent ->
        String fetchedName = nameByReference[ent.reference]
        if (fetchedName) {
          ent.resourceName = fetchedName
          try {
            if (ent.save()) {
              log.info("resourceName for ${resourceType} with EKB ID: ${ent.reference} updated to ${fetchedName}")
            } else {
              log.error("Update failed on ${ent.id} for ${resourceType}:${ent.reference}. Error: ${ent.errors}")
            }
          } catch (Exception e) {
            log.error("Update failed on ${ent.id} for ${resourceType}:${ent.reference}. Error: ${e.message}")
          }
        } else if (failedSet.contains(ent.reference)) {
          log.error("Update failed on ${ent.id} for ${resourceType}:${ent.reference}. Error: returned in meta.failed by /eholdings bulk fetch")
        } else {
          log.info("resourceName for ${resourceType} with EKB ID: ${ent.reference} not updated")
        }
      }
    }
  }

  @CompileStatic(SKIP)
  private def fetchBulkFromKbEbsco(List<Entitlement> chunk, String bulkUri, String requestKey, String resourceType) {
    List<String> references = chunk*.reference
    try {
      return okapiClient.post(bulkUri, [(requestKey): references], null) {
        request.headers['Content-Type'] = JSON_API_CONTENT_TYPE
        request.headers['X-Okapi-User-Id'] = SYNTHETIC_USER_ID
      }
    } catch (Exception e) {
      String detail = (e instanceof HttpException)
        ? "Status: ${e.fromServer?.statusCode}, Body: ${e.body?.toString()}, Error: ${e.message}"
        : "Error: ${e.message}"
      chunk.each { Entitlement ent ->
        log.error("Update failed on ${ent.id} for ${resourceType}:${ent.reference}. ${detail}")
      }
      return null
    }
  }
}