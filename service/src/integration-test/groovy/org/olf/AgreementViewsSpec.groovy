package org.olf

import java.time.LocalDate

import grails.testing.mixin.integration.Integration
import spock.lang.*

@Stepwise
@Integration
class AgreementViewsSpec extends BaseSpec {
  
  @Shared
  String pkg_id
  
  @Shared
  String agg_id
  
  @Shared
  int thisYear = LocalDate.now().year

  @Shared
  int expectedItems = 0;
  
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
        items ([{
          'resource' pkg_id
        }])
        name 'Test agreement'
        agreementStatus 'Active'
      }
      agg_id = httpResult?.id

      expectedItems += 1;
    then: 'Agreement added'
      assert agg_id != null
      assert (httpResult?.items?.size() ?: 0) == expectedItems
  }
  
  @Unroll
  def 'Test agreement line range #agreement_line_start - #agreement_line_end' (final String agreement_line_start, final String agreement_line_end, final Map<String, List<String>> expected) {
    
    final List<String> endpoints = ['current', 'future', 'dropped'] 
    
    when: 'Agreement read'
      Map httpResult = doGet("/erm/sas/${agg_id}", [expand: 'items', excludes: 'items.owner'])
      
    and: 'Order-line dates set to #agreement_line_start - #agreement_line_end'
      httpResult.items[0].activeFrom = agreement_line_start
      httpResult.items[0].activeTo = agreement_line_end
    
    and: 'Update put'
      httpResult = doPut("/erm/sas/${agg_id}", httpResult)
      
    then: 'Agreement saved'
      assert httpResult?.id == agg_id
      assert (httpResult?.items?.size() ?: 0) == 1
      
    // We no longer expand the items array by default, so removing this test for now, but leaving it
    // in place for now.
    // when: 'Agreement re-read'
    //   httpResult = doGet("/erm/sas/${agg_id}", ['expand': 'items', exclude: 'items.owner'])
    
    // then: 'Dates are correct'
    //   assert httpResult.items[0].activeFrom == agreement_line_start
    //   assert httpResult.items[0].activeTo == agreement_line_end
    
    when: 'Enpoints checked'
      final List<String> nevers_not_seen = expected['never']?.collect() ?: []
      final Map<String,List<String>> seen_resources = [:].withDefault { [] }
      
      // We must check all endpoints to ensure the 'never' are met.
      for ( final String endpoint : endpoints ) {
        if (endpoint != 'never') {
          log.debug "Finding ${endpoint} resources"
          List epResult = doGet("/erm/sas/${agg_id}/resources/${endpoint}")
          for ( def result : epResult ) {
            final String name = result['_object'].pti.titleInstance.name
            seen_resources[endpoint] << name
            nevers_not_seen.remove(name)
          }
        }
      }
    then: 'Dropped resources match expected dropped resources'
      assert seen_resources['dropped'].size() == (expected['dropped']?.size() ?: 0)
    
    and: 'Future resources match expected future resources'
      assert seen_resources['future'].size() == (expected['future']?.size() ?: 0)
    
    and: 'Current resources match expected current resources'
      assert seen_resources['current'].size() == (expected['current']?.size() ?: 0)
    
    and: 'Never resources were not seen in the previous matches'
     assert nevers_not_seen.size() == (expected['never']?.size() ?: 0)
    
    where:
      agreement_line_start        | agreement_line_end
      "${thisYear - 12}-01-01"    | "${thisYear - 10}-12-31"
      "${thisYear - 12}-01-01"    | "${thisYear - 7}-12-31"
      "${thisYear}-01-01"         | "${thisYear + 1}-12-31"
      "${thisYear - 13}-01-01"    | "${thisYear + 1}-12-31"
      "${thisYear - 13}-01-01"    | "${thisYear + 11}-12-31"
      "${thisYear + 1}-01-01"     | "${thisYear + 11}-12-31"
      null                        | "${thisYear + 11}-12-31"
      "${thisYear - 12}-01-01"    | null
      
      expected << [[
        never: ['Afghanistan', 'Archaeological and Environmental Forensic Science', 'Bethlehem University Journal'],
        dropped: ['Archives of Natural History']
      ],[
        never: ['Bethlehem University Journal'],
        dropped: ['Afghanistan', 'Archaeological and Environmental Forensic Science','Archives of Natural History']
      ],[
        never: ['Afghanistan', 'Archives of Natural History', 'Bethlehem University Journal'],
        current: ['Archaeological and Environmental Forensic Science']
      ],[
        never: ['Bethlehem University Journal'],
        dropped: ['Afghanistan', 'Archives of Natural History'],
        current: ['Archaeological and Environmental Forensic Science']
      ],[
        future: ['Bethlehem University Journal'],
        dropped: ['Afghanistan', 'Archives of Natural History'],
        current: ['Archaeological and Environmental Forensic Science']
      ],[
        never: ['Afghanistan', 'Archives of Natural History'],
        future: ['Archaeological and Environmental Forensic Science','Bethlehem University Journal']
      ],[
        future: ['Bethlehem University Journal'],
        dropped: ['Afghanistan', 'Archives of Natural History'],
        current: ['Archaeological and Environmental Forensic Science']
      ],[
        future: ['Bethlehem University Journal'],
        dropped: ['Afghanistan', 'Archives of Natural History'],
        current: ['Archaeological and Environmental Forensic Science']
      ]]
  }
}
