package org.olf.General

import static groovyx.net.http.ContentTypes.*
import static groovyx.net.http.HttpBuilder.configure
import static org.springframework.http.HttpStatus.*

import org.olf.BaseSpec

import com.k_int.okapi.OkapiHeaders
import com.k_int.okapi.OkapiTenantResolver
import geb.spock.GebSpec
import grails.gorm.multitenancy.Tenants
import grails.testing.mixin.integration.Integration
import java.time.LocalDate
import spock.lang.Stepwise
import spock.lang.Unroll
import spock.lang.Shared

import groovy.util.logging.Slf4j

@Slf4j
@Integration
@Stepwise
class UpdateStartDateBugSpec extends BaseSpec {

  @Shared
  List respList

  @Shared
  Map respMap

  void "Create multi-select custom property"() {
    when: "Get global yes/no refdata category"
      respList = doGet('/erm/refdata?filters=desc==Global.Yes_No')
    
    then: "Good response with refdata values"
      respList.size() == 1
      respList[0].values.size() == 2

    when: "Post to create a new multi-select custom property with previous refdata category"
      respMap = doPost('/erm/custprops', [
        primary: true,
        retired: false,
        defaultInternal: true,
        type: 'MultiRefdata',
        category: respList[0].id,
        label: 'Bug Test',
        name: 'bugTest',
        description: 'Bug test description',
        weight: '0',
        ctx: ''
      ])

    then: "Good response with ID"
      respMap.id != null

  }

  void "Check creating an agreement with the multiselect refdata custom property"() {

    final LocalDate today = LocalDate.now()

    when: "Post to create new agreement with multi-select custom property"
      respMap = doPost('/erm/sas', [
        name: 'Empty Agreement Test',
        agreementStatus: 'active',
        periods: [
          [startDate: today.toString()]
        ],
        customProperties: [
          bugTest: [[
            value: [respList[0].values[0]],
            note: '123',
            publicNote: '1'
          ]]
      ]
    ])

    then: "Good response containing ID, custom properties and correct start date"
      respMap.id != null
      respMap.customProperties.bugTest.size() > 0
      respMap.startDate == today.toString()


  }

  void "Put previously created agreement with new period start date"() {

    final LocalDate tomorrow = today.plusDays(1)

    when: "Put agreement with new period sart date"
      Map agreementToUpdate = respMap
      agreementToUpdate.periods[0].startDate = tomorrow.toString()
      respMap = doPut("/erm/sas/${respMap.id}", agreementToUpdate)

    then: "Response is good and startDate has been updated to the same as period startDate"
      respMap.id != null
      respMap.startDate == tomorrow.toString()
  }

  void "Get previously created agreement and check startDate"() {
    when: "Get previously created agreement"
      respMap = doGet("/erm/sas/${respMap.id}")

    // This is the crux of the issue, we would expect the startDate == tomorrow as shown in the previous response,
    // But instead the start date is the "today" variable set prior to the PUT
    then: "Good response and startDate is the same as previously updated period startDate"
      respMap.id != null
      respMap.startDate == tomorrow.toString()  
  }
 
}

