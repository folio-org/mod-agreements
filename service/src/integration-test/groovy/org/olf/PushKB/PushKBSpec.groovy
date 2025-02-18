package org.olf.PushKB

import org.codehaus.groovy.transform.stc.SharedVariableCollector
import org.olf.BaseSpec

import grails.gorm.multitenancy.Tenants
import grails.testing.mixin.integration.Integration
import spock.lang.*


@Integration
@Stepwise
@Requires({ instance.isPushKb() })
class PushKBSpec extends BaseSpec {

  @Shared
  String pkgId

  @Unroll
  void "Test pushPkg" ( ) {
    when: 'We POST to pushPkg endpoint'
      def pushPkgBody = getDataFromFile("body1.json", "src/integration-test/resources/pushkb/pushPkg");
      Map resp = doPost("/erm/pushKB/pkg", pushPkgBody);
    then: 'All is well'
      resp.pushPkgResult.success == true
    when: 'Packages are fetched'
      Map pkgGet = doGet("/erm/packages?stats=true");
    then: 'We have the expected amount'
      pkgGet.total == 2636
    when: 'We look specifically for Oxford University Press: STM Collection 2017'
      Map pkgSingleGet = doGet("/erm/packages?filters=name=Oxford%20University%20Press%3A%20STM%20Collection%202017&stats=true");
      Map singlePkg = pkgSingleGet?.results?.getAt(0)
      pkgId = singlePkg?.id
    then: 'We have the expected package'
      pkgSingleGet.total == 1
      singlePkg.name == "Oxford University Press: STM Collection 2017"
      pkgId != null
    when: 'Titles are fetched'
      Map tiGet = doGet("/erm/titles?stats=true");
    then: 'We have the expected amount'
      tiGet.total == 0
    when: 'Package metadata are fetched'
      Map pkgMetadataList = doGet("/erm/packages/metadata?stats=true");
    then: 'We see expected results'
      //log.debug("PKGMETADATA: ${pkgMetadataList}")
      pkgMetadataList.total == 2636
    when: "Package metadata is fetched for ${pkgId}"
      Map pkgMetadata = doGet("/erm/packages/${pkgId}/metadata");
    then: 'We see expected results'
      pkgMetadata.ingressType == 'PUSHKB'
      pkgMetadata.resource.id == pkgId
      pkgMetadata.ingressId != null;
      pkgMetadata.ingressUrl != null;
      pkgMetadata.contentIngressId == null;
      pkgMetadata.contentIngressUrl == null;
      // FIXME test the other fields with ids once pushKB shape is known
  }

  @Unroll
  void "Test pushPCI (sync off)" ( ) {
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
    when: "Package metadata is fetched for ${pkgId}"
      Map pkgMetadata = doGet("/erm/packages/${pkgId}/metadata");
    then: 'We see expected results'
      pkgMetadata.ingressType == 'PUSHKB'
      pkgMetadata.resource.id == pkgId
      pkgMetadata.ingressId != null;
      pkgMetadata.ingressUrl != null;
      pkgMetadata.contentIngressId != null;
      pkgMetadata.contentIngressUrl != null;
      // FIXME test the other fields with ids once pushKB shape is known
  }
}

