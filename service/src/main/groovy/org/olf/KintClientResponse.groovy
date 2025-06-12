package org.olf


class KintClientResponse {
  int statusCode
  Map<String, List<String>> headers
  byte[] body
  String contentType

  KintClientResponse(int statusCode, Map<String, List<String>> headers, byte[] body, String contentType) {
    this.statusCode = statusCode
    this.headers = headers
    this.body = body
    this.contentType = contentType
  }

  @Override
  public String toString() {
    return "KintClientResponse{" +
      "statusCode=" + statusCode +
      ", headers=" + headers +
      ", body='" + body + '\'' +
      ", contentType='" + contentType + '\'' +
      '}';
  }
}
