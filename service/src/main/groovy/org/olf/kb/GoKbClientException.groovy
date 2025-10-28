package org.olf.kb

class GoKbClientException extends Exception {

  String message;
  Integer responseStatusCode;

  GoKbClientException(String message) {
    this.message = message;
  }

  GoKbClientException(String message, responseStatusCode) {
    this.message = message;
    this.responseStatusCode;
  }

}
