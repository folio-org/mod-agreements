package org.olf.PushKB

import org.olf.BaseSpec

import grails.gorm.multitenancy.Tenants
import grails.testing.mixin.integration.Integration
import spock.lang.*

@Integration
@Stepwise
@Requires({ instance.isPushKb() })
class ResyncPackageSpec extends BaseSpec {
  @Shared
  String oxfordPkgId

  @Unroll
  void "Set up pushed pkg" ( ) {
    when: 'We POST to pushPkg endpoint'
      def pushPkgBody = getDataFromFile("pkgBody1.json", "src/integration-test/resources/pushkb/pushPkg");
      Map resp = doPost("/erm/pushKB/pkg", pushPkgBody);
    then: 'All is well'
      resp.pushPkgResult.success == true
    when: 'Packages are fetched'
      Map pkgGet = doGet("/erm/packages?stats=true");
    then: 'We have the expected amount'
      pkgGet.total == 2635
    when: 'We look specifically for Oxford University Press: STM Collection 2017'
      Map pkgSingleGet = doGet("/erm/packages?filters=name=Oxford%20University%20Press%3A%20STM%20Collection%202017&stats=true");
      Map singlePkg = pkgSingleGet?.results?.getAt(0)
      oxfordPkgId = singlePkg?.id
    then: 'We have the expected package'
      pkgSingleGet.total == 1
      singlePkg.name == "Oxford University Press: STM Collection 2017"
      oxfordPkgId != null
      singlePkg.syncContentsFromSource == false
    when: 'Titles are fetched'
      Map tiGet = doGet("/erm/titles?stats=true");
      then: 'We have the expected amount'
      tiGet.total == 0
    when: 'Package metadata are fetched'
      Map pkgMetadataList = doGet("/erm/packages/metadata?stats=true");
    then: 'We see expected results'
      pkgMetadataList.total == 2635
    when: "Package metadata is fetched for ${oxfordPkgId}"
      Map pkgMetadata = doGet("/erm/packages/${oxfordPkgId}/metadata");
    then: 'We see expected results'
      pkgMetadata.ingressType == 'PUSHKB'
      pkgMetadata.resource.id == oxfordPkgId
      pkgMetadata.ingressId == "pkg-pushtask-id-1";
      pkgMetadata.ingressUrl == "pkg-pushkb-url-1";
      pkgMetadata.contentIngressId == null;
      pkgMetadata.contentIngressUrl == null;
  }

  @Unroll
  void "Test pushPCI" ( ) {
    when: 'We POST to pushPci endpoint'
      def pushBody = getDataFromFile("pciBody1.json", "src/integration-test/resources/pushkb/pushPci");
      Map resp = doPost("/erm/pushKB/pci", pushBody);
    then: 'All is well'
      resp.pushPCIResult.success == true
      resp.pushPCIResult.titleCount == 98
      resp.pushPCIResult.nonSyncedTitles == 98 // We're not syncing right now

    when: 'Packages are fetched'
      Map pkgGet = doGet("/erm/packages?stats=true");
    then: 'We have the expected amount'
      pkgGet.total == 2636
    when: 'Titles are fetched'
      Map tiGet = doGet("/erm/titles?stats=true");
    then: 'We have the expected amount'
      tiGet.total == 0
    when: 'Package metadata are fetched'
      Map pkgMetadataList = doGet("/erm/packages/metadata?stats=true");
    then: 'We see expected results'
      pkgMetadataList.total == 2636
    when: "Package metadata is fetched for ${oxfordPkgId}"
      Map pkgMetadata = doGet("/erm/packages/${oxfordPkgId}/metadata");
    then: 'We see expected results'
      pkgMetadata.ingressType == 'PUSHKB'
      pkgMetadata.resource.id == oxfordPkgId
      pkgMetadata.ingressId == "pkg-pushtask-id-1";
      pkgMetadata.ingressUrl == "pkg-pushkb-url-1";
      pkgMetadata.contentIngressId == "pci-pushtask-id-1";
      pkgMetadata.contentIngressUrl == "pci-pushkb-url-1";
  }

  @Unroll
  void "Control sync status of pkg" ( ) {
    when: 'We look up PackageTriggerResyncJobs in the system'
      Map ptrj = doGet('/erm/jobs?filters=class==org.olf.general.jobs.PackageTriggerResyncJob&stats=true')
    then: 'There are none'
      ptrj.total == 0;
    when: 'We POST to controlSync endpoint'
      Map controlSyncResp = doPost("/erm/packages/controlSync", [
        'packageIds': [oxfordPkgId],
        'syncState': 'SYNCHRONIZING'
      ]);
    then: "Responds as expected"
      controlSyncResp.packagesUpdated == 1
      controlSyncResp.packagesSkipped == 0
      controlSyncResp.success == true
    when: 'We subsequently fetch oxford package'
      Map oxfordPkg = doGet("/erm/packages/${oxfordPkgId}");
    then: 'Oxford package has sync status true'
      oxfordPkg.syncContentsFromSource == true;
    when: 'We re-look up PackageTriggerResyncJobs in the system'
      ptrj = doGet('/erm/jobs?filters=class==org.olf.general.jobs.PackageTriggerResyncJob&stats=true')
    then: 'There is one'
      ptrj.total == 1;
  }
}

