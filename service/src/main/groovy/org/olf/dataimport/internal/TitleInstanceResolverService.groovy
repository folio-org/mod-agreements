package org.olf.dataimport.internal
import groovy.transform.CompileStatic

import org.olf.dataimport.internal.PackageSchema.ContentItemSchema
import org.olf.kb.TitleInstance

@CompileStatic
public interface TitleInstanceResolverService {
  public String resolve (ContentItemSchema citation, boolean trustedSourceTI)
}
