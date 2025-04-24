package org.olf.kb.http.request.body;

import grails.validation.Validateable

public class HeirarchicalDeletePCIBody implements Validateable {
  List<String> pCIIds

  static constraints = {
    pCIIds nullable: false, minSize: 1
  }
}