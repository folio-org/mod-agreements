package org.olf.dataimport.internal


class KBManagementBean {
  // Expose this enum.
  public final enum KBIngressType {
    // Allow user to swap between processes to get data into internal KB
    Harvest,
    PushKB
  }

  KBIngressType ingressType
}