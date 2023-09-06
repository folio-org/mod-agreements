package org.olf.dataimport.internal.titleInstanceResolvers;

// Special exception we can catch and do logic on -- necessary since we may want some TIRS
// behaviour to error but not others, such as IdFirstTIRS treats ALL multiple title matches as a thrown exception
// but WorkSourceIdentifierTIRS which will fall _back_ to IdFirstTIRS would choose to move forward in certain circumstances
// This allows us to catch specific exceptions along with codes to ensure that we don't move forward in case of a syntax error or such
public class TIRSException extends Exception {
  public static final Long MULTIPLE_TITLE_MATCHES = 1L;
  public static final Long MULTIPLE_IDENTIFIER_MATCHES = 2L;
  public static final Long MISSING_MANDATORY_FIELD = 3L

  final Long code;

  public TIRSException(String errorMessage, Long code) {
    super(errorMessage);
    this.code = code;
  }
}