package org.olf.general.refdata

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@interface CategoryId {
  String value()
}