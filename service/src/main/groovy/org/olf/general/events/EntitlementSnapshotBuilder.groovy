package org.olf.general.events

import java.time.temporal.ChronoUnit

import com.k_int.web.toolkit.refdata.RefdataValue
import com.k_int.web.toolkit.tags.Tag
import groovy.transform.CompileStatic
import org.hibernate.Hibernate
import org.olf.erm.Entitlement
import org.olf.erm.HoldingsCoverage
import org.olf.erm.OrderLine
import org.olf.erm.SubscriptionAgreement
import org.olf.general.DocumentAttachment
import org.olf.kb.ErmResource

/**
 * Builds the JSON-ready Map payload for an {@link Entitlement} CREATE / UPDATE
 * domain event.
 *
 * Local {@code hasMany} collections (coverage, poLines, tags, docs) are
 * serialized as full nested objects. The owning agreement is an ID-ref only —
 * it has its own event stream. Contrast {@link EntitlementDeleteProjection},
 * which stays lean because a pre-delete read should not walk collections.
 *
 * Must be invoked while the Hibernate session is active so lazy collections
 * resolve rather than throwing later.
 */
@CompileStatic
class EntitlementSnapshotBuilder {

  static Map<String, Object> snapshot(Entitlement ent) {
    if (ent == null) return null

    Map<String, Object> out = [:]
    out.id                    = ent.id
    out.owner                 = ownerRef(ent.owner)
    out.type                  = ent.type
    out.resource              = resource(ent.resource)
    out.resourceName          = ent.resourceName
    out.note                  = ent.note
    out.description           = ent.description
    out.activeFrom            = asString(ent.activeFrom)
    out.activeTo              = asString(ent.activeTo)
    out.enabled               = ent.enabled
    out.suppressFromDiscovery = ent.suppressFromDiscovery
    out.authority             = ent.authority
    out.reference             = ent.reference
    out.contentUpdated        = asString(ent.contentUpdated)
    out.dateCreated           = asString(ent.dateCreated)
    out.lastUpdated           = asString(ent.lastUpdated)

    out.coverage              = sortById((ent.coverage ?: []).collect { coverage((HoldingsCoverage) it) })
    out.poLines               = sortById((ent.poLines ?: []).collect { poLine((OrderLine) it) })
    out.tags                  = sortById((ent.tags ?: []).collect { tag((Tag) it) })
    out.docs                  = sortById((ent.docs ?: []).collect { doc((DocumentAttachment) it) })

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
   * as {@code Entitlement.getExplanation()}). Null — never an omitted key — for
   * external / detached entitlements, which have no local resource.
   */
  private static Map resource(ErmResource resource) {
    if (resource == null) return null
    [
      id                   : resource.id,
      class                : Hibernate.getClass(resource).simpleName,
      name                 : resource.name,
      suppressFromDiscovery: resource.suppressFromDiscovery
    ]
  }

  private static Map coverage(HoldingsCoverage c) {
    if (c == null) return null
    [
      id         : c.id,
      startDate  : asString(c.startDate),
      endDate    : asString(c.endDate),
      startVolume: c.startVolume,
      startIssue : c.startIssue,
      endVolume  : c.endVolume,
      endIssue   : c.endIssue
    ]
  }

  // poLineId is a mod-orders reference carried as a bare UUID — no lookup.
  private static Map poLine(OrderLine ol) {
    if (ol == null) return null
    [id: ol.id, poLineId: ol.poLineId]
  }

  private static Map tag(Tag t) {
    if (t == null) return null
    [id: t.id, value: t.value]
  }

  private static Map doc(DocumentAttachment d) {
    if (d == null) return null
    [
      id      : d.id,
      name    : d.name,
      location: d.location,
      url     : d.url,
      note    : d.note,
      atType  : refdata(d.atType)
    ]
  }

  private static Map refdata(RefdataValue rv) {
    if (rv == null) return null
    [id: rv.id, value: rv.value, label: rv.label]
  }

  /**
   * Deterministic collection order, so a consumer diffing two successive
   * snapshots sees only real changes.
   */
  private static List sortById(List items) {
    items.sort { Object item -> (String) (((Map) item)?.id ?: '') }
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