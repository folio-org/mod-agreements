package org.olf

import groovy.json.JsonBuilder

//import com.thoughtworks.xstream.mapper.Mapper
import groovy.json.JsonSlurper
import jdk.nashorn.internal.runtime.SharedPropertyMap

import java.time.LocalDate
import groovy.json.JsonOutput

import org.olf.kb.PackageContentItem
import org.olf.kb.Pkg
import org.olf.kb.PlatformTitleInstance
import org.olf.kb.TitleInstance

import grails.gorm.multitenancy.Tenants
import com.k_int.okapi.OkapiTenantResolver
import grails.testing.mixin.integration.Integration
import spock.lang.*

@Stepwise
@Integration
class AgreementExportSpec extends BaseSpec {

  def importService
  
  @Shared
  String pkg_id
  
  @Shared
  String agreementId1
  
  @Shared
  String agreementId2
  
  @Shared
  int expectedItems = 0;
  
  @Shared
  int thisYear = LocalDate.now().year
  
  
  void "Load Packages"() {

    when: 'File loaded'

    def jsonSlurper = new JsonSlurper()
    def package_data = jsonSlurper.parse(new File('src/integration-test/resources/packages/simple_pkg_1.json'))
    int result = 0
    final String tenantid = currentTenant.toLowerCase()
    log.debug("Create new package with tenant ${tenantid}");
    Tenants.withId(OkapiTenantResolver.getTenantSchemaName(tenantid)) {
      Pkg.withTransaction { status ->
        result = importService.importPackageUsingInternalSchema(package_data)
        log.debug("Package import complete - num packages: ${Pkg.executeQuery('select count(p.id) from Pkg as p')}");
        log.debug("                            num titles: ${TitleInstance.executeQuery('select count(t.id) from TitleInstance as t')}");
        Pkg.executeQuery('select p.id, p.name from Pkg as p').each { p ->
          log.debug("Package: ${p}");
        }
      }
    }

    then: 'Package imported'
    result > 0
    
    when: "Looked up package with name"
      List resp = doGet("/erm/packages", [filters: ['name==K-Int Test Package 001']])
      pkg_id = resp[0].id

    then: "Package found"
      resp.size() == 1
      resp[0].id != null

  }

 
  void 'Create Agreement with our package' () {
  
    final LocalDate today = LocalDate.now()
    final LocalDate tomorrow = today.plusDays(1)
    
    when: "Post to create new agreement with our package"
      Map respMap = doPost("/erm/sas", {
        'name' 'Agreement with Package'
        'agreementStatus' 'active' // This can be the value or id but not the label
        'periods'([{
                     'startDate' today.toString()
                     'endDate' tomorrow.toString()
                   }])
        'items'([
                { 'resource' pkg_id }
        ])
      })
      agreementId1 = respMap.id
    
    then: "Response is good and we have a new ID"
      assert respMap.id != null
  }
  
  void 'Export Agreement' () {
  when: "Receive JSON Export (Agreement) from Endpoint"
    Map exportMapAgreement = doGet("/erm/sas/${agreementId1}/export/current")
//    exportMap.forEach((key, value) -> System.out.println(key + ":" + value));
//    println exp_json.content.resources.first().relatedTitles.toString()
//    log.debug(exportMap.toMapString())
//    println(exportMapAgreement.resources[0].relatedTitles[0].name)

    then: "Export (Agreement) includes relatedTitles"
//    Map resources = exportMap.resources[0]
//    log.debug(exportMap.resources.toMapString())
      assert exportMapAgreement.size() > 0
      assert exportMapAgreement.resources[0].relatedTitles[0].name == 'Clinical Cancer Drugs'
//    json.get('resources').first().get('relatedTitles')
  }
  
  void 'Export Resources of Agreement' () {
    when: "Receive JSON Export (Resources) from Endpoint"
      def slurper = new JsonBuilder()
      def result = slurper(doGet("/erm/sas/${agreementId1}/resources/export/current"))
//      def export_resources = doGet("/erm/sas/${agreementId1}/resources/export/current")
//      def exp_json = new groovy.json.JsonBuilder(exportMapResources)
//      json rootKey: exportMap
//      println groovy.json.JsonOutput.prettyPrint(exp_json.toString())
    
    then: "Export (Resources) includes relatedTitles"
      assert result?.relatedTitles[0].name.join(',')=='Clinical Cancer Drugs'
//      assert export_resources.relatedTitles[0].name == '[Clinical Cancer Drugs]'
  }
 
 
  void 'Create Agreement for PCI' () {
  
    final LocalDate today = LocalDate.now()
    final LocalDate tomorrow = today.plusDays(1)
    
    when: "Post to create new agreement with our package"
      Map respMap2 = doPost("/erm/sas", {
        'name' 'Agreement with PCI'
        'agreementStatus' 'active' // This can be the value or id but not the label
        'periods'([{
                     'startDate' today.toString()
                     'endDate' tomorrow.toString()
                   }])
      })
      agreementId2 = respMap2.id
      println("agreementId2 (create agreement): ${agreementId2}")
    
    then: "Response is good and we have a new ID"
      assert respMap2.id != null
//  }
  
//  void 'Add PCI to Agreement'() {
    println("agreementId2: ${agreementId2}")
    def pci_id = "";

    when: 'Agreement read'
    Map httpResult2 = doGet("/erm/sas/${agreementId2}", [expand: 'items'])
    println("Agreement found: ${httpResult2.id}")
    and: 'Find package by name'
    List pci_resp = doGet("/erm/pci", [filters: ["pkg.id==${pkg_id}", "pti.titleInstance.name==Clinical Cancer Drugs"]])
    pci_id = pci_resp[0]?.id
    then: 'PCI exists'
    assert pci_id != null

    when: 'attach PCI directly to agreement'
    httpResult2.items << [resource: [id: pci_id]]

    and: 'Update put'
    httpResult2 = doPut("/erm/sas/${agreementId2}", httpResult2, [expand: 'items'])

    expectedItems += 1;

    then: 'Agreement saved'
    assert httpResult2?.id == agreementId2
    // One new
    assert (httpResult2?.items?.size() ?: 0) == expectedItems

  }
  
  void 'Export Agreement with PCI' () {
  when: "Receive JSON Export (Agreement) from Endpoint"
    Map exportMapAgreement = doGet("/erm/sas/${agreementId2}/export/current")
//    exportMap.forEach((key, value) -> System.out.println(key + ":" + value));
//    println exp_json.content.resources.first().relatedTitles.toString()
//    log.debug(exportMap.toMapString())
    println(exportMapAgreement.resources[0].relatedTitles[0].name)

    then: "Export (Agreement) includes relatedTitles"
//    Map resources = exportMap.resources[0]
//    log.debug(exportMap.resources.toMapString())
      exportMapAgreement.size() > 0
      assert exportMapAgreement.resources[0].relatedTitles[0].name == 'Clinical Cancer Drugs'
//    json.get('resources').first().get('relatedTitles')
  }
  
  void 'Export Resources of Agreement with PCI' () {
    when: "Receive JSON Export (Resources) from Endpoint"
      def slurper = new JsonBuilder()
      def result = slurper(doGet("/erm/sas/${agreementId2}/resources/export/current"))
      def export_resources = doGet("/erm/sas/${agreementId2}/resources/export/current")
//      def exp_json = new groovy.json.JsonBuilder(exportMapResources)
//      json rootKey: exportMap
//      println groovy.json.JsonOutput.prettyPrint(exp_json.toString())
    
    then: "Export (Resources) includes relatedTitles"
      assert result?.relatedTitles[0].name.join(',')=='Clinical Cancer Drugs'
//      assert export_resources.relatedTitles[0].name == '[Clinical Cancer Drugs]'
  }
}