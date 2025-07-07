package com.k_int.accesscontrol.core;

public class PolicyEngineException extends RuntimeException {
  public static final Long GENERIC_ERROR = 0L;
  public static final Long INVALID_RESTRICTION = 1L;
  public static final Long INVALID_QUERY_TYPE = 2L;

  final Long code;
  final Throwable cause;


  public PolicyEngineException(String errorMessage) {
    super(errorMessage);
    this.code = GENERIC_ERROR;
    this.cause = null;
  }

  public PolicyEngineException(String errorMessage, Long code) {
    super(errorMessage);
    this.code = code;
    this.cause = null;
  }

  public PolicyEngineException(String errorMessage, Throwable cause) {
    super(errorMessage);
    this.code = GENERIC_ERROR;
    this.cause = cause;
  }

  public PolicyEngineException(String errorMessage, Long code, Throwable cause) {
    super(errorMessage);
    this.code = code;
    this.cause = cause;
  }
}

