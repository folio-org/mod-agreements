package org.olf

import io.micronaut.http.client.HttpClient
import org.springframework.stereotype.Component

import java.net.http.HttpRequest
import java.net.http.HttpResponse
import io.micronaut.http.HttpRequest as MicronautHttpRequest
import io.micronaut.http.HttpResponse as MicronautHttpResponse

@Component
class MicronautHttpClientService implements KintHttpClient {

  HttpClient httpClient

  MicronautHttpClientService() {
    this.httpClient = HttpClient.create(null)
  }

  KintClientResponse get(HttpRequest request) {
    MicronautHttpRequest<?> micronautRequest = MicronautHttpRequest.GET(request.uri())

    MicronautHttpResponse<byte[]> micronautResponse = httpClient.toBlocking().exchange(micronautRequest, byte[])

    return new KintClientResponse(
      micronautResponse.status.code,
      micronautResponse.headers.asMap(),
      micronautResponse.body(),
      micronautResponse.getContentType().toString()
    )
  }

  KintClientResponse post(HttpRequest request) {

  }

  KintClientResponse put(HttpRequest request) {

  }

  KintClientResponse delete(HttpRequest request) {

  }

}
