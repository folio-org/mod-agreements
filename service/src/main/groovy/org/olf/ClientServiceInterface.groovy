package org.olf

import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.HttpClientConfiguration

interface ClientServiceInterface {
  HttpClient createClient(URL url)
  HttpClient createClient(URL url, HttpClientConfiguration config)
//  HttpClient getClient(URL url)
//  void closeClient(HttpClient client)


}