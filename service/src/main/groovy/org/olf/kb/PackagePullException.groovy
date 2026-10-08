package org.olf.kb

/** A rejected pull request, also used to explain a job that can no longer run. */
class PackagePullException extends RuntimeException {
  final int status

  PackagePullException(int status, String message) {
    super(message)
    this.status = status
  }
}
