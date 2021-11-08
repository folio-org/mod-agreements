package org.olf

import groovy.json.JsonSlurper
import jdk.nashorn.internal.runtime.SharedPropertyMap

import java.time.LocalDate

import org.olf.kb.PackageContentItem
import org.olf.kb.Pkg
import org.olf.kb.TitleInstance

import grails.gorm.multitenancy.Tenants
import com.k_int.okapi.OkapiTenantResolver
import grails.testing.mixin.integration.Integration
import spock.lang.*

@Stepwise
@Integration
class EntitlementLogSpec extends BaseSpec {

  @Shared
  int thisYear = LocalDate.now().year

  @Shared
  PackageContentItem item;
  
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
      List pci_list = doGet("/erm/resource/electronic", [filters: ['class==org.olf.kb.PackageContentItem']])

      item = pci_list?.getAt(0)

      println("LOGDEBUG PCI_LIST: ${pci_list}")
      println("LOGDEBUG ITEM: ${item}")
      
    then: 'Expect package found with at least one item'
      assert resp?.getAt(0)?.name == 'access_start_access_end_tests Package'
      assert pci_list?.getAt(0)?.id != null
  }

  def triggerEntitlementLogUpdateAndFetchEntitlementLogs() {
    doGet("/erm/admin/triggerEntitlementLogUpdate")
    def ele = doGet("/erm/entitlementLogEntry?stats=true")

    return ele
  }

  void 'Fetch initial EntitlementLogEntries' () {
    // Initially fetch logs
    when: 'Entitlement Log Entry endpoint polled'
      def ele = doGet("/erm/entitlementLogEntry?stats=true")
    then: 'There are no entries'
      assert ele.totalRecords == 0;
  }

 
  void 'Create an Agreement with our item' () {
    final LocalDate today = LocalDate.now()
    final LocalDate tomorrow = today.plusDays(1)
    
    when: "Post to create new agreement with our package"
      Map respMap = doPost("/erm/sas", {
        'name' 'Agreement with Entitlement'
        'agreementStatus' 'active' // This can be the value or id but not the label
        'periods'([{
          'startDate' today.toString()
          'endDate' tomorrow.toString()
        }])
        'items'([
          { 'resource' item.id }
        ])
      })
      agreementId1 = respMap.id
    
    then: "Response is good and we have a new ID"
      respMap.id != null
  }

  void 'EntitlementLogEntry created' () {
    // Initially fetch logs
    when: 'Entitlement Log Entries trigger and fetched'
      def ele = triggerEntitlementLogUpdateAndFetchEntitlementLogs()

      def ele_add = ele.findAll { it.eventType == 'ADD' }
    then: 'There is a single entry of type ADD'
      assert ele.totalRecords == 1;
      assert ele_add.size() == 1;
  }

}
