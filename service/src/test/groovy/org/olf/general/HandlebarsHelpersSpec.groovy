package org.olf.general

import org.junit.Assert

import com.github.jknack.handlebars.EscapingStrategy
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.helper.StringHelpers

import spock.lang.Specification
import spock.lang.Unroll
import uk.co.cacoethes.handlebars.HandlebarsTemplateEngine

class HandlebarsHelpersSpec extends Specification {
  
  static final EscapingStrategy noEscaping = new EscapingStrategy() {
    public String escape(final CharSequence value) {
      return value.toString()
    }
  };
  
  static final Handlebars hb = new Handlebars().with( noEscaping )
  
  static final HandlebarsTemplateEngine hbte = new HandlebarsTemplateEngine(handlebars: hb)
  
  def setupSpec() {
    hb.registerHelpers(StringTemplateHelpers.class)
    hb.registerHelpers(StringHelpers.class)
  }
  
  @Unroll
  def 'Individual Helper checks' ( String template, String subject, String expectation ) {
    
    given: 'Template: #template'
    
    and: 'Subject: #subject'
    
    expect: 'expectation: #expectation'
    
      final String replaced = hb.compileInline(template)
        .apply(subject)
      
      Assert.assertEquals(expectation, replaced)
      
    where:
      template << [
        '{{ insertAfter this ".com" "/proxy1" }}',
        '{{ insertAfter this ".com" "/proxy1" }}',
        '{{ insertAfter this ".com" "/proxy1" }}',
        '{{ insertAfterAll this ".com" "/proxy1" }}',
        '{{ insertAfterAll this ".com" "/proxy1" }}',
        '{{ insertAfterAll this ".com" "/proxy1" }}',
        '{{ insertBefore this ".com" "/proxy1" }}',
        '{{ insertBefore this ".com" "/proxy1" }}',
        '{{ insertBefore this ".com" "/proxy1" }}',
        '{{ insertBeforeAll this ".com" "/proxy1" }}',
        '{{ insertBeforeAll this ".com" "/proxy1" }}',
        '{{ insertBeforeAll this ".com" "/proxy1" }}',
        '{{ removeProtocol this }}',
        '{{ urlEncode this }}'
      ]
      
      subject                                   | expectation
      'http://link.current.url.com'             | 'http://link.current.url.com/proxy1'
      'http://link.current.comp.url.com'        | 'http://link.current.com/proxy1p.url.com'
      'http://link.current.url.co.uk'           | 'http://link.current.url.co.uk'
      'http://link.current.url.com'             | 'http://link.current.url.com/proxy1'
      'http://link.current.comp.url.com'        | 'http://link.current.com/proxy1p.url.com/proxy1'
      'http://link.current.url.co.uk'           | 'http://link.current.url.co.uk'
      'http://link.current.url.com'             | 'http://link.current.url/proxy1.com'
      'http://link.current.comp.url.com'        | 'http://link.current/proxy1.comp.url.com'
      'http://link.current.url.co.uk'           | 'http://link.current.url.co.uk'
      'http://link.current.url.com'             | 'http://link.current.url/proxy1.com'
      'http://link.current.comp.url.com'        | 'http://link.current/proxy1.comp.url/proxy1.com'
      'http://link.current.url.co.uk'           | 'http://link.current.url.co.uk'
      'http://link.current.comp.url.com'        | 'link.current.comp.url.com'
      'Many Specials ?><*&^%^{}@:~"£$%^&'       | 'Many+Specials+%3F%3E%3C*%26%5E%25%5E%7B%7D%40%3A%7E%22%C2%A3%24%25%5E%26'
      
  }
  
  @Unroll
  def 'testScenarios' (final String subject, final String platform, final String template, final String expectation ) {
    
    given: 'Template: #template' 
      
    and: 'Subject: #subject'
    
    and: 'Bindings: #bindings'
    
      final Map<String, String> allBindings = [ inputUrl: subject, platformLocalCode: platform]
      
    expect: 'expectation: #expectation'
    
      final Writable compiletdTemplate = hbte.createTemplate(template).make(allBindings)
      
      final StringWriter sw = new StringWriter()
      compiletdTemplate.writeTo(sw)
      final String replaced = sw.toString()
      
      Assert.assertEquals(expectation, replaced)
      
    
    where:
    subject                                     | platform       | template                                                                             || expectation
    'http://example.org'                        | _              | 'http://proxy.url/?url={{ urlEncode inputUrl }}'                                     || 'http://proxy.url/?url=http%3A%2F%2Fexample.org'
    'http://example.org'                        | _              | '{{ inputUrl }}&authentication=ip'                                                   || 'http://example.org&authentication=ip'
    'http://example.org/index/'                 | 'ABCD'         | '{{ insertBefore inputUrl "/index" (join "/" platformLocalCode "") }}'               || 'http://example.org/ABCD/index/'
    'http://example.org'                        | _              | 'https://proxy.url/?url={{ removeProtocol inputUrl }}'                               || 'https://proxy.url/?url=example.org'
    'http://example.org'                        | _              | '{{ replace inputUrl "org" "com" }}'                                                 || 'http://example.com'
    'http://example.org/ebook/'                 | _              | 'https://proxy.url/?url={{ replace ( removeProtocol inputUrl ) \"/ebook/\" \"\" }}'  || 'https://proxy.url/?url=example.org'
    'http://example.org'                        | _              | 'http://ezproxy.abclib.org:2048/login?url={{ inputUrl }}'                            || 'http://ezproxy.abclib.org:2048/login?url=http://example.org'
    'http://example.org/index/'                 | _              | 'http://{{ insertBefore ( removeProtocol inputUrl ) \"/\" \".web.library.edu\" }}'   || 'http://example.org.web.library.edu/index/'
    'http://example.org'                        | _              | 'https://go.openathens.net/redirector/athensdemo.net?url={{ urlEncode inputUrl }}'   || 'https://go.openathens.net/redirector/athensdemo.net?url=http%3A%2F%2Fexample.org'
    'https://infotrac.gale.com/itweb/?db=AONE'  | 'duke_perkins' | '{{ insertBefore inputUrl "?" platformLocalCode }}'                                  || 'https://infotrac.gale.com/itweb/duke_perkins?db=AONE'
  }
  
}
