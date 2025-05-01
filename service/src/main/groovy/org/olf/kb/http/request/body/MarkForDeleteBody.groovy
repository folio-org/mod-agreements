package org.olf.kb.http.request.body;

import grails.validation.Validateable

public class MarkForDeleteBody implements Validateable {
  List<String> pcis
  List<String> ptis
  List<String> tis

  static constraints = {
    pcis nullable: true
    ptis nullable: true
    tis nullable: true
  }

  @Override
  public String toString() {
    return "MarkForDeleteBody{" +
            ", pcis=" + pcis +
            ", ptis=" + ptis +
            ", tis=" + tis +
            '}';
  }
}