package org.olf.rbac;

// Implemented by any domain class implementing our RBAC implementation
public interface RBACImplementer {
  public default String rbacDBColumn() {
    return "rbac_owner";
  }
}
