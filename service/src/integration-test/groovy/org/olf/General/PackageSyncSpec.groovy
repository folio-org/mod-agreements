package org.olf.General

import org.olf.BaseSpec

import org.olf.kb.Pkg

import com.k_int.okapi.OkapiTenantResolver

import grails.gorm.multitenancy.Tenants
import grails.testing.mixin.integration.Integration
import spock.lang.*

import groovyx.net.http.HttpException

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
      ingestType  | packageFileName                                      | packageName               | expectedSyncContentsFromSource
      'kbart'     | "Testdata_KBART_AnnualReviews.tsv"                   | "KbartImportPackage1"     | true
      'json'      | "simple_pkg_with_syncContentsFromSource_true.json"   | "K-Int Test Package 001"  | true
      'json'      | "simple_pkg_with_syncContentsFromSource_null.json"   | "K-Int Test Package 002"  | false
  }

  @Unroll
  void "Test ingesting on top of syncStatus: null" ( ) {
    when: 'we remove all sync statuses from the database and fetch package "K-Int Test Package 001"'
      withTenantNewTransaction {
        Pkg.executeUpdate("""
          UPDATE Pkg AS pkg SET pkg.syncContentsFromSource = NULL
        """.toString(), [])
      }
      List resp = doGet("/erm/packages", [filters: ["name==K-Int Test Package 001"]]);
      Map pkg = resp?.getAt(0);
    then: 'Package sync status is null'
      pkg.syncContentsFromSource == null;
    when: 'Package is subsequently reingested and refetched'
      withTenant {
        importPackageFromFileViaService(
            "simple_pkg_with_syncContentsFromSource_true.json",
            specPackagePath
        )
      }
      resp = doGet("/erm/packages", [filters: ["name==K-Int Test Package 001"]]);
      pkg = resp?.getAt(0);
    then: 'Sync status has not been overwritten'
      pkg.syncContentsFromSource == null;
  }

  @Unroll
  void "Test package PUT can not overwrite syncContentsFromSource" ( ) {
    when: 'We fetch the package K-Int Test Package 001'
      List resp = doGet("/erm/packages", [filters: ["name==K-Int Test Package 001"]]);

      Map pkg = resp?.getAt(0);

    then: "Single package found and is as expected"
      resp.size() == 1
      pkg.id != null
      pkg.name == 'K-Int Test Package 001'
      pkg.syncContentsFromSource == null;

    String pkgId = pkg.id;
    Map putResp = [:];

    when: 'We PUT just the package name'
      withTenant {
        putResp = doPut("/erm/packages/${pkgId}", {
          'name' 'Wibble'
        });
      }
    then: 'PUT response is as expected'
      putResp.name == 'Wibble'
    when: 'Package is subsequently refetched'
      Map pkgResp = [:]
      pkgResp = doGet("/erm/packages/${pkgId}");
    then: 'Name has changed'
      pkgResp.name == 'Wibble'
    when: 'We include a change to syncContentsFromSource in the PUT'
      def errorObj;
      try {
        withTenant {
          putResp = doPut("/erm/packages/${pkgId}", {
            'name' 'K-Int Test Package 001'
            'syncContentsFromSource' 'true'
          });
        }
      } catch (HttpException e) {
        errorObj = e;
      }

    then: 'PUT fails with "Unprocessable Entity"'
      errorObj != null
      errorObj.getMessage() == 'Unprocessable Entity' // I wish this were a little more helpful ngl
    when: 'Package is subsequently refetched'
      pkgResp = doGet("/erm/packages/${pkgId}");
    then: 'Name hasn\'t changed back, and syncContentsFromSource remains null'
      pkgResp.name == 'Wibble'
      pkgResp.syncContentsFromSource == null
  }
}

