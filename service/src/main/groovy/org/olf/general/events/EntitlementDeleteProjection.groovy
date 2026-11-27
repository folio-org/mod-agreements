package org.olf.general.events

import java.time.temporal.ChronoUnit

import groovy.transform.CompileStatic
import org.hibernate.Hibernate
import org.olf.erm.Entitlement
import org.olf.erm.SubscriptionAgreement
import org.olf.kb.ErmResource

/**
 * Builds the JSON-ready Map payload for an {@link Entitlement} DELETE event.
 *
 * Deliberately lean, unlike {@link AgreementSnapshotBuilder}: scalars, the
 * owning agreement id, and the resource reference only. The {@code hasMany}
 * collections ({@code coverage}, {@code poLines}, {@code tags}, {@code docs})
 * are NOT traversed — that state was carried by prior events for this
 * Entitlement and by the parent Agreement's snapshots, and skipping it keeps
 * the pre-delete read cheap.
 *
 * Must be invoked while the Hibernate session is active, and before the row
 * is deleted.
 */
@CompileStatic
class EntitlementDeleteProjection {

  static Map<String, Object> of(Entitlement ent) {
    if (ent == null) return null

    Map<String, Object> out = [:]
    out.id                    = ent.id
    out.owner                 = ownerRef(ent.owner)
    out.type                  = ent.type
    out.resource              = resourceRef(ent.resource)
    out.resourceName          = ent.resourceName
    out.activeFrom            = asString(ent.activeFrom)
    out.activeTo              = asString(ent.activeTo)
    out.enabled               = ent.enabled
    out.suppressFromDiscovery = ent.suppressFromDiscovery
    out.authority             = ent.authority
    out.reference             = ent.reference
    out.dateCreated           = asString(ent.dateCreated)
    out.lastUpdated           = asString(ent.lastUpdated)

    return out
  }

  // Reading the id off the proxy does not initialise it.
  private static Map ownerRef(SubscriptionAgreement owner) {
    if (owner == null) return null
    [id: owner.id]
  }

  /**
   * ErmResource is mapped {@code tablePerHierarchy false}, so there is no
   * discriminator column and the concrete subclass cannot be read from an
   * uninitialised proxy — {@code Hibernate.getClass()} resolves it (same idiom
   * as {@code Entitlement.getExplanation()}) at the cost of one single-row
   * read. Null for external / detached entitlements, which have no local
   * resource.
   */
  private static Map resourceRef(ErmResource resource) {
    if (resource == null) return null
    [id: resource.id, class: Hibernate.getClass(resource).simpleName]
  }

  // ISO-8601, seconds precision, UTC — matches the REST GET representation.
  private static String asString(Object o) {
    if (o == null) return null
    if (o instanceof Date) {
      return ((Date) o).toInstant().truncatedTo(ChronoUnit.SECONDS).toString()
    }
    return o.toString()
  }
}