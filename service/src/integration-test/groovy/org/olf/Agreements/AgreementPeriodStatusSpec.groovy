package org.olf.Agreements

import org.olf.BaseSpec

import grails.testing.mixin.integration.Integration
import groovy.util.logging.Slf4j

import spock.lang.Stepwise

/*
 * Regression coverage for ERM-4106.
 *
 * SubscriptionAgreement.findPreviousPeriod / findNextPeriod used
 * `a.startDate - b.startDate` as a comparator, but LocalDate has no
 * minus(LocalDate). The closure only fires when the filtered list has
 * 2+ elements, so the bug stayed hidden until an agreement had two
 * fully-past (or two fully-future) periods on the same agreement.
 * That shape took down POST /erm/sas, GET /erm/sas/{id} and GET /erm/sas.
 */
@Slf4j
@Integration
@Stepwise
class AgreementPeriodStatusSpec extends BaseSpec {

  void "agreement with two already-ended periods: previous resolves to the newer one"() {
    given:
      Map payload = [
        name: 'ERM-4106 two ended periods',
        agreementStatus: 'active',
        periods: [
          [startDate: today.minusYears(4).toString(), endDate: today.minusYears(3).toString()],
          [startDate: today.minusYears(2).toString(), endDate: today.minusYears(1).toString()]
        ]
      ]

    when:
      Map created = doPost('/erm/sas', payload)
      Map fetched = doGet("/erm/sas/${created.id}")

    then:
      created?.id != null
      fetched.periods?.size() == 2

    and: 'newer ended period is tagged previous, older has no status'
      Map newer = fetched.periods.find { it.startDate == today.minusYears(2).toString() }
      Map older = fetched.periods.find { it.startDate == today.minusYears(4).toString() }
      newer?.periodStatus == 'previous'
      older?.periodStatus == null
  }

  void "agreement with two fully-future periods: next resolves to the earlier one"() {
    given:
      Map payload = [
        name: 'ERM-4106 two future periods',
        agreementStatus: 'active',
        periods: [
          [startDate: today.plusYears(1).toString(), endDate: today.plusYears(2).toString()],
          [startDate: today.plusYears(3).toString(), endDate: today.plusYears(4).toString()]
        ]
      ]

    when:
      Map created = doPost('/erm/sas', payload)
      Map fetched = doGet("/erm/sas/${created.id}")

    then:
      created?.id != null
      fetched.periods?.size() == 2

    and: 'earlier future period is tagged next, later has no status'
      Map earlier = fetched.periods.find { it.startDate == today.plusYears(1).toString() }
      Map later   = fetched.periods.find { it.startDate == today.plusYears(3).toString() }
      earlier?.periodStatus == 'next'
      later?.periodStatus   == null
  }

}