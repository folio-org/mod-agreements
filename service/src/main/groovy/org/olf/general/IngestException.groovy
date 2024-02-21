package org.olf.general;

public class IngestException extends Exception {
  public static final Long GENERIC_ERROR = 0L;

  final Long code;

  public IngestException(String errorMessage, Long code) {
    super(errorMessage);
    this.code = code;
  }

  public IngestException(String errorMessage) {
    super(errorMessage);
    this.code = GENERIC_ERROR;
  }
}