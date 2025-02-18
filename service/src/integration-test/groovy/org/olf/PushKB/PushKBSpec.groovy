package org.olf.PushKB

import org.olf.BaseSpec

import org.olf.general.pushKB.PushKBService;

import grails.gorm.multitenancy.Tenants
import grails.testing.mixin.integration.Integration
import spock.lang.*


@Integration
@Stepwise
@Requires({ instance.isPushKb() })
class PushKBSpec extends BaseSpec {

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
  }
}

