package org.olf.kb.http.response

class DeleteResponse {

  DeletionCounts statistics;
  MarkForDeleteResponse deletedIds;
  MarkForDeleteResponse markedForDeletion;

  DeleteResponse() {
  }

  DeleteResponse(DeletionCounts statistics) {
    this.statistics = statistics
  }

  DeleteResponse(DeletionCounts statistics, MarkForDeleteResponse deletedIds) {
    this.statistics = statistics
    this.deletedIds = deletedIds
  }

  DeleteResponse(DeletionCounts statistics, MarkForDeleteResponse deletedIds, MarkForDeleteResponse markedForDeletion) {
    this.statistics = statistics
    this.deletedIds = deletedIds
    this.markedForDeletion = markedForDeletion;
  }

  @Override
  public String toString() {
    return "DeleteResponse{" +
      "deletedIds=" + deletedIds +
      "statistics=" + statistics +
      '}';
  }
}

class DeletionCounts {
  Integer pci;
  Integer pti;
  Integer ti;
  Integer work;

  DeletionCounts() {
  }

  DeletionCounts(Integer pciDeleted, Integer ptiDeleted, Integer tiDeleted, Integer workDeleted) {
    this.pci = pciDeleted
    this.pti = ptiDeleted
    this.ti = tiDeleted
    this.work = workDeleted
  }

  @Override
  public String toString() {
    return "DeletionCounts{" +
      "pciDeleted=" + pci +
      ", ptiDeleted=" + pti +
      ", tiDeleted=" + ti +
      ", workDeleted=" + work +
      '}';
  }
}
