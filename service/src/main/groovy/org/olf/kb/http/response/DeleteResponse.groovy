package org.olf.kb.http.response

class IdGroups {
  MarkForDeleteMap deleted = new MarkForDeleteMap()
  MarkForDeleteMap markedForDeletion = new MarkForDeleteMap()
}

/**
 * A container for the 'statistics' part of the response, holding counts
 * for both deleted and marked-for-deletion items.
 */
class StatisticGroups {
  DeletionCounts deleted = new DeletionCounts()
  DeletionCounts markedForDeletion = new DeletionCounts()
}

class DeleteResponse {
  IdGroups ids = new IdGroups()
  StatisticGroups statistics = new StatisticGroups()
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
