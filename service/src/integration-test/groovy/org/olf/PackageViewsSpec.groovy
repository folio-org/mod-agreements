package org.olf

import java.time.LocalDate

import grails.testing.mixin.integration.Integration
import spock.lang.*

@Integration
@Stepwise
class PackageViewsSpec extends BaseSpec {
  
  @Shared
  String pkg_id
  
  def 'Ingest a test package' () {
    final LocalDate nextWeek = LocalDate.now().plusWeeks(1)
    
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
            availabilityScope "Global"
            lifecycleStatus "Current"
            sourceDataCreated "2022-01-01"
            sourceDataUpdated "2022-01-01"
            contentItems ([
              {
                depth "fulltext"
                accessStart "2011-01-01"
                accessEnd "2018-12-31"
                coverage ([
                  {
                    startDate "2018-04-01"
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
                accessStart "2011-01-01"
                coverage ([
                  {
                    startDate "2017-01-01"
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
                accessEnd "2018-12-31"
                coverage ([
                  {
                    startDate "2000-02-01"
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
                accessStart "${nextWeek}"
                coverage ([
                  {
                    startDate "${nextWeek}"
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
      
    then: 'Expect package found'
      assert (pkg_id = resp.getAt(0)?.id) != null
      assert resp?.getAt(0)?.name == 'access_start_access_end_tests Package'
  }
  
  @Unroll
  def 'Test package content endpoint #test_endpoint' (final String test_endpoint, final List<String> expected_titles) {
    
    final List<String> endpoints = ['current', 'future', 'dropped']
    
    when: 'Enpoints checked'
      final Map<String,List<String>> seen_resources = [:].withDefault { [] }
      
      // Checking all endpoints and check we only see the title in the expected list
      for ( final String endpoint : endpoints ) {
        List epResult = doGet("/erm/packages/${pkg_id}/content/${endpoint}")
        for ( def result : epResult ) {
          final String name = result.pti.titleInstance.name
          seen_resources[endpoint] << name
        }
      }
    
    then: 'Expectations are met'
      endpoints.each { String endpoint ->
        
        if (test_endpoint == endpoint) {
          // Test is present.
          assert seen_resources[endpoint].intersect(expected_titles).size() == expected_titles.size()
        } else {
          // Should be no overlap.
          assert seen_resources[endpoint].intersect(expected_titles).size() == 0
        }
      }
    
    where:
      test_endpoint   | expected_titles
      'dropped'       | ['Afghanistan', 'Archives of Natural History']
      'current'       | ['Archaeological and Environmental Forensic Science']
      'future'        | ['Bethlehem University Journal']
  }
}
