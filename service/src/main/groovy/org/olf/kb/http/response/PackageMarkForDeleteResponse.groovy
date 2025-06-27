package org.olf.kb.http.response

import org.olf.kb.http.response.DeletionCounts
import org.olf.kb.http.response.MarkForDeleteResponse

class PackageStatistics {
  DeletionCounts total_markedForDeletion
}

class PackageMarkForDeleteResponse {
  Map<String, MarkForDeleteResponse> packages = [:]
  PackageStatistics statistics = new PackageStatistics()
}