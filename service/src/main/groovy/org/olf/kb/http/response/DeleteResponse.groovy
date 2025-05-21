package org.olf.kb.http.response

class DeleteResponse {

  DeletionCounts statistics;

  DeleteResponse() {
  }

  DeleteResponse(DeletionCounts statistics) {
    this.statistics = statistics
  }

  @Override
  public String toString() {
    return "DeleteResponse{" +
      "statistics=" + statistics +
      '}';
  }
}

class DeletionCounts {
  Integer pciDeleted;
  Integer ptiDeleted;
  Integer tiDeleted;
  Integer workDeleted;

  DeletionCounts() {
  }

  DeletionCounts(Integer pciDeleted, Integer ptiDeleted, Integer tiDeleted, Integer workDeleted) {
    this.pciDeleted = pciDeleted
    this.ptiDeleted = ptiDeleted
    this.tiDeleted = tiDeleted
    this.workDeleted = workDeleted
  }

  @Override
  public String toString() {
    return "DeletionCounts{" +
      "pciDeleted=" + pciDeleted +
      ", ptiDeleted=" + ptiDeleted +
      ", tiDeleted=" + tiDeleted +
      ", workDeleted=" + workDeleted +
      '}';
  }
}
