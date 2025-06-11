package org.olf

import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.HttpClientConfiguration
import io.micronaut.reactor.http.client.ReactorStreamingHttpClient

interface ClientServiceInterface {
  HttpClient createClient(URL url)
  HttpClient createClient(URL url, HttpClientConfiguration config)

  ReactorStreamingHttpClient createClientReactive(URL url)
  ReactorStreamingHttpClient createClientReactive(URL url, HttpClientConfiguration config)

//  HttpClient getClient(URL url)
//  void closeClient(HttpClient client)


}