package org.olf

import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.HttpClientConfiguration
import jakarta.inject.Singleton

@Singleton
class ClientService implements ClientServiceInterface {
  HttpClient createClient(URL url) {
    return HttpClient.create(url)
  }

  HttpClient createClient(URL url, HttpClientConfiguration config) {
    return HttpClient.create(url, config)
  }
}
