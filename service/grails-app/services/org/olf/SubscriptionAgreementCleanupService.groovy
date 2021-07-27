package org.olf

import java.time.LocalDate

import org.olf.general.Org;
import org.olf.kb.Pkg;
import org.olf.erm.SubscriptionAgreement;
import org.olf.erm.Period;

import groovy.transform.CompileDynamic

@CompileDynamic
class SubscriptionAgreementCleanupService {
  def periodService

  private List<List<String>> batchFetchAgreements(final int agreementBatchSize, int agreementBatchCount) {
    // Fetch the ids and localCodes for all platforms
    List<List<String>> agreements = SubscriptionAgreement.createCriteria().list ([max: agreementBatchSize, offset: agreementBatchSize * agreementBatchCount]) {
      order 'id'
      projections {
        property('id')
        property('startDate')
        property('endDate')
        property('cancellationDeadline')
      }
    }
    return agreements
  }

  private Set<Period> fetchPeriodsForAgreementId(String aggId) {
    Set<Period> periods = Period.executeQuery("""
      SELECT p FROM Period p
      WHERE p.owner.id = :aggId
      """,
      [aggId: aggId]
    )
    return periods
  }

  void triggerDateCleanup() {
    final int agreementBatchSize = 25
    int agreementBatchCount = 0
    SubscriptionAgreement.withNewTransaction {
      List<List<String>> agreements = batchFetchAgreements(agreementBatchSize, agreementBatchCount)
      while (agreements && agreements.size() > 0) {
        SubscriptionAgreement.withNewSession { session ->
          agreementBatchCount++
          agreements.each { a ->
            Set<Period> periods = fetchPeriodsForAgreementId(a[0])
            LocalDate earliest = periodService.calculateStartDate(periods)
            LocalDate latest = periodService.calculateEndDate(periods)
            LocalDate cancellationDeadline = periodService.calculateCancellationDeadline(periods)

            if (
              a[1] != earliest ||
              a[2] != latest ||
              a[3] != cancellationDeadline
            ) {
              log.warn("Agreement date mismatch for (${a[0]}), calculating new start date, end date and cancellation deadline")
              // Only actually fetch object if you have to
              SubscriptionAgreement agg = SubscriptionAgreement.get(a[0])
              agg.startDate = earliest
              agg.endDate = latest
              agg.cancellationDeadline = cancellationDeadline
              agg.save(failOnError: true)
            }
          }
          
          // Next page
          agreements = batchFetchAgreements(agreementBatchSize, agreementBatchCount)
          session.flush()
          session.clear()
        }
      }
    }
  }

  void triggerOrgsCleanup() {
    Org.executeUpdate("""
      DELETE from Org as theOrg WHERE NOT EXISTS (
        FROM SubscriptionAgreementOrg as sao
          WHERE sao.org = theOrg
      ) AND NOT EXISTS (
        FROM Pkg as p
          WHERE p.vendor = theOrg
      )
    """)
  }
}
