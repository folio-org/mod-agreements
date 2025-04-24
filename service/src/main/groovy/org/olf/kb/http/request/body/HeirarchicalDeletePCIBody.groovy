package org.olf.kb.http.request.body;

import grails.validation.Validateable

public class HeirarchicalDeletePCIBody implements Validateable {
  List<String> pCIIds

  static constraints = {
    pCIIds nullable: false, minSize: 1
  }


  @Override
  public String toString() {
    return "HeirarchicalDeletePCIBody{" +
            "grails_validation_Validateable__beforeValidateHelper=" + grails_validation_Validateable__beforeValidateHelper +
            ", pCIIds=" + pCIIds +
            ", grails_validation_Validateable__errors=" + grails_validation_Validateable__errors +
            '}';
  }
}