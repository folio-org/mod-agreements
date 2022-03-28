package org.olf.general.jobs

import grails.gorm.MultiTenant
import org.apache.commons.io.input.BOMInputStream
import org.springframework.web.multipart.MultipartFile

import com.opencsv.ICSVParser
import com.opencsv.CSVParser
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReader
import com.opencsv.CSVReaderBuilder

class KbartImportJob extends PersistentJob implements MultiTenant<KbartImportJob> {
  
  String packageName
  String packageSource
  String packageReference
  String packageProvider
  boolean trustedSourceTI

  final Closure getWork() {
    
    final Closure theWork = { final String eventId, final String tenantId ->
    
      log.info "Running KBART Package Import Job"
      PersistentJob.withTransaction {
      
        // We should ensure the job is read into the current session. This closure will probably execute
        // in a future session and we need to reread the event in.
        final KbartImportJob job = KbartImportJob.read(eventId)

        Map packageInfo = [
          packageName: job.packageName,
          packageSource: job.packageSource,
          packageReference: job.packageReference,
          packageProvider: job.packageProvider,
          trustedSourceTI: job.trustedSourceTI
        ]

        boolean packageInfoValid = true
        if ( packageInfo.packageName == null ||
             packageInfo.packageSource == null ||
             packageInfo.packageReference == null ) {
          packageInfoValid = false
          log.error("Import is missing key package information")
        }

        if (job.fileUpload && packageInfoValid) {

          // BOMInputStream bis = new BOMInputStream(job.fileUpload.fileObject.fileContents.binaryStream);
          BOMInputStream bis = new BOMInputStream(fileUploadService.getInputStreamFor(job.fileUpload.fileObject));
          Reader fr = new InputStreamReader(bis);
          CSVParser parser = new CSVParserBuilder().withSeparator('\t' as char)
            .withQuoteChar(ICSVParser.DEFAULT_QUOTE_CHARACTER)
            .withEscapeChar(ICSVParser.DEFAULT_ESCAPE_CHARACTER)
          .build();

          CSVReader csvReader = new CSVReaderBuilder(fr).withCSVParser(parser).build();
          importService.importPackageFromKbart(csvReader, packageInfo)
        } else {
          log.error "No file attached to the Job."
        }
      }
    }.curry( this.id )
    
    theWork
  }
  
  void beforeValidate() {
    if (!this.name && this.fileUpload) {
      // Set the name from the file upload if no name has been set.
      this.name = "Import package from ${this.fileUpload.fileName}"
    }
  }
  
  static constraints = {
      fileUpload (nullable:false)
      packageProvider (nullable:true)
  }

  static mapping = {
                      version false
                 packageName column:'package_name'
               packageSource column:'package_source'
            packageReference column:'package_reference'
            packageProvider column: 'package_provider'
            trustedSourceTI column: 'trusted_source_ti'
  }
}
