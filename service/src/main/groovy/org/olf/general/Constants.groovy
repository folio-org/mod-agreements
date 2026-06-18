package org.olf.general

public interface Constants {
  static interface Queries {
    public static final String DEFAULT_ROOT_ALIAS = 'this'
  }

  static interface UUIDs {
    UUID NIL = new UUID( 0 , 0 );
  }

  static interface Time {
    public static final long ZERO = 0L
    public static final long ONE_HOUR_MS = 60L * 60L * 1000L
    public static final long ONE_DAY_MS = 24L * 60L * 60L * 1000L
  }
}