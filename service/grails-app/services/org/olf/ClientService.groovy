package org.olf

import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.HttpClientConfiguration
import io.micronaut.reactor.http.client.ReactorStreamingHttpClient
import jakarta.inject.Singleton

@Singleton
class ClientService implements ClientServiceInterface {
  HttpClient createClient(URL url) {
    return HttpClient.create(url)
  }

  ReactorStreamingHttpClient createClientReactive(URL url) {
    return ReactorStreamingHttpClient.create(url)
  }

  ReactorStreamingHttpClient createClientReactive(URL url, HttpClientConfiguration config) {
    return ReactorStreamingHttpClient.create(url, config)
  }

  HttpClient createClient(URL url, HttpClientConfiguration config) {
    return HttpClient.create(url, config)
  }
}
