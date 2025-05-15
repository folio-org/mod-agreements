package org.olf.kb.http.request.body;

import grails.validation.Validateable

public class MarkForDeleteBody implements Validateable {
  List<String> resources

  static constraints = {
    resources nullable: true
  }

  @Override
  public String toString() {
    return "MarkForDeleteBody{" +
            ", resources=" + resources +
            '}';
  }
}