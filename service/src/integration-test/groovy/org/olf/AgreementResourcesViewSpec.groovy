package org.olf

import java.time.LocalDate
import groovy.json.JsonOutput

import grails.testing.mixin.integration.Integration
import spock.lang.*

@Stepwise
@Integration
class AgreementResourcesViewSpec extends BaseSpec {
  
  @Shared
  String pkg_id
  
  @Shared
  String agg_id
  
  @Shared
  int thisYear = LocalDate.now().year

  @Shared
  int expectedItems = 0;

  @Shared
  List<String> endpoints = ['current', 'future', 'dropped']

  def fetchResourcesForAgreement() {
    Map resourceMap = [:]
    for (final String endpoint : endpoints) {
      List resources = doGet("/erm/sas/${agg_id}/resources/${endpoint}").collect { it.id }
      resourceMap[endpoint] = resources
    }
    return resourceMap
  }
  
  def 'Ingest a test package' () {
      
    when: 'Testing package added'
      doPost('/erm/packages/import') {
        header {
          dataSchema {
            name "mod-agreements-package"
            version 1.0
          }
        }
        records ([
          {
            source "Folio Testing"
            reference "access_start_access_end_examples"
            name "access_start_access_end_tests Package"
            packageProvider {
              name "DIKU"
            }
            contentItems ([
              {
                depth "fulltext"
                accessStart "${thisYear - 8}-01-01"
                accessEnd "${thisYear - 1}-12-31"
                coverage ([
                  {
                    startDate "${thisYear - 1}-04-01"
                    startVolume "1"
                    startIssue "1"
                  }
                ])
                platformTitleInstance {
                  platform "EUP Publishing"
                  platform_url "https://www.euppublishing.com"
                  url "https://www.euppublishing.com/loi/afg"
                  titleInstance {
                    name "Afghanistan"
                    identifiers ([
                      {
                        value "2399-357X"
                        namespace "issn"
                      }
                      {
                        value "2399-3588"
                        namespace "eissn"
                      }
                    ])
                    type "serial"
                  }
                }
              },
              {
                depth "fulltext"
                accessStart "${thisYear - 8}-01-01"
                coverage ([
                  {
                    startDate "${thisYear - 2}-01-01"
                    startVolume "1"
                    startIssue "1"
                  }
                ])
                platformTitleInstance {
                  platform "Archaeological and Environmental Forensic Science"
                  platform_url "http://www.equinoxjournals.com"
                  url "http://www.equinoxjournals.com/AEFS/"
                  titleInstance {
                    name "Archaeological and Environmental Forensic Science"
                    identifiers ([
                      {
                        value "2052-3378"
                        namespace "issn"
                      }
                      {
                        value "2052-3386"
                        namespace "eissn"
                      }
                    ])
                    type "serial"
                  }
                }
              },
              {
                depth "fulltext"
                accessEnd "${thisYear - 1}-12-31"
                coverage ([
                  {
                    startDate "${thisYear - 9}-02-01"
                    startVolume "27"
                    startIssue "1"
                  }
                ])
                platformTitleInstance {
                  platform "EUP Publishing"
                  platform_url "https://www.euppublishing.com"
                  url "https://www.euppublishing.com/loi/anh"
                  titleInstance {
                    name "Archives of Natural History"
                    identifiers ([
                      {
                        value "0260-9541"
                        namespace "issn"
                      }
                      {
                        value "1755-6260"
                        namespace "eissn"
                      }
                    ])
                    type "serial"
                  }
                }
              },
              {
                depth "fulltext"
                accessStart "${thisYear + 6}-01-01"
                coverage ([
                  {
                    startDate "${thisYear - 4}-01-01"
                    startVolume "33"
                  }
                ])
                platformTitleInstance {
                  platform "JSTOR"
                  platform_url "https://www.jstor.org"
                  url "https://www.jstor.org/journal/bethunivj"
                  titleInstance {
                    name "Bethlehem University Journal"
                    identifiers ([
                      {
                        value "2521-3695"
                        namespace "issn"
                      }
                      {
                        value "2410-5449"
                        namespace "eissn"
                      }
                    ])
                    type "serial"
                  }
                }
              }
            ])
          }
        ])
      }
    and: 'Find the package by name'
      List resp = doGet("/erm/packages", [filters: ['name==access_start_access_end_tests Package']])
      pkg_id = resp[0].id
      
    then: 'Expect package found'
      assert pkg_id != null
      assert resp?.getAt(0)?.name == 'access_start_access_end_tests Package'
  }
  
  def 'Add an agreement for our package' () {
    final LocalDate today = LocalDate.now()
    final LocalDate tomorrow = today.plusDays(1)
    when: 'Agreement added for package'
      Map httpResult = doPost('/erm/sas') {
        periods ([{
          startDate today.toString()
          endDate tomorrow.toString()
        }])
        name 'Test agreement'
        agreementStatus 'Active'
      }
      agg_id = httpResult?.id

    then: 'Agreement saved'
      assert agg_id != null
  }

  @Unroll
  def 'Test direct PCI access dates are ignored for PCI #name' (final String name) {
    def pci_id = "";

    when: 'Agreement read'
      Map httpResult = doGet("/erm/sas/${agg_id}", [expand: 'items'])
    and: 'Find package by name'
      List pci_resp = doGet("/erm/pci", [filters: ["pkg.id==${pkg_id}", "pti.titleInstance.name==${name}"]])
      pci_id = pci_resp[0]?.id
    then: 'PCI exists'
      assert pci_id != null

    when: 'attach PCI directly to agreement'
      httpResult.items << [resource: [id: pci_id]]

    and: 'Update put'
      httpResult = doPut("/erm/sas/${agg_id}", httpResult, [expand: 'items'])

      expectedItems += 1;
      
    then: 'Agreement saved'
      assert httpResult?.id == agg_id
      // One new
      assert (httpResult?.items?.size() ?: 0) == expectedItems

    when: 'agreement line is set to be active this year'
      // set agreement line to be activeFrom/activeTo dates
      def index = httpResult.items.findIndexOf{ it.resource.id == pci_id }
      httpResult.items[index].activeFrom = "${thisYear - 1}-01-01"
      httpResult.items[index].activeTo = "${thisYear + 1}-12-31"

    and: 'Update put'
      httpResult = doPut("/erm/sas/${agg_id}", httpResult, [expand: 'items'])
      println("LOGDEBUG CURRENT HTTP AFTER PUT: ${JsonOutput.prettyPrint(JsonOutput.toJson(httpResult))}")
      Map resourceMap = fetchResourcesForAgreement()
      println("LOGDEBUG CURRENT RESOURCE MAP: ${JsonOutput.prettyPrint(JsonOutput.toJson(resourceMap))}")

    then: 'Agreement saved and pci in current block'
      assert httpResult?.id == agg_id
      assert resourceMap['current'].contains(pci_id)

    when: 'agreement line is set to be active in the past'
      // set agreement line to be activeFrom/activeTo dates
      index = httpResult.items.findIndexOf{ it.resource.id == pci_id }
      httpResult.items[index].activeFrom = "${thisYear - 12}-01-01"
      httpResult.items[index].activeTo = "${thisYear -10}-12-31"

    and: 'Update put'
      httpResult = doPut("/erm/sas/${agg_id}", httpResult, [expand: 'items'])
      println("LOGDEBUG DROPPED HTTP AFTER PUT: ${JsonOutput.prettyPrint(JsonOutput.toJson(httpResult))}")
      resourceMap = fetchResourcesForAgreement()
      println("LOGDEBUG DROPPED RESOURCE MAP: ${JsonOutput.prettyPrint(JsonOutput.toJson(resourceMap))}")


    then: 'Agreement saved and pci in dropped block'
      assert httpResult?.id == agg_id
      assert resourceMap['dropped'].contains(pci_id)

    when: 'agreement line is set to be active in the future'
      // set agreement line to be activeFrom/activeTo dates
      index = httpResult.items.findIndexOf{ it.resource.id == pci_id }
      httpResult.items[index].activeFrom = "${thisYear + 10}-01-01"
      httpResult.items[index].activeTo = "${thisYear + 12}-12-31"

    and: 'Update put'
      httpResult = doPut("/erm/sas/${agg_id}", httpResult, [expand: 'items'])
      println("LOGDEBUG FUTURE HTTP AFTER PUT: ${JsonOutput.prettyPrint(JsonOutput.toJson(httpResult))}")

      resourceMap = fetchResourcesForAgreement()
      println("LOGDEBUG FUTURE RESOURCE MAP: ${JsonOutput.prettyPrint(JsonOutput.toJson(resourceMap))}")


    then: 'Agreement saved and pci in future block'
      assert httpResult?.id == agg_id
      assert resourceMap['future'].contains(pci_id)

    where:
      name << [
        "Afghanistan",
        "Archaeological and Environmental Forensic Science",
        "Archives of Natural History",
        "Bethlehem University Journal"
      ]
  }
}
