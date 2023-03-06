package org.olf

import org.slf4j.MDC
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.validation.ObjectError

import org.springframework.validation.Errors
import org.grails.datastore.gorm.GormEntity
import grails.validation.Validateable

import grails.util.Holders

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * This service works at the module level, it's often called without a tenant context.
 * --- I think.... see coverage service
 */
@CompileStatic
@Slf4j
class UtilityService {
  private static MessageSource getMessageSource() {
    Holders.grailsApplication.mainContext.getBean('messageSource') as MessageSource
  }

  // Small utility method to log out any errors
  private void logErrors(Errors errors) {
    errors.allErrors.each { ObjectError error ->
      log.error "${ messageSource.getMessage(error, LocaleContextHolder.locale) }"
    }
  }

  // Small utility method to systematically check binding and log out any errors
  // Provide separate checkValidBinding for Validateable vs GormEntity objects
  public boolean checkValidBinding(GormEntity obj) {
    boolean validBinding = false;
    // Check for binding errors.
    if (!obj.errors.hasErrors()) {
      // Validate the actual values now. And check for constraint violations
      obj.validate()
      if (!obj.errors.hasErrors()) {
      
        validBinding = true;
      } else {
        // Log the errors.
        logErrors(obj.errors)
      }
    } else {
      // Log the errors.
      logErrors(obj.errors)
    }

    validBinding
  }

  // Small utility method to systematically check binding and log out any errors
  // Provide separate checkValidBinding for Validateable vs GormEntity objects
  public boolean checkValidBinding(Validateable obj) {
    boolean validBinding = false;
    // Check for binding errors.
    if (!obj.errors.hasErrors()) {
      // Validate the actual values now. And check for constraint violations
      obj.validate()
      if (!obj.errors.hasErrors()) {
      
        validBinding = true;
      } else {
        // Log the errors.
        logErrors(obj.errors)
      }
    } else {
      // Log the errors.
      logErrors(obj.errors)
    }

    validBinding
  }
}