package org.olf.DeleteResources

class Scenario {
  String description
  List<String> inputResources
  List<String> agreementLines
  String structure
  List<String> markExpectedIds
  List<String> expectedKbMarkForDelete
  List<String> expectedKbDelete

  Scenario() {}


  @Override
  public String toString() {
    return "Scenario{" +
      "description='" + description + '\'' +
      ", inputResources=" + inputResources +
      ", agreementLines=" + agreementLines +
      ", structure='" + structure + '\'' +
      ", markExpectedIds=" + markExpectedIds +
      ", expectedKbMarkForDelete=" + expectedKbMarkForDelete +
      ", expectedKbDelete=" + expectedKbDelete +
      '}';
  }
}