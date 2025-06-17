package org.olf.kb

import grails.gorm.multitenancy.Tenants
import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.uri.UriBuilder

import java.time.Duration

class GoKbClient {
  HttpClient httpClient

  GoKbClient() {
    def config = new DefaultHttpClientConfiguration()
    config.setConnectTimeout(Duration.ofSeconds(5))
    config.setReadTimeout(Duration.ofMinutes(15))
    config.setMaxContentLength(2147483647)
    this.httpClient = HttpClient.create(null, config)
  }

  GPathResult getPackageData(final String url) {
    getPackageData(url, null)
  }

  GPathResult getPackageData(final String url, final Map<String, Object> params) {
    def header = "Folio mod-agreements / ${Tenants.currentId()}"

    UriBuilder uriBuilder = UriBuilder.of(url)
    params.each { key, value ->
      uriBuilder.queryParam(key, value)
    }

    HttpRequest<?> micronautRequest = HttpRequest.GET(uriBuilder.build()).header('User-Agent', header)

    HttpResponse<byte[]> response = httpClient.toBlocking().exchange(micronautRequest, byte[])
    byte[] responseBytes = response.body.get()

    return new XmlSlurper().parse(new ByteArrayInputStream(responseBytes))

  }

//  def dataStreamToOutputStream(HttpRequest<?> request,
//  PipedOutputStream outputStream, Runnable finallyRunnable) {
//    httpClient.dataStream(request as HttpRequest<Object>)
//      .doOnNext(byteBuffer -> {
//        try {
//          outputStream.write(byteBuffer.toByteArray());
//        } catch (IOException e) {
//        }
//      })
//      .doFinally(signalType -> {
//        try {
//          outputStream.close();
//        } catch (IOException e) {
//          System.out.println();
//        }
//        finallyRunnable.run();
//      })
//      .subscribe();
//  }
//
//
//  final GPathResult getPackageData(final String url, final Map<String, Object> params) {
//
//    UriBuilder uriBuilder = UriBuilder.of(url)
//    if (params) {
//      params.each { key, value -> uriBuilder.queryParam(key as String, value as String) }
//    }
//
//    def userAgentHeader = "Folio mod-agreements / ${Tenants.currentId()}"
//
//    MutableHttpRequest<Object> requestBuilder = HttpRequest.GET(uriBuilder.build()).header('User-Agent', userAgentHeader)
//
//    HttpRequest<Object> finalRequest = requestBuilder
//
//    // Streaming
//    PipedOutputStream outputStream = new PipedOutputStream();
//    dataStreamToOutputStream(finalRequest, outputStream, () -> println ("finished download"));
//    PipedInputStream inputStream = new PipedInputStream(1024*10);
//    inputStream.connect(outputStream);
//    def parser = new XmlSlurper()
//    return parser.parse(inputStream)
//  }
}
