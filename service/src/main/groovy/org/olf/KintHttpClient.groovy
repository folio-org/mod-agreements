package org.olf

import java.net.http.HttpRequest

interface KintHttpClient {
  KintClientResponse get(HttpRequest request)
  KintClientResponse post(HttpRequest request)
  KintClientResponse put(HttpRequest request)
  KintClientResponse delete(HttpRequest request)

}