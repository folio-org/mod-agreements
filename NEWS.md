## 5.0.0 2021-10-04
 * ERM-1848 Support duplicating supplementary docs and supplementary properties separately on duplicating an agreement
 * ERM-1847 Failure to resolve a title should lead to skipping title
 * ERM-1819 Add PTI URL to entitlement log entries
 * ERM-1816 Error on tagging an agreement line which references an external (eHoldings) resource
 * ERM-1801 Implement Title Ingest process
 * ERM-1781 Error on adding coverage to PCI which overlaps with existing coverage on PTI/TI
 * ERM-1777 Templated URL not updating on deletion of Proxy
 * ERM-1774 Regularly remove organizations that do not have any links to Agreements/Packages
 * ERM-1766 Change restriction on GOKb harvest `editStatus` value
 * ERM-1765 triggerEntitlementLogUpdate fails after deletion of agreement line
 * ERM-1755 If a KBART file fails to load, the job outcome should be "failure"
 * ERM-1754 PCI accessStart and accessEnd dates should be ignored when PCI added as individual resource to agreement
 * ERM-1753 Migration to new Org / Org role structure leads to duplicate notes
 * ERM-1747 Bump versions across ERM apps follow Organization management changes
 * ERM-1739 Remove duplicate stanzas from application.yml
 * ERM-1723 Use label rather than name when sorting custom properties in agreements
 * ERM-1649 Strictly enforce ISSN and ISBN vs eISSN/pISSN and eISBN/pISBN
 * ERM-1645 The package resourceCount incorrectly includes PCIs with a removedTimestamp
 * ERM-1542 Make organization roles for agreements editable in tenant settings
 * ERM-1540 Support for multiple roles per organisation in Agreements
 * ERM-1231 Add date created/last updated metadata to Agreements
 * ERM-1001 Separate permissions for file download in Licenses/Agreements
 * ERM-506 KB Local Admin | Export import logs as JSON

## 4.1.0 2021-06-15
 * ERM-1730 Add renewalPriority to agreement widget definition
 * ERM-1724 Reduce running time for StringTemplateBulkSpec integration test
 * ERM-1696 Added match terms to WidgetDefinitions
 * ERM-1652 Agreement jobs simple search widget definition
 * ERM-1651 Agreements simple search widget definition
 * ERM-1650 Add unique indexes for refdata tables
 * The package resourceCount incorrectly includes PCIs with a removedTimestamp
 * ERM-1643 Implemented dashboard interface 
 * ERM-1632 Remote KB "LOCAL" should be created as read only
 * FOLIO-3131: Use https for maven.k-int.com
 * FOLIO-3106 Use https for maven.indexdata.com
 * Enable tenant logging
 * Harmonize snapshot versions
 * Added sample KBs

## 4.0.1 2021-03-31
 * ERM-1616 ERM admin/triggerHousekeeping fails

## 4.0.0 2021-03-15
 * ERM-1567 Cancellation deadline is now non-transient property on agreement
 * ERM-1564 Period starting/ending on current date not shown as current period
 * ERM-1534 Changed Agreement API to no longer directly expose "current agreement", switching instead to a tag on the period object.
 * ERM-1533 Agreement start and end dates should be the earliest period start and latest period end date respectively
 * ERM-1457 Extend length of field for monographVolume, monographEdition, firstAuthor, firstEditor
 * ERM-1248 If local platform code not set, appears as `null` when a customised URL is proxied
 * ERM-1246 Template with null output will fail silently
 * ERM-1243 CustomProperty values not duplicated properly
 * ERM-1238 Agreements /erm/entitlements endpoint doesn't expand owner when entitlement is external
 * ERM-1213 Exporting Resources from Agreement when "All" filter selected does not export all resources
 * ERM-1203 Agreement Resources Export contains duplicate resources and incorrect agreement line information
 * ERM-1156 Support user access to platform records
  * ERM-1157 Implement Platform controller and URLMappings in mod-agreements
  * ERM-1185 Add hasMany platformLocators to platform in domain model
 * ERM-1079 Unable to delete license without permissions in agreements module
 * ERM-1048 Implement "template" mechanism to create URLs for resources based on existing data
  * ERM-1108 Add proxied URLs to exports
 * ERM-1047 Support for customised URLs at platform level
  * ERM-1135 Platform local code field
 * ERM-972 Missing permission definitions
 * Removed static version from vagrant box for developers
 * Feature ii kbplus kb -- Internal tweaks to the KIJPFAdapter
## 3.0.0 2020-10-14
 * ERM-1180 Add @ EnableScheduling annotation in mod-agreements
 * ERM-1163 Remove default values from publicationType
 * ERM-1154 change name of an agreement with local resource AL fails 
 * ERM-1147 Incorrect values displaying in "Acquisition method" column in "Agreements for this e-resource" MCL
 * ERM-1131 GOKb record numbers in info/error log
 * ERM-1117 Adding PCI to basket from /erm/eresources/<pci UUID> view results in different JSON shape added to basket
 * ERM-1114 Duplicating an Agreement with Supplementary Documents does not duplicate the documents -- data cleanup service job created
 * ERM-1101 Attempt to export All resources in KBART fails
 * ERM-1094 Error on saving PCI
 * ERM-1093 Fetching an Agreement with a PCI Entitlement gives 500 error
 * ERM-1086 Exception being thrown on adding an agreement line while creating an agreement
 * ERM-1080 Logging not working after Grails upgrade
 * ERM-1070 Support POST operation for creating new entitlements
 * ERM-1069 Add an agreement line without a resource
 * ERM-1055 Comparison report "overlap" discrepancy
 * ERM-1046 Non-phrase searching support for agreements
 * ERM-1012 Correct order of fields in KBART export
 * ERM-1010 Ensure use of MDC fields is consistent in import job error and info logging
 * ERM-1006 "Future" and "Dropped" title lists do not include directly linked PCIs
 * ERM-994 Comparison treats two separate resources with same title as the same
 * ERM-966 Add "Note" to Organisation link in Agreements
 * ERM-952 Process and save package/agreement comparisons
 * ERM-948 Enhance eHoldings display in dedicated Agreement Line view
 * ERM-943 Separate ERM `publication type` from `type`
 * ERM-932 Add TI "suppress from discovery" field to Agreement resource JSON export
 * ERM-904 Update tooling and framework
   * ERM-908 Update agreements to Grails 4
   * ERM-909 Update docker image to Java 11
  * ERM-892 Sample data being used on setup
  * ERM-851 Securing APIs by default
 * ERM-742 Custom properties: Backend validation not working
 * ERM-510 KB Local Admin | Log should include information to identify Record where appropriate

## 2.3.0 2020-06-11
 * ERM-941 remove time check from remotekb harvest
 * ERM-931 Add Agreement Line "suppress from discovery" field to agreement resource JSON export
 * ERM-930 Add PCI "suppress from discovery" field to Agreement resource JSON export
 * ERM-929 Add Agreement Line tags to agreement resource JSON export
 * ERM-925 Add PCI and TI tags to resources JSON export
 * ERM-910 Loading titles to local KB as JSON or KBART fails
 * ERM-903 Migration scripts has hardcoded diku tenant
 * ERM-897 Add support for tags to agreement lines
 * ERM-896 Add support for tags to eresources
 * ERM-895 Add "Suppress from discovery" property to Agreement lines
 * ERM-894 Add "Suppress from discovery" property to e-resources
 * ERM-891 Require Agreement Names to be unique (Backend Validation)
 * ERM-889 Add new agreement-agreement relationships
 * ERM-866 Import of KBART incorrectly generates errors for monograph coverage
 * ERM-864 Updates of selected fields fail silently
 * ERM-837 Agreement line note does not save and/or retrieve for Agreement lines from eHoldings
 * ERM-834 PCI not created when title is too long
 * ERM-827 Add support for "Alternative name" for agreements
 * ERM-826 RemoteLicenseLink.Status refdata category should be "internal"
 * ERM-807 Add Embargo to PCI in Agreement exports
 * ERM-800 Add "Embargo" to local KB data model and imports
 * ERM-793 Support controlled updating of title instances in agreements local KB
 * ERM-786 Agreement supplementary property descriptions are limited to 255 characters
 * ERM-775 Use information from siblingInstances in KBART export
 * ERM-773 Import service | Missing instanceMedia on import should skip line and continue
 * ERM-772 KBART export should use subtype to classify identifier as print_identifier or online_identifier
 * ERM-769 Error loading KBART file
 * ERM-767 Monograph resources are not exported in KBART format
 * ERM-735 Separate refdata categories into "internal" and "user" lists
 * ERM-689 Add monograph fields to KBART export
 * ERM-685 Support import and processing of KBART in Local KB Admin
 * ERM-681 Fetch enhanced metadata for books from GOKb after harvesting
 * ERM-547 Interpret GOKb Package Status, Edit Status and List Status
 * ERM-427 No way of fetching agreement entitlements sorted by name
 * ERM-193 Deleting a License (and possibly Agreement) with a Tag isn't possible 

## 2.2.2 2020-04-29
 * ERM-858 Postgres sequences out of sync when existing agreement data is upgraded

## 2.2.1 2020-04-18
 * ERM-838 Add custom_properties_id to existing subscription_agreement entries (upgrade)

## 2.2.0 2020-03-13
 * ERM-747 Custom Properties: Unable to correctly save decimals with german browser locale
 * ERM-711 POST/PUT on erm/sas/{:id} endpoint appears to not save reasonForClosure
 * ERM-696 Agreements: Reason for closure is not displayed
 * ERM-689 Add monograph fields to KBART export
 * ERM-685 Support import and processing of KBART in Local KB Admin
   * ERM-687 Create tsv parser
 * ERM-682 Upgrading mod-agreements module from Daisy to Edelweiss fails
 * ERM-680 Show spinner in agreement lines when switching between agreements
 * ERM-676 Support Editor information in Agreements local KB
 * ERM-655 Sorting limits output in some cases
 * ERM-649 Display a better message when the ExternalDataSources name isn't unique
 * ERM-641 License custom property "note" field should not be returned in public API
 * ERM-587 Agreements : On duplicating an agreement the "General notes about this agreement's license" field should be copied
 * ERM-571 Ensure remote KB syncs are not left "In Progress" when an error occurs.
 * ERM-507 Agreement: Agreement Lines: Package item count always equals "1"
 * ERM-486 Support monograph volume in Agreements local KB
 * ERM-485 Support e/p monograph publication dates in Agreements local KB
 * ERM-482 Support Author information in Agreements local KB
 * ERM-481 Support Edition information in Agreements local KB
 * ERM-461 Local KB Admin | Log error when accessEnd date before accessStart date
 * ERM-437 Local KB admin | External data source name should be unique
   * ERM-643 Add validation clause to ensure that the names data source names are unique
 * ERM-434 "LOCAL" Remote KB/data source should not be editable or be able to be deleted
   * ERM-644 Add preUpdate and preDelete listeners
 * ERM-356 Expose license terms over API
   * ERM-361 Support API for retrieving license terms based on a resource identifier

## 2.0.1 2020-01-28
 * ERM-682 Upgrading mod-agreements module from Daisy to Edelweiss fails

## 2.0.0 2019-12-04
 * ERM-621 License terms not included in Agreement JSON export
 * ERM-614 rkb_cursor reset after harvest completed
 * ERM-583 Use JVM features to manage container memory
 * ERM-561 GOKb harvest job ends in failure
 * ERM-552 Clone endpoint clones properties that are omitted/set to false
 * ERM-546 Interpret <status> element on GOKb TIPP
 * ERM-538 Support health check endpoint (for example /admin/health provided by RMB)
 * ERM-536 mod-agreements Re-assess the container Memory allocation in default LaunchDescriptor
 * ERM-515 Agreements | Handling removed/deleted resources
 * ERM-508 Support multiple POLs on a single Agreement Line
 * ERM-505 Move test data so it's only active for the diku tenant only
 * ERM-500 reasonForClosure Field is not cleared
 * ERM-498 Modify KB Local admin to improve the retrieval and display of logs
 * ERM-477 License and agreement APIs are not protected by FOLIO permissions
   * ERM-479 Add permission definitions and api endpoint config
 * ERM-465 Close an Agreement
   * ERM-483 Migrate agreement status "Rejected" to 'Reason for closure'
 * ERM-462 Agreement | Export resources covered by this agreement gives error
 * ERM-457 Use Agreement Line active from/active to dates when calculating current/expected/previous content of agreement
 * ERM-443	Remove "existing coverage statements overlapping" messages from info log
 * ERM-440 KB Local Admin | Titles not searchable/viewable after file upload
 * ERM-419 Duplicate refdata entries in folio builds
 * ERM-418 Duplicate titles in KBART export
 * ERM-396 Viewing upcoming joiners/leavers for a package
 * ERM-395 Populate access start and access end dates
 * ERM-394 Viewing only current content of package in UI
   * ERM-407 Ensure "E-resources in package" concertina only includes resources currently part of the package
   * ERM-406 Ensure Agreement resources export only includes resources currently part of the package
 * ERM-393 Reporting change to the date a resource joined/date left package
   * ERM-401 Local KB Admin | Add info logging for number of titles where access_start updated
   * ERM-400 Local KB Admin | Add info logging for number of titles where access_end updated
 * ERM-392 Reporting change in title count in a package
 * ERM-377 entitlementOptions endpoint is too slow
 * ERM-376 Export full Agreement as JSON
 * ERM-362 Issue with calling install?purge=true option multiple times
 * ERM-349 Create New Local KB Import - backend work
 * ERM-338 Log import/sync errors on package import
 * ERM-315 Linking PO Line to an agreement line for an eHolding resource fails
 * ERM-297 File attachment over 10MB causes out of memory errors
 * ERM-285 Entitlement Options endpoint throws a 500 Error
 * ERM-182 On sync with remote KB, keep TIPPs where coverage data is incompatible 

## 1.8.0 2019-09-11
 * ERM-440 KB Local Admin | Titles not searchable/viewable after file upload
 * ERM-349 Create New Local KB Import - backend work
 * ERM-338 Log import/sync errors on package import
 * ERM-182 On sync with remote KB, keep TIPPs where coverage data is incompatible 

## 1.8.0 2019-08-12
 * ERM-290 Set up Job Management for Local KB Admin
   * ERM-334 Delete job not in progress
 * ERM-274 Add cleanup task for orphan file uploads
 * ERM-265 Export Agreement data as JSON
 * ERM-215 Export Agreement data as KBART
 * ERM-327 Export entitlements as JSON from single agreement
 * ERM-328 Export entitlements as KBART from single agreement

## 1.8.0 2019-07-24
 * ERM-273 Manage usage data providers on agreements
   * ERM-291 Add usage data provider to subscription agreement
 * ERM-262 Agreement start and end date display with a time offset in some environments
 * ERM-189 Custom coverage dates display with a time offset in some environments 

## 1.7.0 2019-06-11
 * ERM-259 Set supplementary information for an Agreement
 * ERM-245 Tenant bootstrap improvements
   * ERM-249 Create bootstrap data
   * ERM-247 Change descriptors to reflect new interface version

## 1.6.0 2019-05-21
 * ERM-220 Support Organizations app as source of Organizations in Agreements
 * ERM-198 Ensure that mod-agreements used in place of olf-erm
 * ERM-92  Require UUIDs that are RFC 4122 compliant
   * ERM-135 Change UUID generator from UUID to UUID-2

## 1.5.0 2019-05-07

 * ERM-166 Remove unwanted extra license section
 * ERM-133 Configure Document Categories
 * ERM-143 Add License / Supplementaty License Information Panel UI
 * ERM-181 Fix data sync issue with GOKb (Resumption Token and Broken Coverage)
 * ERM-139 Convert from SearchAndSort to SearchAndSortQuery
 * ERM-79 Set supplementary informaiton for a license
 * ERM-173 Manage Tags on Agreements
 * ERM-174 Seach Agreements by Tag
 * ERM-194 BUGFIX: Opening edit/create license with only one page does not work

## 1.4.0 2019-04-08

 * ERM-115 Provide correct data for agreement line
 * ERM-111 Build Settings Page
 * ERM-112 Build Wrapper Component for supression
 * ERM-113 Use Wrapper Component in Agreements
 * ERM-114 Write tests
 * ERM-98 Rendering Controlling Terms License
 * ERM-127 Resources with no coverage set should not display
 * ERM-110 Agreement Detail record - View attached EBSCO eResource
 * ERM-109 Support the ability to create an agreement from eHoldings
 * ERM-108 Supress agreements app functions
 * ERM-64 Show Controlling License Terms

## 1.3.0 Not issued

## 1.2.0 2019-03-22
 * ERM-130 Sort order of multiple coverage statements should be ascending by start date
 * ERM-129 Cannot edit custom coverage dates once they have been added
 * ERM-65 Manage custom entitlement coverage for titles
 * ERM-91 Indicate the coverage for a title within an Agreement
 * ERM-63 View linked agreement details in a license 

## 1.1.0 2019-03-12
 * ERM-59 Manage licenses linked to agreements
 * ERM-71 Add Model for JSON resource
 * ERM-47 Fix defaults in SubsciriptionAgreementOrg
 * ERM-46 Update note about a license for an agreement
 * ERM-41 Manage external licenses for an Agreement
 * ERM-70 Add LicenseAttachment Domain model
 * ERM-44 Remove an external license from an Agreement
 * ERM-43 Edit external license details
 * ERM-42 Add external license for an Agreement
 * ERM-7 Add an Organisation to a License 

## 1.0.3 2019-02-23

 * ERM-1 eResource Managers can manually create licenses
 * ERM-6 Set pre-defined Terms for a License
 * ERM-7 Add an Organisation to a License
 * ERM-8 Add an Organisation to an existing License
 * ERM-10 Remove an Organisation from a License
 * ERM-11 eResource Managers can edit basic license details
 * ERM-12 Filter License Search Results by License Status
 * ERM-13 Filter License Search Results by License Type
 * ERM-16 Set open-ended License Expiry
 * ERM-17 See basic License details in search results
 * ERM-35 Filter Agreement Search Results by Organisation Role
