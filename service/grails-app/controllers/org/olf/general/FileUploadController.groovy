package org.olf.general

import static org.springframework.http.HttpStatus.*

import org.springframework.web.multipart.MultipartFile

import com.k_int.okapi.OkapiTenantAwareController
import com.k_int.web.toolkit.files.FileUpload
import com.k_int.web.toolkit.files.FileUploadService

import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j

@Slf4j
@CurrentTenant
class FileUploadController extends OkapiTenantAwareController<FileUpload> {

  FileUploadService fileUploadService
  
  FileUploadController()  {
    super(FileUpload)
  }

  @Transactional
  def uploadFile() {
     
    FileUpload.withTransaction {
      if(handleReadOnly()) {
        return
      }
    
      MultipartFile f = request.getFile('upload')
    
      FileUpload fileUpload = fileUploadService.save(f)
      if (fileUpload.hasErrors()) {
          transactionStatus.setRollbackOnly()
          respond fileUpload.errors, view:'create' // STATUS CODE 422
          return
      }

      respond fileUpload, [status: CREATED]
    }

  }
  
  @Transactional(readOnly=true)
  def downloadFile() {
    FileUpload.withTransaction {
      FileUpload fileUpload = FileUpload.read(params.fileUploadId)

      // Do the right thing depending upon the fileObject type - currently S3 or LOB
      InputStream is = fileUploadService.getInputStreamFor(fileUpload.fileObject);
    
      // render file: fileUpload.fileObject.fileContents.binaryStream, contentType: fileUpload.fileContentType
      render file: is, contentType: fileUpload.fileContentType
    }
  }
}
