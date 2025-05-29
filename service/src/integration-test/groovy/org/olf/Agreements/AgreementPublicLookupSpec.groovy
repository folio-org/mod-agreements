package org.olf.Agreements
import grails.testing.mixin.integration.Integration
import org.olf.BaseSpec
import spock.lang.Shared

@Integration
class AgreementPublicLookupSpec extends BaseSpec {
  @Shared
  String pkg_id

  void "Load Packages"() {

    when: 'File loaded'
    Map result = importPackageFromFileViaService('publicLookup/publicLookupPackage1.json')
    importPackageFromFileViaService('publicLookup/publicLookupPackage4.json')
    then: 'Package imported'
    result.packageImported == true

    when: "Looked up package with name"
    List resp = doGet("/erm/packages", [filters: ['name==test_package_1']])
    doGet("/erm/packages", [filters: ['name==test_package_4']])
    pkg_id = resp[0].id

    then: "Package found"
    resp.size() == 1
    resp[0].id != null
  }
}
