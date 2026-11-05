# Repairing legacy package ingress metadata

Tenant enables automatically repair missing package ingress metadata (PIM) when
the module runs with `INGRESS_TYPE=Harvest`. This repairs packages that predate
the introduction of PIM in 7.2 without resetting the OAI cursor or re-harvesting.
The repair runs on upgrades and re-enables, even when the tenant request omits
`module_from`. A disabled module can retain tenant data, so the request's upgrade
flag does not determine whether repair is needed. Fresh tenants have no packages
to backfill.

The repair requires exactly one configured, non-readonly RemoteKB with the GOKb
OAI adapter and package record type. Inactive sources count too: an inactive source
may still own packages. The repair only considers packages whose source label is
`GOKb` (case-insensitive) and which have exactly one distinct, nonblank approved
`gokb_uuid` identifier. The stored source label is preserved.
If there is no `gokb_uuid` occurrence of any status, the repair can instead use a
reference in full UUID form (`8-4-4-4-12` hexadecimal digits). The GOKb adapter
uses the upstream package UUID as the reference. This fallback creates the missing
approved identifier as well as PIM in the same transaction, so the ordinary pull
service can subsequently use the package. Existing identifier records are reused.
The module logs each use of the fallback. References containing shortcodes,
whitespace, or malformed UUIDs are not used, and non-approved identifier occurrences
are never overridden. Conflicting approved identifiers remain unresolved.
It creates a `HARVEST` PIM pointing to that RemoteKB. Existing PIM, package sync
flags (including legacy null flags), contents, and source harvest state are preserved.
It does not queue pulls or retry earlier failed resync jobs.

Legacy package records do not retain a direct RemoteKB relationship. The single
source rule assumes the configured GOKb source is the source of those packages.
It cannot reconstruct a deleted historical source, or distinguish a pre-PIM JSON
import that used the same GOKb source label and identifiers. Review such historical
configurations before upgrading. Multiple configured GOKb sources are left for
manual review rather than selecting the currently active source arbitrarily.
The reference fallback validates UUID syntax locally; it does not verify package
existence or identity against GOKb during tenant enable.

The module logs a per-tenant result and warnings for unresolved sources or missing
identifiers. After correcting source configuration or identifiers, another tenant
enable can safely repeat the repair; it only fills missing records. Processing
uses batches of 100 and commits each batch independently.

After repair, enabled packages can be refreshed using
`POST /erm/admin/pullPackage` with `{"packageId":"<local-package-id>"}`.
Ordinary pull validation still applies: the source must be active and writable.
Setting an already-enabled package to `SYNCHRONIZING` again does not queue a job.
