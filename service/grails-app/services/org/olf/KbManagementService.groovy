package org.olf

import static groovy.transform.TypeCheckingMode.SKIP

import com.k_int.web.toolkit.SimpleLookupService

import org.olf.dataimport.internal.PackageSchema.ContentItemSchema

import org.olf.kb.ErmResource
import org.olf.kb.PackageContentItem
import org.olf.kb.PlatformTitleInstance
import org.olf.kb.TitleInstance
import org.olf.kb.Platform
import org.olf.kb.IdentifierOccurrence

import org.olf.dataimport.internal.TitleInstanceResolverService

import com.k_int.web.toolkit.settings.AppSetting

import org.hibernate.sql.JoinType
import grails.gorm.DetachedCriteria

import org.springframework.scheduling.annotation.Scheduled

import com.k_int.okapi.OkapiTenantResolver

import grails.events.annotation.Subscriber
import grails.gorm.multitenancy.Tenants
import groovy.transform.CompileStatic

import java.time.Instant
import java.time.temporal.ChronoUnit
import groovy.util.logging.Slf4j

/**
 * See http://guides.grails.org/grails-scheduled/guide/index.html for info on this way of
 * scheduling tasks
 */
@Slf4j
@CompileStatic
class KbManagementService {
  // This service used to hold MatchKey related methods, but is now empty.
}
