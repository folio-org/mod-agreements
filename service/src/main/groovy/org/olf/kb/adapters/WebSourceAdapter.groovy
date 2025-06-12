package org.olf.kb.adapters

import grails.gorm.multitenancy.Tenants
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import groovyx.net.http.HttpBuilder
import groovyx.net.http.HttpConfig
import groovyx.net.http.HttpObjectConfig
import io.micronaut.http.uri.UriBuilder
import jakarta.inject.Inject
import org.olf.KintClientResponse
import org.olf.KintHttpClient
import org.olf.MicronautHttpClientService
import org.springframework.stereotype.Component

import java.net.HttpURLConnection
import java.net.http.HttpRequest
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

import groovy.xml.XmlSlurper
import groovyx.net.http.ChainedHttpConfig
import groovyx.net.http.FromServer

@Slf4j
@CompileStatic
@Component
public abstract class WebSourceAdapter {

  KintHttpClient httpClient

  WebSourceAdapter() {
    this.httpClient = new MicronautHttpClientService()
  }

  protected KintHttpClient instanceClient = null
  protected KintHttpClient getHttpClient() {
    return httpClient;
  }

  
  protected final String stripTrailingSlash (final String uri) {
    uri.endsWith('//') ? uri.substring(0, uri.length() - 1) : uri
  }
  
//  protected final def getAsync (final String url, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
//    getAsync( url, null, expand)
//  }
//  protected final CompletableFuture getAsync (final String url, final Map params, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
//    httpClient.getAsync({
//      request.uri = url
//      request.uri.query = params
//
//      if (expand) {
//        expand.rehydrate(delegate, expand.owner, thisObject)()
//      }
//    })
//  }
  
  protected final def getSync (final String url, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
    getSync( url, null, expand)
  }

  class RequestCustomizer {
    Map<String, String> headers = [:]

    // This method now ensures the value is a standard String
    void header(String name, Object value) {
      headers[name] = value.toString() // .toString() on a GString produces a java.lang.String
    }
  }

  protected final def getSync (final String url, final Map params, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
    def header = "Folio mod-agreements / ${Tenants.currentId()}"
    def finalUrl = new StringBuilder(url)


    if (params) {
      String queryString = params.collect { key, value ->
        String encodedKey = URLEncoder.encode(key as String, StandardCharsets.UTF_8)
        String encodedValue = URLEncoder.encode(value.toString(), StandardCharsets.UTF_8)
        return "${encodedKey}=${encodedValue}"
      }.join('&')

      if (queryString) {
        finalUrl.append('?').append(queryString)
      }
    }

    HttpRequest req = HttpRequest.newBuilder()
      .uri(URI.create(finalUrl.toString()))
      .header('User-Agent', header)
      .GET()
      .build()

    KintClientResponse response = httpClient.get(req)
    def customizer = new RequestCustomizer()
    customizer.header('User-Agent', header)

    if (expand) {
      expand.setDelegate(customizer)
      expand.setResolveStrategy(Closure.DELEGATE_FIRST)
      expand.call(response)
    }
    def contentType = response.contentType
    log.info("Content type: ${contentType}")
      log.info("Parsing xml: ${response.body}")
      return new XmlSlurper().parse(new ByteArrayInputStream(response.body))
  }
  }
//
//  protected final def post (final String url, final def jsonData, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
//    post(url, jsonData, null, expand)
//  }
//  protected final def post (final String url, final def jsonData, final Map params, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
//    httpClient.post({
//      request.uri = url
//      request.uri.query = params
//      request.body = jsonData
//
//      if (expand) {
//        expand.rehydrate(delegate, expand.owner, thisObject)()
//      }
//    })
//  }
//
//  protected final def put (final String url, final def jsonData, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
//    put(url, jsonData, null, expand)
//  }
//  protected def put (final String url, final def jsonData, final Map params, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
//
//    httpClient.put({
//      request.uri = url
//      request.uri.query = params
//      request.body = jsonData
//
//      if (expand) {
//        expand.rehydrate(delegate, expand.owner, thisObject)()
//      }
//    })
//  }
//
//  protected final def patch (final String url, final def jsonData, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
//    patch(url, jsonData, null, expand)
//  }
//  protected final def patch (final String url, final def jsonData, final Map params, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
//
//    httpClient.patch({
//      request.uri = url
//      request.uri.query = params
//      request.body = jsonData
//
//      if (expand) {
//        expand.rehydrate(delegate, expand.owner, thisObject)()
//      }
//    })
//  }
//
//  protected final def delete (final String url, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
//    delete(url, null, expand)
//  }
//  protected final def delete (final String url, final Map params, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
//
//    httpClient.delete({
//      request.uri = url
//      request.uri.query = params
//
//      if (expand) {
//        expand.rehydrate(delegate, expand.owner, thisObject)()
//      }
//    })
//  }
//}
