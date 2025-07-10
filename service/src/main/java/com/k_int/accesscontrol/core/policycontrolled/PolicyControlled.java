package com.k_int.accesscontrol.core.policycontrolled;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PolicyControlled {
  String resourceIdReference() default ""; // Could be column (For sql usage) or field (hibernate stuff?)

  // Allow us to roam up an ownership tree
  String ownerReference() default "";  // Could be column (For sql usage) or field (hibernate stuff?)
  Class<?> ownerClass() default Object.class;
}
