package org.olf

import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j
import com.k_int.okapi.OkapiTenantAwareController
import org.olf.erm.SubscriptionAgreement
import org.olf.kb.Pkg
import org.olf.kb.PackageContentItem
import org.olf.kb.PlatformTitleInstance
import grails.converters.JSON
import org.olf.kb.Work
import org.olf.kb.http.request.body.HeirarchicalDeletePCIBody
import org.springframework.http.HttpStatus

/**
 * Explore package content items - the KB
 */
@Slf4j
@CurrentTenant
class PackageContentItemController extends OkapiTenantAwareController<PackageContentItem>  {

  PackageContentItemController() {
    super(PackageContentItem)
  }

  PackageContentItemDeletionService packageContentItemDeletionService;

  // TODO: Override POST and DELETE
  def heirarchicalDeletePCIs(HeirarchicalDeletePCIBody deleteBody) {
    log.info("Received request for hierarchical PCI delete. Body: {}", deleteBody.toString())

    if (deleteBody == null || deleteBody.hasErrors()) {
      log.warn("Validation failed for hierarchical delete request body: {}", deleteBody?.errors)
      response.status = HttpStatus.BAD_REQUEST.value()
      respond(deleteBody?.errors)
      return
    }

    List<String> idsToDelete = []
    try {
      if (deleteBody.pCIIds) {
        idsToDelete = deleteBody.pCIIds.findAll { String idStr ->
          idStr != null && !idStr.trim().isEmpty()
        }
      }
      if (idsToDelete.isEmpty()) {
        log.warn("No valid non-empty PCI IDs provided in the list.")
        response.status = HttpStatus.BAD_REQUEST.value() // 400
        render(contentType: 'application/json') { [message: "No valid IDs provided in the list."] }
        return
      }
    }
    catch (Exception e) {
      log.error("Error processing ID list: ${e.message}", e)
      response.status = HttpStatus.INTERNAL_SERVER_ERROR.value() // 500
      render(contentType: 'application/json') { [message: "Error processing provided IDs."] }
      return
    }


    // Get instances from DB based on IDs.
    try {

//      List<PackageContentItem> pciInstances = PackageContentItem.getAll(idsToDelete)
      Work work = packageContentItemDeletionService.heirarchicalDeletePCI(idsToDelete);
      if (work) {
        render(contentType: 'application/json', text: work as JSON)
      } else {
        log.warn("No PackageContentItem instances found for the provided IDs.")
      }

    } catch (Exception e) {
      log.error("Error during hierarchical PCI deletion for IDs {}: {}", idsToDelete, e.message, e)
      response.status = HttpStatus.INTERNAL_SERVER_ERROR.value()
      render(contentType: 'application/json') {
        [error: "Internal Server Error", message: "Failed to delete package content items: ${e.message}"]
      }
      return
    }

  }

}

