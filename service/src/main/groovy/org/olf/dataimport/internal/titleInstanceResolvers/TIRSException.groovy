package org.olf.dataimport.internal.titleInstanceResolvers;

// Special exception we can catch and do logic on -- necessary since we may want some TIRS
// behaviour to error but not others, such as IdFirstTIRS treats ALL multiple title matches as a thrown exception
// but WorkSourceIdentifierTIRS which will fall _back_ to IdFirstTIRS would choose to move forward in certain circumstances
// This allows us to catch specific exceptions along with codes to ensure that we don't move forward in case of a syntax error or such
public class TIRSException extends Exception {
  public static final Long GENERIC_ERROR = 0L;
  public static final Long MULTIPLE_TITLE_MATCHES = 1L;
  public static final Long MULTIPLE_IDENTIFIER_MATCHES = 2L;
  public static final Long MISSING_MANDATORY_FIELD = 3L
  public static final Long MULTIPLE_WORK_MATCHES = 4L;
  public static final Long NO_TITLE_MATCH = 5L;
  public static final Long NO_WORK_MATCH = 6L;

  final Long code;

  public TIRSException(String errorMessage, Long code) {
    super(errorMessage);
    this.code = code;
  }

  public TIRSException(String errorMessage) {
    super(errorMessage);
    this.code = GENERIC_ERROR;
  }
}