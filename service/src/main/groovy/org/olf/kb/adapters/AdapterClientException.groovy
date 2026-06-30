package org.olf.kb.adapters

class AdapterClientException extends Exception {
  String message;
  Integer responseStatusCode;

  AdapterClientException(String message) {
    this.message = message;
  }

  AdapterClientException(String message, responseStatusCode) {
    this.message = message;
    this.responseStatusCode;
  }
}