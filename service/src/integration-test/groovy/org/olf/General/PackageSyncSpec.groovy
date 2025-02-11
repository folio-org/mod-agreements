package org.olf.General

import org.olf.BaseSpec

import org.olf.kb.Pkg

import com.k_int.okapi.OkapiTenantResolver

import grails.gorm.multitenancy.Tenants
import grails.testing.mixin.integration.Integration
import spock.lang.*

@Integration
@Stepwise
class PackageSyncSpec extends BaseSpec {
  private static Map kbartOptions = [
      packageName: 'KbartImportPackage1',
      packageSource: 'testSource',
      packageReference: 'testReference',
      packageProvider: 'testProvider',
      trustedSourceTI: true
  ];

  private static specPackagePath = "src/integration-test/resources/packages/packageSyncSpec"

  @Unroll
  void "Test Package #packageName import via #ingestType results in syncContentsFromSource: #expectedSyncContentsFromSource" (
      String ingestType,
      String packageFileName,
      String packageName,
      boolean expectedSyncContentsFromSource
  ) {
    when: 'ingest #ingestType file'
    def resultBool = false
    def resultMap = [:]

    if (ingestType == 'kbart') {
      // KBART import
      withTenant {
        resultBool = importKBARTPackageViaService(
            packageFileName,
            specPackagePath,
            kbartOptions
        )
      }
    } else {
      // JSON import
      withTenant {
        resultMap = importPackageFromFileViaService(
            packageFileName,
            specPackagePath
        )
      }
    }

    then: 'Import succeeded'
    if (ingestType == 'kbart') {
      assert resultBool == true
    } else {
      assert resultMap.packageId != null
    }

    when: 'Package subsequently fetched'
    List resp = doGet("/erm/packages", [filters: ["name==${packageName}"]]);
    Map pkg = resp?.getAt(0);

    then: "Single package found and is as expected"
    resp.size() == 1
    pkg.id != null
    // TODO should probably ensure a lot more than just this
    pkg.syncContentsFromSource == expectedSyncContentsFromSource;

    where:
    ingestType | packageFileName                                      | packageName               | expectedSyncContentsFromSource
      'kbart'  | "Testdata_KBART_AnnualReviews.tsv"                   | "KbartImportPackage1"     | true
      'json'   | "simple_pkg_with_syncContentsFromSource_true.json"   | "K-Int Test Package 001"  | true
      'json'   | "simple_pkg_with_syncContentsFromSource_null.json"   | "K-Int Test Package 002"  | false
  }

/*
  @Unroll
  void "Test ingesting on top of syncStatus: null" ( ) {
    when: 'ingest JSON file'

    def result = [:]
    withTenant {
      result = importPackageFromFileViaService(
          "brill-eg.json"
      )
    }

    then: 'Import succeeded'
    result.packageId != null


    when: 'Package subsequently fetched'
    List resp = doGet("/erm/packages", [filters: ['name==K-Int Test Package 002']]);
    Map pkg = resp?.getAt(0);
    then: "Single package found and is as expected"
    resp.size() == 1
    pkg.id != null
    // TODO should probably ensure a lot more than just this
    pkg.syncContentsFromSource == true;
  }*/
}

