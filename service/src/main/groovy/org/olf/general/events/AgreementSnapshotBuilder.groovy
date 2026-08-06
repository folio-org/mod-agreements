package org.olf.general.events

import java.time.Instant
import java.time.temporal.ChronoUnit

import com.k_int.web.toolkit.refdata.RefdataValue
import com.k_int.web.toolkit.tags.Tag
import groovy.transform.CompileStatic
import org.olf.erm.AgreementRelationship
import org.olf.erm.Entitlement
import org.olf.erm.InternalContact
import org.olf.erm.Period
import org.olf.erm.RemoteLicenseLink
import org.olf.erm.SubscriptionAgreement
import org.olf.erm.SubscriptionAgreementOrg
import org.olf.erm.SubscriptionAgreementOrgRole
import org.olf.erm.AlternateName
import org.olf.general.DocumentAttachment
import org.olf.general.Org

/**
 * Builds the JSON-ready Map payload for a {@link SubscriptionAgreement} domain
 * event.
 *
 * Local {@code hasMany} collections (periods, orgs, contacts, alternateNames,
 * docs, supplementaryDocs, externalLicenseDocs, relationships) are serialized
 * as full nested objects. First-class children with their own event stream
 * ({@code items}, {@code linkedLicenses}) are ID-refs only — see
 * {@code stories/kafka-events-design.md} §4.3. Agreements on the far side of a
 * relationship are ID-refs for the same reason, and because nesting them would
 * recurse.
 *
 * Must be invoked while the Hibernate session is active so lazy collections
 * resolve rather than throwing later.
 */
@CompileStatic
class AgreementSnapshotBuilder {

  static Map<String, Object> snapshot(SubscriptionAgreement sa) {
    if (sa == null) return null

    Map<String, Object> out = [:]
    out.id                    = sa.id
    out.name                  = sa.name
    out.description           = sa.description
    out.localReference        = sa.localReference
    out.vendorReference       = sa.vendorReference
    out.attachedLicenceId     = sa.attachedLicenceId
    out.licenseNote           = sa.licenseNote
    out.enabled               = sa.enabled
    out.renewalDate           = asString(sa.renewalDate)
    out.nextReviewDate        = asString(sa.nextReviewDate)
    out.startDate             = asString(sa.startDate)
    out.endDate               = asString(sa.endDate)
    out.cancellationDeadline  = asString(sa.cancellationDeadline)
    out.dateCreated           = asString(sa.dateCreated)
    out.lastUpdated           = asString(sa.lastUpdated)

    out.agreementType         = refdata(sa.agreementType)
    out.renewalPriority       = refdata(sa.renewalPriority)
    out.agreementStatus       = refdata(sa.agreementStatus)
    out.reasonForClosure      = refdata(sa.reasonForClosure)
    out.isPerpetual           = refdata(sa.isPerpetual)
    out.contentReviewNeeded   = refdata(sa.contentReviewNeeded)

    out.vendor                = orgWrapper(sa.vendor)

    out.periods               = (sa.periods ?: []).collect { period((Period) it) }
    out.contacts              = (sa.contacts ?: []).collect { contact((InternalContact) it) }
    out.orgs                  = (sa.orgs ?: []).collect { agreementOrg((SubscriptionAgreementOrg) it) }
    out.alternateNames        = (sa.alternateNames ?: []).collect { altName((AlternateName) it) }
    out.tags                  = (sa.tags ?: []).collect { tag((Tag) it) }

    out.docs                  = (sa.docs ?: []).collect { doc((DocumentAttachment) it) }
    out.supplementaryDocs     = (sa.supplementaryDocs ?: []).collect { doc((DocumentAttachment) it) }
    out.externalLicenseDocs   = (sa.externalLicenseDocs ?: []).collect { doc((DocumentAttachment) it) }

    out.inwardRelationships   = (sa.inwardRelationships ?: []).collect { relationship((AgreementRelationship) it) }
    out.outwardRelationships  = (sa.outwardRelationships ?: []).collect { relationship((AgreementRelationship) it) }

    out.items                 = (sa.items ?: []).collect { [id: ((Entitlement) it).id] }
    out.linkedLicenses        = (sa.linkedLicenses ?: []).collect { linkedLicense((RemoteLicenseLink) it) }

    return out
  }

  private static Map refdata(RefdataValue rv) {
    if (rv == null) return null
    [id: rv.id, value: rv.value, label: rv.label]
  }

  private static Map orgWrapper(Org o) {
    if (o == null) return null
    [id: o.id, name: o.name, orgsUuid: o.orgsUuid]
  }

  private static Map agreementOrg(SubscriptionAgreementOrg sao) {
    if (sao == null) return null
    [
      id        : sao.id,
      primaryOrg: sao.primaryOrg,
      note      : sao.note,
      roles     : (sao.roles ?: []).collect { saoRole((SubscriptionAgreementOrgRole) it) },
      org       : orgWrapper(sao.org)
    ]
  }

  private static Map saoRole(SubscriptionAgreementOrgRole r) {
    if (r == null) return null
    [id: r.id, role: refdata(r.role)]
  }

  private static Map contact(InternalContact c) {
    if (c == null) return null
    [id: c.id, user: c.user, role: refdata(c.role)]
  }

  private static Map period(Period p) {
    if (p == null) return null
    [
      id                  : p.id,
      startDate           : asString(p.startDate),
      endDate             : asString(p.endDate),
      cancellationDeadline: asString(p.cancellationDeadline),
      note                : p.note
    ]
  }

  private static Map altName(AlternateName a) {
    if (a == null) return null
    [id: a.id, name: a.name]
  }

  private static Map tag(Tag t) {
    if (t == null) return null
    [id: t.id, value: t.value]
  }

  // Same projection as EntitlementSnapshotBuilder.doc() — the two event streams
  // must expose an identical docs[] shape. fileUpload is deliberately omitted:
  // the payload carries metadata, not file content.
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

  /**
   * Both ends are emitted so consumers need not infer which side the snapshot
   * sits on — for an entry of {@code inwardRelationships}, {@code inward} is
   * this agreement and {@code outward} is the far side (and vice versa).
   * Reading the id off the proxy does not initialise it.
   */
  private static Map relationship(AgreementRelationship rel) {
    if (rel == null) return null
    [
      id     : rel.id,
      type   : refdata(rel.type),
      note   : rel.note,
      inward : agreementRef(rel.inward),
      outward: agreementRef(rel.outward)
    ]
  }

  private static Map agreementRef(SubscriptionAgreement sa) {
    if (sa == null) return null
    [id: sa.id]
  }

  private static Map linkedLicense(RemoteLicenseLink link) {
    if (link == null) return null
    [
      id       : link.id,
      remoteId : link.remoteId,
      status   : refdata(link.status)
    ]
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