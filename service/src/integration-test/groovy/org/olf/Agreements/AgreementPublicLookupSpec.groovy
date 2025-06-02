package org.olf.Agreements
import grails.testing.mixin.integration.Integration
import groovy.util.logging.Slf4j
import org.olf.BaseSpec
import org.olf.kb.PackageContentItem
import org.olf.kb.Pkg
import spock.lang.Ignore
import spock.lang.Shared
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.olf.erm.SubscriptionAgreement
import org.olf.erm.Entitlement

@Integration
@Slf4j
class AgreementPublicLookupSpec extends BaseSpec {
  @Shared
  String pkg_id


  @Ignore
  Map createAgreement(String name="test_agreement") {
    def today = LocalDate.now()
    def tomorrow = today.plusDays(1)

    def payload = [
      periods: [
        [
          startDate: today.format(DateTimeFormatter.ISO_LOCAL_DATE),
          endDate: tomorrow.format(DateTimeFormatter.ISO_LOCAL_DATE)
        ]
      ],
      name: name,
      agreementStatus: "active"
    ]

    def response = doPost("/erm/sas/", payload)

    return response as Map
  }

  @Ignore
  Map addEntitlementForAgreement(String agreementName, String resourceId) {
    String agreement_id;
    withTenant {
      String hql = """
            SELECT agreement.id 
            FROM SubscriptionAgreement agreement 
            WHERE agreement.name = :agreementName 
        """
      List results = SubscriptionAgreement.executeQuery(hql, [agreementName: agreementName])
      agreement_id = results.get(0)
    }


    return doPut("/erm/sas/${agreement_id}", {
      items ([
        {
          resource {
            id resourceId
          }
        }
      ])
    }) as Map
  }

  @Ignore
  Map updateEntitlementForAgreement(String agreementName, String resourceId) {
    String agreement_id;
    withTenant {
      String hql = """
            SELECT agreement.id 
            FROM SubscriptionAgreement agreement 
            WHERE agreement.name = :agreementName 
        """
      List results = SubscriptionAgreement.executeQuery(hql, [agreementName: agreementName])
      agreement_id = results.get(0)
    }

    def today = LocalDate.now()
    def dateInPast = today.minusDays(10)


    return doPut("/erm/sas/${agreement_id}", {
      items ([
        {
          resource {
            id resourceId
          }
        }
      ])
      activeTo: dateInPast.toString()
    }) as Map
  }

  Pkg findPkgByPackageName(String packageName) {
    log.info("Package name: " + packageName)
    withTenant {
      String hql = """
            SELECT package
            FROM Pkg package
            WHERE package.name = :packageName
        """
      List results = Pkg.executeQuery(hql, [packageName: packageName])
      if (results.size() > 1) {
        throw new IllegalStateException("Multiple Packages found for package name ${packageName}, one expected.")
      }
      return results.get(0);
    }
  }

  PackageContentItem findPCIByPackageName(String packageName, String titleName) {
    withTenant {
      String hql = """
            SELECT pci
            FROM PackageContentItem pci
            WHERE pci.pkg.name = :packageName
            AND pci.pti.titleInstance.work.title = :titleName
        """
      List results = PackageContentItem.executeQuery(hql, [packageName: packageName, titleName: titleName])
      if (results.size() > 1) {
        throw new IllegalStateException("Multiple PCIs found for package name ${packageName}, one expected.")
      }
      return results.get(0);
    }
  }

  @Shared
  String pci1Id;

  @Shared
  String pti1Id;

  @Shared
  String ti1Id;

  @Shared
  String pci2Id;

  @Shared
  String pti2Id;

  @Shared
  String ti2Id;

  @Shared
  PackageContentItem test_package_1_pci;

  void "Load Packages"() {

    when: 'File loaded'
    Map result = importPackageFromFileViaService('publicLookup/publicLookupPackage1.json')
    importPackageFromFileViaService('publicLookup/publicLookupPackage2.json')
    importPackageFromFileViaService('publicLookup/publicLookupPackage3.json')
    importPackageFromFileViaService('publicLookup/publicLookupPackage4.json')
    importPackageFromFileViaService('publicLookup/publicLookupPackage5.json')
    importPackageFromFileViaService('publicLookup/publicLookupPackage6.json')
    then: 'Package imported'
    result.packageImported == true

    when: "Looked up package with name"
    List resp = doGet("/erm/packages", [filters: ['name==test_package_1']])
    doGet("/erm/packages", [filters: ['name==test_package_2']])
    doGet("/erm/packages", [filters: ['name==test_package_3']])
    doGet("/erm/packages", [filters: ['name==test_package_4']])
    doGet("/erm/packages", [filters: ['name==test_package_5']])
    doGet("/erm/packages", [filters: ['name==test_package_6']])
    pkg_id = resp[0].id

    then: "Package found"
    log.info(resp.toListString())
    resp.size() == 1
    resp[0].id != null
  }

  void "Create Agreements and lines"() {

    when:
    Pkg test_package_1 = findPkgByPackageName("test_package_1")
    createAgreement("Agreement A")
    addEntitlementForAgreement("Agreement A", test_package_1.id)

    test_package_1_pci = findPCIByPackageName("test_package_1", "Academy of Management Learning & Education")
    createAgreement("Agreement B")
    addEntitlementForAgreement("Agreement B", test_package_1_pci.id)

    createAgreement("Agreement C")
    Pkg test_package_6 = findPkgByPackageName("test_package_6")
    addEntitlementForAgreement("Agreement C", test_package_6.id)

    createAgreement("Agreement D")
    PackageContentItem test_package_6_pci = findPCIByPackageName("test_package_6", "Academy of Management Learning & Education")
    addEntitlementForAgreement("Agreement D", test_package_6_pci.id)

    createAgreement("Agreement E")
    Pkg test_package_2 = findPkgByPackageName("test_package_2")
    addEntitlementForAgreement("Agreement E", test_package_2.id)

    createAgreement("Agreement F")
    PackageContentItem test_package_2_pci = findPCIByPackageName("test_package_2", "Academy of Management Learning & Education")
    addEntitlementForAgreement("Agreement F", test_package_2_pci.id)

    pci1Id = test_package_1_pci.id
    pti1Id = test_package_1_pci.pti.id
    ti1Id = test_package_1_pci.pti.titleInstance.id

    pci2Id = test_package_2_pci.id
    pti2Id = test_package_2_pci.pti.id
    ti2Id = test_package_2_pci.pti.titleInstance.id

    then:
    Map res = doGet("/erm/sas")[0]
    Map resEnt = doGet("/erm/entitlements")[0]
    log.info("Logging")
    log.info(res.toString())
    log.info(resEnt.toString())

    log.info("PCI ID: {}", pci1Id)
    doGet("/erm/sas/publicLookup?resourceId=${pci1Id}").get("records").forEach{Object record -> log.info(record.name.toString())}

    log.info("PTI ID: {}", pti1Id)
    doGet("/erm/sas/publicLookup?resourceId=${pti1Id}").get("records").forEach{Object record -> log.info(record.name.toString())}

    log.info("TI ID: {}", ti1Id)
    doGet("/erm/sas/publicLookup?resourceId=${ti1Id}").get("records").forEach{Object record -> log.info(record.name.toString())}

    log.info("PCI ID: {}", pci2Id)
    doGet("/erm/sas/publicLookup?resourceId=${pci2Id}").get("records").forEach{Object record -> log.info(record.name.toString())}

    log.info("PTI ID: {}", pti2Id)
    doGet("/erm/sas/publicLookup?resourceId=${pti2Id}").get("records").forEach{Object record -> log.info(record.name.toString())}

    log.info("TI ID: {}", ti2Id)
    doGet("/erm/sas/publicLookup?resourceId=${ti2Id}").get("records").forEach{Object record -> log.info(record.name.toString())}


  }

  void "Agreement B active to date in the past"() {
    Map httpResult = doGet("/erm/sas/${agreement_id}", [expand: 'items'])
    def index = httpResult.items.findIndexOf{ it.resource?.id == test_package_1_pci.id }
    httpResult.items[index].activeFrom = "${thisYear - 2}-01-01"
    httpResult.items[index].activeTo = "${thisYear -1}-12-31"
    httpResult = doPut("/erm/sas/${agreement_id}", httpResult, [expand: 'items'])

    then:
    List res = doGet("/erm/sas")
    List resEnt = doGet("/erm/entitlements")
    log.info("Logging")
    log.info(res.toListString())
    log.info(resEnt.toListString())

    log.info("PCI ID: {}", pci1Id)
    doGet("/erm/sas/publicLookup?resourceId=${pci1Id}").get("records").forEach{Object record -> log.info(record.name.toString())}

    log.info("PTI ID: {}", pti1Id)
    doGet("/erm/sas/publicLookup?resourceId=${pti1Id}").get("records").forEach{Object record -> log.info(record.name.toString())}

    log.info("TI ID: {}", ti1Id)
    doGet("/erm/sas/publicLookup?resourceId=${ti1Id}").get("records").forEach{Object record -> log.info(record.name.toString())}

  }
}
