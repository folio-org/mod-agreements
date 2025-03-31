package org.olf.Agreements


import org.olf.BaseSpec
import grails.testing.mixin.integration.Integration
import spock.lang.Unroll

import java.time.LocalDate
import spock.lang.Stepwise
import spock.lang.Shared

import groovy.util.logging.Slf4j

@Slf4j
@Integration
@Stepwise
class AgreementPeriodDateERM3438Spec extends BaseSpec {

  @Shared
  List refdataList

  @Shared
  Map respMap

  @Shared
  Map singleCustpropRespMap

  @Shared
  Map multiCustpropRespMap

  void "Create multi-select custom property"() {
    when: "Get global yes/no refdata category"
    refdataList = doGet('/erm/refdata?filters=desc==Global.Yes_No')

    then: "Good response with refdata values"
    refdataList.size() == 1
    refdataList[0].values.size() == 2

    when: "Post to create a new multi-select custom property with refdata category"
    multiCustpropRespMap = doPost('/erm/custprops', [
        primary        : true,
        retired        : false,
        defaultInternal: true,
        type           : 'MultiRefdata',
        category       : refdataList[0].id,
        label          : 'Multi refdata prop',
        name           : 'multiRefdataProp',
        description    : 'Multi refdata prop description',
        weight         : '0',
        ctx            : ''
    ])

    then: "Good response with ID"
    multiCustpropRespMap.id != null

    when: "Post to create a new custom property with refdata category"
    singleCustpropRespMap = doPost('/erm/custprops', [
        primary        : true,
        retired        : false,
        defaultInternal: true,
        type           : 'Refdata',
        category       : refdataList[0].id,
        label          : 'Refdata prop',
        name           : 'refdataProp',
        description    : 'Refdata prop description',
        weight         : '1',
        ctx            : ''
    ])

    then: "Good response with ID"
    singleCustpropRespMap.id != null
  }

  @Unroll
  void "Agreement start date updates as expected"(
      String custProp,
      String agreementName
  ) {
    final LocalDate today = LocalDate.now()
    given: "Agreement is set up"
    Map agreementMap = [
        name           : agreementName,
        agreementStatus: 'active',
        periods        : [
            [startDate: today.toString()]
        ]
    ]

    if (custProp) {
      agreementMap.customProperties = [:]
      agreementMap.customProperties[custProp] = [[
                                                     value     : [refdataList[0].values[0]],
                                                     note      : '123',
                                                     publicNote: '1'
                                                 ]]
    }
    respMap = doPost('/erm/sas', agreementMap)
    when:
    "We subsequently fetch agreement ${agreementName}"
    respMap = doGet("/erm/sas/${respMap.id}")
    then: "Good response containing ID, expected custom properties and correct start date"
    respMap.id != null
    if (custProp) {
      respMap.customProperties[custProp].size() > 0
    } else {
      respMap.customProperties[custProp] == null
    }
    respMap.startDate == today.toString()
    respMap.name == agreementName
    when: "We put to the agreement with a new period start date"
    final LocalDate tomorrow = today.plusDays(1)
    Map agreementToUpdate = respMap
    agreementToUpdate.periods[0].startDate = tomorrow.toString()
    respMap = doPut("/erm/sas/${respMap.id}", agreementToUpdate)
    then: "Response is good and startDate has been updated to the same as period startDate"
    respMap.id != null
    respMap.startDate == tomorrow.toString()
    when:
    "Get agreement: ${agreementName}"
    respMap = doGet("/erm/sas/${respMap.id}")

    // This is the crux of the issue, we would expect the startDate == tomorrow as shown in the previous response,
    // But instead the start date is the "today" variable set prior to the PUT
    then: "Good response and startDate is the same as previously updated period startDate"
    respMap.id != null
    respMap.startDate == tomorrow.toString()

    where:
    custProp            | agreementName
    null                | 'Agreement no custprop'
    'refdataProp'       | 'Agreement with single custprop'
    'multiRefdataProp'  | 'Agreement with multi custprop'
  }
}
