package org.olf

import grails.gorm.transactions.Transactional

import grails.events.annotation.Subscriber
import grails.gorm.multitenancy.Tenants
import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import com.k_int.okapi.OkapiTenantResolver
import com.k_int.web.toolkit.settings.AppSetting
import com.k_int.web.toolkit.refdata.RefdataValue;

/**
 * This service works at the module level, it's often called without a tenant context.
 */
@Slf4j
@Transactional
public class ErmHousekeepingService {

  def coverageService
  def entitlementLogService
  def subscriptionAgreementCleanupService
  def remoteKbCleanupService

  public void triggerHousekeeping() {
    entitlementLogService.triggerUpdate();

    // A process to ensure the correct start/end date is stored per agreement
    subscriptionAgreementCleanupService.triggerDateCleanup();

    // A process to ensure any unused Org records are deleted
    subscriptionAgreementCleanupService.triggerOrgsCleanup();
    
    remoteKbCleanupService.checkLocal();
  }

  // @Subscriber('okapi:tenant_enabled')
  @Subscriber('okapi:dataload:reference')
  public void onLoadReference (final String tenantId, String value, final boolean existing_tenant, final boolean upgrading, final String toVersion, final String fromVersion) {
    log.debug("ErmHousekeepingService::onLoadReference(${tenantId},${value},${existing_tenant},${upgrading},${toVersion},${fromVersion})");
    final String tenant_schema_id = OkapiTenantResolver.getTenantSchemaName(tenantId)
    try {
      Tenants.withId(tenant_schema_id) {
        AppSetting.withTransaction {

          log.debug("Check app settings for file storage are in place");

          // Bootstrap refdata - controlled vocabulary of storage engines
          RefdataValue.lookupOrCreate('FileStorageEngines', 'LOB');
          RefdataValue.lookupOrCreate('FileStorageEngines', 'S3');
 
          // Bootstrap any app settings we may need
          [
            [ 'fileStorage', 'storageEngine', 'String', 'FileStorageEngines', 'LOB' ],
            [ 'fileStorage', 'S3Endpoint',    'String', null,                 'http://s3_endpoint_host.domain:9000' ],
            [ 'fileStorage', 'S3AccessKey',   'String', null,                 'ACCESS_KEY' ],
            [ 'fileStorage', 'S3SecretKey',   'String', null,                 'SECRET_KEY' ],
            [ 'fileStorage', 'S3BucketName',  'String', null,                 "${tenantId}-shared" ],
            [ 'fileStorage', 'S3ObjectPrefix','String', null,                 "/${tenantId}/agreements/" ],
          ].each { st_row ->
            log.debug("Check app setting ${st_row}");
  
            AppSetting new_as = AppSetting.findBySectionAndKey(st_row[0], st_row[1]) ?: new AppSetting(
                                              section:st_row[0],
                                              key:st_row[1],
                                              settingType:st_row[2],
                                              vocab:st_row[3],
                                              value:st_row[4]).save(flush:true, failOnError:true);
 
          }
        }
      }
    }
    catch ( Exception e ) {
      log.error("Problem with load reference",e);
    }
  }

}
