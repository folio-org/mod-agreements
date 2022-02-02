package org.olf

import java.time.LocalDate
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import org.apache.commons.io.input.BOMInputStream
import org.springframework.web.multipart.MultipartFile

import com.opencsv.ICSVParser
import com.opencsv.CSVParser
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReader
import com.opencsv.CSVReaderBuilder

import java.net.URLEncoder

import com.k_int.okapi.OkapiTenantResolver
import grails.gorm.multitenancy.Tenants
import org.olf.kb.Pkg

import grails.testing.mixin.integration.Integration
import spock.lang.*

@Stepwise
@Integration
class MatchKeySpec extends BaseSpec {
  
  @Shared
  String pkg_id

  @Shared
  int thisYear = LocalDate.now().year

  // Importing from file mimics the behaviour of packageImportJob
  // Importing fromKBART mimics the behaviour of kbartImportKob
  def importService

  def 'Import an erm schema test package' (final String package_name) {
    when: 'File loaded'

      def jsonSlurper = new JsonSlurper()
      def package_data = jsonSlurper.parse(new File('src/integration-test/resources/packages/mod-agreement-package-import-sample.json'))
      
      final String tenantid = currentTenant.toLowerCase()
      log.debug("Create new package with tenant ${tenantid}");
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantid )) {
        Pkg.withTransaction { status ->
          importService.importFromFile( package_data )
        }
      }

    and: 'Find the package by name'
      List resp = doGet("/erm/packages?match=name&term=${URLEncoder.encode(package_name, "UTF-8")}")
      pkg_id = resp[0].id
      
    then: 'Expect package found'
      assert pkg_id != null
      assert resp?.getAt(0)?.name == package_name

    where:
      package_name << ["Middle East & Islamic Studies Journal Collection"]
  }

  def 'Check MatchKeys are established as expected for each PCI and PTI' (final String name, final String electronicIssn, final String printIssn) {
    when: "PCI for ${name} is fetched"
      ArrayList httpResult = doGet("/erm/pci?match=name&term=${URLEncoder.encode(name, "UTF-8")}")
      ArrayList matchKeys = httpResult[0].matchKeys

      ArrayList ptiMatchKeys = httpResult[0].pti.matchKeys
    
    then:
      assert httpResult[0].id != null
      // PCI matchkeys
      assert matchKeys.find { mk -> mk.key == 'title_string' }?.value == name
      assert matchKeys.find { mk -> mk.key == 'electronic_issn' }?.value == electronicIssn
      assert matchKeys.find { mk -> mk.key == 'print_issn' }?.value == printIssn

      // PTI matchKeys ( should be the same)
      assert ptiMatchKeys.find { mk -> mk.key == 'title_string' }?.value == name
      assert ptiMatchKeys.find { mk -> mk.key == 'electronic_issn' }?.value == electronicIssn
      assert ptiMatchKeys.find { mk -> mk.key == 'print_issn' }?.value == printIssn

    where:
    name                                                       | electronicIssn | printIssn
    "Iran and the Caucasus"                                    | "1573-384x"    | "1609-8498"
    "Abgadiyat"                                                | "2213-8609"    | "1687-8280"
    "Indo-Iranian Journal"                                     | "1572-8536"    | "0019-7246"
    "Journal of the Economic and Social History of the Orient" | "1568-5209"    | "0022-4995"
  }


  def 'Import an internal schema test package' (final String package_name) {
    when: 'File loaded'

      def jsonSlurper = new JsonSlurper()
      def package_data = jsonSlurper.parse(new File('src/integration-test/resources/packages/apa_1062.json'))
      
      final String tenantid = currentTenant.toLowerCase()
      log.debug("Create new package with tenant ${tenantid}");
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantid )) {
        Pkg.withTransaction { status ->
          importService.importFromFile( package_data )
        }
      }

    and: 'Find the package by name'
      List resp = doGet("/erm/packages?match=name&term=${URLEncoder.encode(package_name, "UTF-8")}")
      pkg_id = resp[0].id
      
    then: 'Expect package found'
      assert pkg_id != null
      assert resp?.getAt(0)?.name == package_name
    
     where:
      package_name << ["American Psychological Association:Master"]
  }

  def 'Check MatchKeys are established as expected for each PCI and PTI' (
    final String name,
    final String electronicIssn,
    final String printIssn,
    final String doi
  ) {
    when: "PCI for ${name} is fetched"
      ArrayList httpResult = doGet("/erm/pci?match=name&term=${URLEncoder.encode(name, "UTF-8")}")
      ArrayList matchKeys = httpResult[0].matchKeys

      ArrayList ptiMatchKeys = httpResult[0].pti.matchKeys
    
    then:
      assert httpResult[0].id != null
      // PCI matchkeys
      assert matchKeys.find { mk -> mk.key == 'title_string' }?.value == name
      assert matchKeys.find { mk -> mk.key == 'electronic_issn' }?.value == electronicIssn
      assert matchKeys.find { mk -> mk.key == 'print_issn' }?.value == printIssn
      assert matchKeys.find { mk -> mk.key == 'electronic_doi' }?.value == doi


      // PTI matchKeys ( should be the same)
      assert ptiMatchKeys.find { mk -> mk.key == 'title_string' }?.value == name
      assert ptiMatchKeys.find { mk -> mk.key == 'electronic_issn' }?.value == electronicIssn
      assert ptiMatchKeys.find { mk -> mk.key == 'print_issn' }?.value == printIssn
      assert ptiMatchKeys.find { mk -> mk.key == 'electronic_doi' }?.value == doi


    where:
    name                                           | electronicIssn | printIssn   | doi
    "American Journal of Orthopsychiatry"          | "1939-0025"    | "0002-9432" | "10.1111/(ISSN)1939-0025"
    "European Journal of Psychological Assessment" | "2151-2426"    | "1015-5759" | null
    "Emotion"                                      | "1931-1516"    | "1528-3542" | null
    "Dreaming"                                     | "1573-3351"    | "1053-0797" | null
  }

  def 'Import kbart packages' (final String package_name, final String fileName) {
    when: 'File loaded'
      Map packageInfo = [
        packageName: package_name,
        packageSource: package_name,
        packageReference: package_name,
        packageProvider: package_name,
        trustedSourceTI: true
      ]
  
      BOMInputStream bis = new BOMInputStream(new FileInputStream(new File("src/integration-test/resources/packages/${fileName}")));
      Reader fr = new InputStreamReader(bis);
      CSVParser parser = new CSVParserBuilder().withSeparator('\t' as char)
        .withQuoteChar(ICSVParser.DEFAULT_QUOTE_CHARACTER)
        .withEscapeChar(ICSVParser.DEFAULT_ESCAPE_CHARACTER)
      .build();

      CSVReader package_data = new CSVReaderBuilder(fr).withCSVParser(parser).build();

      final String tenantid = currentTenant.toLowerCase()
      log.debug("Create new package with tenant ${tenantid}");
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantid )) {
        Pkg.withTransaction { status ->
          importService.importPackageFromKbart( package_data, packageInfo )
        }
      }

    and: 'Find the package by name'
      List resp = doGet("/erm/packages?match=name&term=${URLEncoder.encode(package_name, "UTF-8")}")
      pkg_id = resp[0].id
      
    then: 'Expect package found'
      assert pkg_id != null
      assert resp?.getAt(0)?.name == package_name

    where:
      package_name     | fileName
      "Test package 1" | "Springer_Global_J.B._Metzler_Humanities_eBooks_2021_2021-05-01.tsv"
      "Test package 2" | "CanadianSciencePublishing_Global_AllTitles_2020-10-21-1603278035587.tsv"
  }

 def 'Check MatchKeys are established as expected for each PCI and PTI' (
    final String name,
    final String electronicIsbn,
    final String printIsbn,
    final String electronicIssn,
    final String printIssn,
    final String author,
    final String editor,
    final String dateElectronicPublished,
    final String datePrintPublished,
    final String edition,
    final String monographVolume
  ) {
    when: "PCI for ${name} is fetched"
      
      ArrayList httpResult = doGet("/erm/pci?match=name&term=${URLEncoder.encode(name, "UTF-8")}")
      ArrayList matchKeys = httpResult[0].matchKeys
      ArrayList ptiMatchKeys = httpResult[0].pti.matchKeys

    then:
      assert httpResult[0].id != null
      // PCI matchkeys
      assert matchKeys.find { mk -> mk.key == 'title_string' }?.value == name
      assert matchKeys.find { mk -> mk.key == 'electronic_isbn' }?.value == electronicIsbn
      assert matchKeys.find { mk -> mk.key == 'print_isbn' }?.value == printIsbn
      assert matchKeys.find { mk -> mk.key == 'electronic_issn' }?.value == electronicIssn
      assert matchKeys.find { mk -> mk.key == 'print_issn' }?.value == printIssn
      assert matchKeys.find { mk -> mk.key == 'author' }?.value == author
      assert matchKeys.find { mk -> mk.key == 'editor' }?.value == editor
      assert matchKeys.find { mk -> mk.key == 'date_electronic_published' }?.value == dateElectronicPublished
      assert matchKeys.find { mk -> mk.key == 'date_print_published' }?.value == datePrintPublished
      assert matchKeys.find { mk -> mk.key == 'edition' }?.value == edition
      assert matchKeys.find { mk -> mk.key == 'monograph_volume' }?.value == monographVolume

      // PTI matchKeys ( should be the same)
      assert ptiMatchKeys.find { mk -> mk.key == 'title_string' }?.value == name
      assert ptiMatchKeys.find { mk -> mk.key == 'electronic_isbn' }?.value == electronicIsbn
      assert ptiMatchKeys.find { mk -> mk.key == 'print_isbn' }?.value == printIsbn
      assert ptiMatchKeys.find { mk -> mk.key == 'electronic_issn' }?.value == electronicIssn
      assert ptiMatchKeys.find { mk -> mk.key == 'print_issn' }?.value == printIssn
      assert ptiMatchKeys.find { mk -> mk.key == 'author' }?.value == author
      assert ptiMatchKeys.find { mk -> mk.key == 'editor' }?.value == editor
      assert ptiMatchKeys.find { mk -> mk.key == 'date_electronic_published' }?.value == dateElectronicPublished
      assert ptiMatchKeys.find { mk -> mk.key == 'date_print_published' }?.value == datePrintPublished
      assert ptiMatchKeys.find { mk -> mk.key == 'edition' }?.value == edition
      assert ptiMatchKeys.find { mk -> mk.key == 'monograph_volume' }?.value == monographVolume

    where:
    name                                                              | electronicIsbn      | printIsbn           | electronicIssn | printIssn   | author      | editor   | dateElectronicPublished | datePrintPublished | edition | monographVolume
    "Science, Agriculture and Food Security"                          | "978-0-9877172-8-3" | "978-0-660-16210-2" | null           | null        | "Hulse"     | null     | "2011-11-04"            | "1996-01-01"       | null    | null
    "Canadian Journal of Community Mental Health"                     | null                | null                | null           | "0713-3936" | null        | null     | null                    | null               | null    | null 
    "STEM Fellowship Journal"                                         | null                | null                | "2369-0399"    | null        | null        | null     | null                    | null               | null    | null
    "Molecular Symmetry and Spectroscopy, 2nd Ed."                    | "978-0-660-18464-7" | "978-0-660-19628-2" | null           | null        | "Bunker"    | null     | "2011-11-30"            | "2006-01-01"       | null    | null
    "Amua-gaig-e: The ethnobotany of the Amungme of Papua, Indonesia" | "978-1-927346-22-8" | "978-1-927346-21-1" | null           | null        | "Cook"      | null     | "2016-05-06"            | "2016-01-01"       | null    | null
    "Die Alice-Maschine"                                              | "978-3-476-05707-5" | "978-3-476-05706-8" | null           | null        | "Lötscher"  | null     | "2020"                  | "2020"             | "1"     | "6"
    "Alexander von Humboldt: Geographie der Pflanzen"                 | "978-3-476-04965-0" | "978-3-476-04964-3" | null           | null        | null        | "Päßler" | "2020"                  | "2020"             | "1"     | "1"
    "Europa im Umbruch"                                               | "978-3-476-05730-3" | "978-3-476-05729-7" | null           | null        | null        | "Raß"    | "2020"                  | "2020"             | "1"     | null
  }
}
