package org.olf.general

import java.util.HashMap

import groovy.transform.CompileStatic

@CompileStatic
public class StringTemplateBindings extends HashMap<String, Object> {
  
  // Name of the bindings. Used internally only,
  // so not a map based property as it should not be accessible inside the
  // template. 
  String name
  
  public String getInputUrl() {
    get('inputUrl')
  }
  
  public void setInputUrl(String inputUrl) {
    put('inputUrl', inputUrl)
  }
  
  public String getPlatformLocalCode() {
    get('platformLocalCode')
  }
  
  public void setPlatformLocalCode(String platformLocalCode) {
    put('platformLocalCode', platformLocalCode)
  }
}
