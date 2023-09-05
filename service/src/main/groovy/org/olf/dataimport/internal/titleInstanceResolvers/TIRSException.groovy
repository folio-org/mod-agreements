package org.olf.dataimport.internal.titleInstanceResolvers;

// Special exception we can catch and do logic on
public class TIRSException extends Exception {
  public TIRSException(String errorMessage) {
    super(errorMessage);
  }
}