package org.olf.general;

public class FolioIngestException extends Exception {
  public static final Long GENERIC_ERROR = 0L;

  final Long code;

  public FolioIngestException(String errorMessage, Long code) {
    super(errorMessage);
    this.code = code;
  }

  public FolioIngestException(String errorMessage) {
    super(errorMessage);
    this.code = GENERIC_ERROR;
  }
}