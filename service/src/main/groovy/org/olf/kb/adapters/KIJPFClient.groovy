package org.olf.kb.adapters

import grails.gorm.multitenancy.Tenants
import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.uri.UriBuilder

import java.time.Duration

class KIJPFClient implements AdapterClient {

  HttpClient httpClient

  KIJPFClient() {
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

    byte[] responseBytes
    try {
      HttpRequest<?> micronautRequest = HttpRequest.GET(uriBuilder.build()).header('User-Agent', header)

      HttpResponse<byte[]> response = httpClient.toBlocking().exchange(micronautRequest, byte[])
      responseBytes = response.body.get()
    } catch (HttpClientResponseException exception) {
      throw AdapterClientException(exception.message, exception.status.code)
    } catch (Exception exception) {
      throw AdapterClientException(exception.message)
    }

    return new XmlSlurper().parse(new ByteArrayInputStream(responseBytes))

  }

  @Override
  Object getData(String url, Map<String, Object> params) {

    return getPackageData(url, params)
  }
}