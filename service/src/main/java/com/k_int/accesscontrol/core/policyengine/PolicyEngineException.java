package com.k_int.accesscontrol.core.policyengine;

/**
 * Custom runtime exception class for errors that occur within the Policy Engine.
 * This exception provides specific error codes and can wrap an underlying cause.
 */
public class PolicyEngineException extends RuntimeException {
  /**
   * Generic error code, used when no more specific code is applicable.
   */
  public static final Long GENERIC_ERROR = 0L;
  /**
   * Error code indicating that an invalid policy restriction was provided.
   */
  public static final Long INVALID_RESTRICTION = 1L;
  /**
   * Error code indicating that an invalid query type was provided for a policy operation.
   */
  public static final Long INVALID_QUERY_TYPE = 2L;
  /**
   * Error code indicating that policy query parameters were invalid or insufficient.
   */
  public static final Long INVALID_QUERY_PARAMETERS = 3L;
  /**
   * Error code indicating that an invalid policy type was encountered.
   */
  public static final Long INVALID_POLICY_TYPE = 4L;


  /**
   * The specific error code associated with this exception.
   */
  final Long code;
  /**
   * The underlying cause of this exception, if any.
   */
  final Throwable cause;

  /**
   * Constructs a new {@code PolicyEngineException} with the specified detail message.
   * The error code defaults to {@link #GENERIC_ERROR} and the cause is {@code null}.
   * @param errorMessage The detail message (which is saved for later retrieval by the {@link Throwable#getMessage()} method).
   */
  public PolicyEngineException(String errorMessage) {
    super(errorMessage);
    this.code = GENERIC_ERROR;
    this.cause = null;
  }

  /**
   * Constructs a new {@code PolicyEngineException} with the specified detail message and a specific error code.
   * The cause is {@code null}.
   * @param errorMessage The detail message.
   * @param code The specific error code.
   */
  public PolicyEngineException(String errorMessage, Long code) {
    super(errorMessage);
    this.code = code;
    this.cause = null;
  }

  /**
   * Constructs a new {@code PolicyEngineException} with the specified detail message and cause.
   * The error code defaults to {@link #GENERIC_ERROR}.
   * @param errorMessage The detail message.
   * @param cause The cause (which is saved for later retrieval by the {@link Throwable#getCause()} method).
   * (A {@code null} value is permitted, and indicates that the cause is nonexistent or unknown.)
   */
  public PolicyEngineException(String errorMessage, Throwable cause) {
    super(errorMessage);
    this.code = GENERIC_ERROR;
    this.cause = cause;
  }

  /**
   * Constructs a new {@code PolicyEngineException} with the specified detail message, error code, and cause.
   * @param errorMessage The detail message.
   * @param code The specific error code.
   * @param cause The cause.
   */
  public PolicyEngineException(String errorMessage, Long code, Throwable cause) {
    super(errorMessage);
    this.code = code;
    this.cause = cause;
  }
}

