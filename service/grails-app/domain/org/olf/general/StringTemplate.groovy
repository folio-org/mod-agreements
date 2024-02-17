package org.olf.general

import com.github.jknack.handlebars.EscapingStrategy
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.helper.StringHelpers

import com.k_int.web.toolkit.databinding.BindImmutably
import com.k_int.web.toolkit.refdata.CategoryId
import com.k_int.web.toolkit.refdata.Defaults
import com.k_int.web.toolkit.refdata.RefdataValue

import grails.gorm.MultiTenant
import groovy.text.Template
import uk.co.cacoethes.handlebars.HandlebarsTemplateEngine

class StringTemplate implements MultiTenant<StringTemplate> {
  private static final HandlebarsTemplateEngine hte = new HandlebarsTemplateEngine(handlebars: new Handlebars().with(new EscapingStrategy() {
    public String escape(final CharSequence value) {
      return value.toString() // No escaping. Return as is.
    }
  })
  .registerHelpers(StringHelpers)
  .registerHelpers(StringTemplateHelpers))

  String id
  String name
  String rule

  Date dateCreated
  Date lastUpdated

  @CategoryId(defaultInternal=true)
  @Defaults(['urlProxier', 'urlCustomiser'])
  RefdataValue context
  
  static transients = ['template']
  
  private Template template
  private Template getTemplate() {
    if (template == null) {
      template = hte.createTemplate(rule)
    }
    
    template
  }

  /*
   * The useage of this list will depend somewhat on context,
   * but the general idea is that this captures the 'scope' of a translation.
   * For certain contexts this list will act as a 'allowlist', for others as a 'denylist'
   */
  @BindImmutably
  Set<String> idScopes = []
  static hasMany = [idScopes: String]


  static mapping = {
    id column:'strt_id', generator: 'uuid2', length:36
    name column:'strt_name'
    rule column:'strt_rule'
    context column:'strt_context_fk'
    dateCreated column: 'strt_date_created'
    lastUpdated column: 'strt_last_updated'
    idScopes cascade: 'all-delete-orphan', joinTable: [name: 'string_template_scopes', key: 'string_template_id', column: 'id_scope']
  }

  static constraints = {
    rule (validator: { String rule, StringTemplate obj ->
      return obj.checkValidTemplate()
    })
  }

  public String customiseString(Map<String, ?> binding) {
    
    final String outputString = getTemplate().make(binding).with { 
      StringWriter sw = new StringWriter()
      writeTo(sw)
      sw.toString()
    }

    return outputString
  }

  public List<String> checkValidTemplate() {
    String output
    try {
      output = this.customiseString([
        inputUrl: "testing",
        platformLocalCode: "12345"
      ])
    } catch (Exception e) {
      return ["invalidTemplate", e.message]
    }
    if (output && output?.length() > 0) {
      return null
    } else {
      return [
        "invalidTemplate",
        "Output is null or empty"
      ]
    }
  }
  
  @Override
  public boolean equals(Object obj) {
    if (id && obj?.class && StringTemplate.class.isAssignableFrom(obj.class)) {
      return id.equals(obj.id)
    }
    return false
  }
}
