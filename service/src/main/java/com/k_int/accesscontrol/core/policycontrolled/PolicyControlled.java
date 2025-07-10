package com.k_int.accesscontrol.core.policycontrolled;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PolicyControlled {
  String resourceIdColumn() default "";
  String resourceIdField() default "";

  // Allow us to roam up an ownership tree
  String ownerColumn() default "";
  String ownerField() default "";
  Class<?> ownerClass() default Object.class;
}
