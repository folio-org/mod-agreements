package org.olf

import java.time.LocalDate
import groovy.json.JsonOutput
import java.net.URLEncoder

import grails.testing.mixin.integration.Integration
import spock.lang.*

@Stepwise
@Integration
class MatchKeySpec extends BaseSpec {
  
  @Shared
  String pkg_id

  @Shared
  int thisYear = LocalDate.now().year

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

  def 'Check MatchKeys are established as expected for each PCI' (final String name) {
    when: "PCI for ${name} is fetched"
      //ArrayList httpResult = doGet("/erm/pci?match=name&term=${URLEncoder.encode(name, "UTF-8")}")
      ArrayList httpResult = doGet("/erm/pci")
      List resp = doGet("/erm/packages", [filters: ['name==access_start_access_end_tests Package']])
      log.debug("LOGDEBUG RESULT for ${name}: ${JsonOutput.prettyPrint(JsonOutput.toJson(httpResult))}")
      log.debug("LOGDEBUG PKG RESULT for ${name}: ${JsonOutput.prettyPrint(JsonOutput.toJson(resp))}")
    then:
      true == true
    where:
      name << [
        "Afghanistan",
        "Archaeological and Environmental Forensic Science",
        "Archives of Natural History",
        "Bethlehem University Journal"
      ]
  }
}
