package com.k_int.folio;

public class InternalFolioClientException extends Exception {
  public static final Long GENERIC_ERROR = 0L;
  public static final Long FAILED_REQUEST = 1L;
  public static final Long REQUEST_NOT_OK = 2L;
  public static final Long RESPONSE_WRONG_SHAPE = 3L;

  final Long code;
  final Throwable cause;


  public InternalFolioClientException(String errorMessage) {
    super(errorMessage);
    this.code = GENERIC_ERROR;
    this.cause = null;
  }

  public InternalFolioClientException(String errorMessage, Long code) {
    super(errorMessage);
    this.code = code;
    this.cause = null;
  }

  public InternalFolioClientException(String errorMessage, Throwable cause) {
    super(errorMessage);
    this.code = GENERIC_ERROR;
    this.cause = cause;
  }

  public InternalFolioClientException(String errorMessage, Long code, Throwable cause) {
    super(errorMessage);
    this.code = code;
    this.cause = cause;
  }


}
