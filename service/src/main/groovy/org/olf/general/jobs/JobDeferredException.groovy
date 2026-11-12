package org.olf.general.jobs

/** Retry on a later runner tick. Throw only after releasing any job-owned resources. */
class JobDeferredException extends RuntimeException {
  JobDeferredException(String message) {
    super(message)
  }
}
