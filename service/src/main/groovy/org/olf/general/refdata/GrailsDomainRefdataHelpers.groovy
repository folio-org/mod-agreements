package org.olf.general.refdata

import java.beans.PropertyDescriptor
import java.lang.reflect.Field

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.olf.general.RefdataCategory
import org.olf.general.RefdataValue

import com.k_int.web.toolkit.utils.DomainUtils

import grails.core.GrailsApplication
import grails.core.GrailsClass
import grails.gorm.multitenancy.Tenants
import grails.util.GrailsClassUtils
import grails.util.GrailsNameUtils
import grails.util.Holders
import groovy.transform.Memoized
import groovy.util.logging.Slf4j

@Slf4j
class GrailsDomainRefdataHelpers {
  
  
  /**
   * Checks to see if this field is annotated with the Category annotation and
   * uses it if present. Else it is derived from simple classname and property name.
   * 
   * @param c - The class to which the property belongs
   * @param propertyName - The name of the property to examine.
   * @return The derived or specified category name
   */
//  @Memoized(maxCacheSize=50)
  public static String getCategoryString(final Class<?> c, final String propertyName) {
    String typeString = "${c.simpleName}.${GrailsNameUtils.getClassName(propertyName)}"
    try {
      // Check for annotated values first.
      Field field = c.getDeclaredField("${propertyName}")
      CategoryId cat = field.getAnnotation(CategoryId.class)
      
      if (cat) typeString = cat.value()
    } catch (NoSuchFieldException e) {
      // We can safely ignore this exception here. GORM does not force you to
      // explicitly declare a field in all cases.
    }
    
    typeString
  }
  
  public static void setDefaultsForTenant (final Serializable tid) {
    final GrailsApplication grailsApplication = Holders.grailsApplication
    final GrailsClass[] domainObj = grailsApplication.getArtefacts("Domain")
    Tenants.withId ( tid ) {
      domainObj.each { GrailsClass gc ->
        GrailsDomainRefdataHelpers.setDefaults(gc)
      }
    }
  }
  
  public static void setDefaults (final GrailsClass gc) {
    PersistentEntity pe = DomainUtils.resolveDomainClass(gc)
    if (pe) {
      setDefaults(pe)
    }
  }
  
  public static void setDefaults(PersistentEntity pe) {
    final Class<?> targetClass = pe.javaClass
    
    log.debug "Testing class ${targetClass.name} for refData defaults"
    
    if (!GrailsClassUtils.isAssignableOrConvertibleFrom(RefdataValue.class, targetClass)) {
      
      // Get the declared fields.
      for (PersistentProperty p : pe.getPersistentProperties()) {
        Class<?> type = p instanceof Association ? p.associatedEntity?.javaClass : null
        if (GrailsClassUtils.isAssignableOrConvertibleFrom(RefdataValue.class, type)) {
          
          // The field name.
          final String fn = p.name
          
          log.debug "  Found property ${fn} that has compatible type"
          
          // Create our type string once per property.
          final String typeString = targetClass."${fn}Category"
          
          log.debug "  Category: ${typeString}"
          
          try {
            // Check for annotated valuesd.
            Field field = targetClass.getDeclaredField("${fn}")
            Defaults defaults = field.getAnnotation(Defaults.class)
            if (defaults) {
              
              log.debug "  Declared defaults:"
              defaults.value().each {
                log.debug "    ${it}"
                RefdataValue.lookupOrCreate(
                  typeString,
                  it,
                  null,
                  type
                )
              }
            }
          } catch (NoSuchFieldException e) {
            // We can safely ignore this exception here. GORM does not force you to
            // explicitly declare a field in all cases.
          }
        }
      }
    }
  }

  public static void addMethods (GrailsClass gc) {
    PersistentEntity pe = DomainUtils.resolveDomainClass(gc)
    if (pe) {
      addMethods(pe)
    }
  }
  
  public static void addMethods (PersistentEntity pe) {
    final Class<?> targetClass = pe.javaClass
    final GrailsApplication grailsApplication = Holders.grailsApplication

    if ( !(RefdataValue.class.isAssignableFrom(targetClass) || RefdataCategory.class.isAssignableFrom(targetClass)) ) {

      // Get each refdataValue type property (or derivative)
      PropertyDescriptor[] props = GrailsClassUtils.getPropertiesAssignableToType(targetClass, RefdataValue.class)
      for (final PropertyDescriptor pd : props) {

        Class<? extends RefdataValue> typeClass = pd.getPropertyType()

        // The ClassName representation of the property name.
        final String upperName = GrailsNameUtils.getClassName(pd.name)
        final String typeString = GrailsDomainRefdataHelpers.getCategoryString (targetClass, pd.name)
        
        targetClass.metaClass.static."get${upperName}Category" = { typeString }
        log.debug ("Added '${pd.name}Category' to ${targetClass}")

        // Add static method for refdata values lookup.
        targetClass.metaClass.static."all${upperName}Values" << { Map parameters = [:] ->
          // Default read only
          def param = ["readOnly": true]
          if (parameters) param.putAll(parameters)

          // Do the lookup.
          return typeClass.findAll('FROM ' + typeClass.simpleName + ' as rdv WHERE rdv.owner.desc=:desc', [desc: "${typeString}"], param)
        }
        
        // Lookup single value.
        targetClass.metaClass.static."lookup${upperName}" << { String value ->
          // Do the lookup.
          def val = typeClass.find('FROM ' + typeClass.simpleName + ' as rdv WHERE rdv.value=:val AND rdv.owner.desc=:desc', [val: typeClass.normValue(value), desc:"${typeString}"],  ["readOnly": true])
          val
        }
        
        targetClass.metaClass.static."lookupOrCreate${upperName}" << { String value ->
          // Do the lookup.
          typeClass.lookupOrCreate(typeString, value, value, typeClass)
        }

        log.debug ("Added static methods ['all${upperName}Values', 'lookup${upperName}(value)', 'set${upperName}FromString(value)', 'lookupOrCreate${upperName}(value)'] to ${targetClass}")
        
        // Add instance method method for setting refdata value from string.
        targetClass.metaClass."set${upperName}FromString" = { final String value ->
          
          log.info "${targetClass.simpleName}.set${upperName}FromString ( '${value}' )"
          delegate."set${upperName}" ( targetClass."lookupOrCreate${upperName}" (value) )
        }

        log.debug ("Added instance method 'set${upperName}FromString' to ${targetClass}")
      }

      // Extend the class' collection properties of this type.
      props = GrailsClassUtils.getPropertiesAssignableToType(targetClass, Collection.class)
      
      for (final PropertyDescriptor pd : props) {
        PersistentProperty pp = pe.getPropertyByName ("${pd.name}")
        Class<?> genericClass = pp ? ((pp instanceof Association) ? pp.associatedEntity.javaClass : pp.type) : null
        if (genericClass && GrailsClassUtils.isAssignableOrConvertibleFrom(RefdataValue.class, genericClass)) {

          // The ClassName representation of the property name.
          String upperName = GrailsNameUtils.getClassName(pd.name)
          final String typeString = GrailsDomainRefdataHelpers.getCategoryString (targetClass, pd.name)
        
          targetClass.metaClass.static."get${upperName}Category" = { typeString }
          log.debug ("Added '${pd.name}Category' to ${targetClass}")

          targetClass.metaClass.static."all${upperName}Values" << { Map parameters = [:] ->
            // Default read only
            def param = ["readOnly": true]
            if (parameters) param.putAll(parameters)

            // Do the lookup.
            return genericClass.findAll('FROM ' + genericClass.simpleName + ' as rdv WHERE rdv.owner.desc=:desc', [desc: "${typeString}"], param)
          }
          
          // Add instance method method for adding refdata value from string.
          targetClass.metaClass."addTo${upperName}FromString" << { String value ->

            // Set the refdata value by using the lookupOrCreate method
            def rdv = genericClass.lookupOrCreate("${typeString}", value, value, genericClass)

            delegate."addTo${upperName}" ( rdv )
          }

          log.debug ("Added methods ['all${upperName}Values','addTo${upperName}FromString','lookup${upperName}ByValue'] to ${targetClass}")
        }
      }
    }
  }
}