package org.olf.kb.http.request.body

class MarkForDeleteResponse {
  Set<String> pci;
  Set<String> pti;
  Set<String> ti;
  Set<String> work;

  MarkForDeleteResponse(Set<String> pci, Set<String> pti, Set<String> ti, Set<String> work) {
    this.pci = pci
    this.pti = pti
    this.ti = ti
    this.work = work
  }

  MarkForDeleteResponse() {
    this.pci = new HashSet<String>()
    this.pti = new HashSet<String>()
    this.ti = new HashSet<String>()
    this.work = new HashSet<String>()
  }
}
