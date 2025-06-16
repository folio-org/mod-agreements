package org.olf

import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClient
import org.springframework.stereotype.Component

import java.net.http.HttpRequest
import java.net.http.HttpResponse
import io.micronaut.http.HttpRequest as MicronautHttpRequest
import io.micronaut.http.HttpResponse as MicronautHttpResponse

import java.time.Duration

@Component
class MicronautHttpClientService implements KintHttpClientService {

  HttpClient httpClient

  MicronautHttpClientService() {
    def config = new DefaultHttpClientConfiguration()
    config.setConnectTimeout(Duration.ofSeconds(5))
    config.setReadTimeout(Duration.ofMinutes(15))
    config.setMaxContentLength(2147483647)
    this.httpClient = HttpClient.create(null, config)
  }

  KintClientResponse sendMicronautRequest(MicronautHttpRequest request) {

    MicronautHttpResponse<byte[]> micronautResponse = httpClient.toBlocking().exchange(request, byte[])

    return new KintClientResponse(
      micronautResponse.status.code,
      micronautResponse.headers.asMap(),
      micronautResponse.body(),
      micronautResponse.getContentType()?.toString()
    )
  }

  KintClientResponse get(HttpRequest request) {
    MicronautHttpRequest<?> micronautRequest = MicronautHttpRequest.GET(request.uri())

    return sendMicronautRequest(micronautRequest)
  }

  KintClientResponse post(String url, byte[] body, Map<String, String> headers) {
    MutableHttpRequest<?> micronautRequest = MicronautHttpRequest.POST(url, body)
    headers.each { key, value ->
      micronautRequest.header(key, value)
    }

    return sendMicronautRequest(micronautRequest)
  }

  KintClientResponse put(String url, byte[] body, Map<String, String> headers) {
    MutableHttpRequest<?> micronautRequest = MicronautHttpRequest.PUT(url, body)
    headers.each { key, value -> micronautRequest.header(key, value) }
    return sendMicronautRequest(micronautRequest)
  }

  KintClientResponse patch(String url, byte[] body, Map<String, String> headers) {
    MutableHttpRequest<?> micronautRequest = MicronautHttpRequest.PATCH(url, body)
    headers.each { key, value -> micronautRequest.header(key, value) }
    return sendMicronautRequest(micronautRequest)
  }

  KintClientResponse delete(String url, Map<String, String> headers) {
    MutableHttpRequest<?> micronautRequest = MicronautHttpRequest.DELETE(url)
    headers.each { key, value -> micronautRequest.header(key, value) }
    return sendMicronautRequest(micronautRequest)
  }


}
