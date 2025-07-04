package com.k_int.accesscontrol.core;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PolicyControlled {
  String resourceClass();
  String resourceIdColumn();
}
