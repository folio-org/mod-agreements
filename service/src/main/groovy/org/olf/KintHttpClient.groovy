package org.olf

import java.net.http.HttpRequest

interface KintHttpClient {
  KintClientResponse get(HttpRequest request)
  KintClientResponse post(String url, byte[] body, Map<String, String> headers)
  KintClientResponse put(String url, byte[] body, Map<String, String> headers)
  KintClientResponse patch(String url, byte[] body, Map<String, String> headers)
  KintClientResponse delete(String url, Map<String, String> headers)
}