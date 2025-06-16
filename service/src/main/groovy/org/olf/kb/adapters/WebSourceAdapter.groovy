package org.olf.kb.adapters

import grails.gorm.multitenancy.Tenants
import groovy.json.JsonOutput
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

  protected KintHttpClient getHttpClient() {
    return httpClient;
  }


  protected final String stripTrailingSlash(final String uri) {
    uri.endsWith('//') ? uri.substring(0, uri.length() - 1) : uri
  }

  private String buildUrlWithParams(String url, Map params) {
    if (!params) return url

    def finalUrl = new StringBuilder(url)
    String queryString = params.collect { key, value ->
      String encodedKey = URLEncoder.encode(key as String, StandardCharsets.UTF_8)
      String encodedValue = URLEncoder.encode(value.toString(), StandardCharsets.UTF_8)
      return "${encodedKey}=${encodedValue}"
    }.join('&')

    if (queryString) {
      finalUrl.append('?').append(queryString)
    }
    return finalUrl.toString()
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

  protected final def getSync(final String url, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
    getSync(url, null, expand)
  }

  class RequestCustomizer {
    Map<String, String> headers = [:]

    void header(String name, Object value) {
      headers[name] = value.toString()
    }
  }

  protected final def getSync(final String url, final Map params, @DelegatesTo(RequestCustomizer.class) final Closure requestConfigurer = null, // Optional request customizer
                              final Closure responseHandler = null ) {
    def header = "Folio mod-agreements / ${Tenants.currentId()}"
    def customizer = new RequestCustomizer()
    customizer.header('User-Agent', header)

    // Rather than using the expand closure, which was tied to http-builder-ng,
    // I made separate closures for a) configuring the request (if you wanted the calling method to do that)
    // or b) taking some action with the response.
    if (requestConfigurer) {
      requestConfigurer.setDelegate(customizer)
      requestConfigurer.setResolveStrategy(Closure.DELEGATE_FIRST)
      requestConfigurer.call()
    }

    def finalUrl = buildUrlWithParams(url, params)

    HttpRequest req = HttpRequest.newBuilder()
      .uri(URI.create(finalUrl.toString()))
      .header('User-Agent', header)
      .GET()
      .build()

    KintClientResponse response = httpClient.get(req)

    if (responseHandler) {
      responseHandler.call(response) // Because we pass a KintClientResponse to the calling adapter (e.g. GOKbOAIAdapter),
      // the adapter is no longer dependent on the "http-builder-ng" response object.
    }

    return new XmlSlurper().parse(new ByteArrayInputStream(response.body))
  }

  private KintClientResponse executeRequestWithBody(String url, def jsonData, Map params, @DelegatesTo(RequestCustomizer.class) final Closure requestConfigurer = null, // Optional request customizer
                                                    final Closure responseHandler = null, Closure<KintClientResponse> clientAction) {
    def customizer = new RequestCustomizer()
    customizer.header('Content-Type', 'application/json; charset=utf-8')

    String finalUrl = buildUrlWithParams(url, params)
    byte[] bodyBytes = JsonOutput.toJson(jsonData).getBytes(StandardCharsets.UTF_8)

    if (requestConfigurer) {
      requestConfigurer.setDelegate(customizer)
      requestConfigurer.setResolveStrategy(Closure.DELEGATE_FIRST)
      requestConfigurer.call()
    }

    KintClientResponse response = clientAction.call(finalUrl, bodyBytes, customizer.headers)

    if (responseHandler) {
      responseHandler.call(response)
    }

    return response
  }

  protected final def post(final String url, final def jsonData, @DelegatesTo(RequestCustomizer.class) final Closure requestConfigurer = null, // Optional request customizer
                           final Closure responseHandler = null) {
    post(url, jsonData, null, requestConfigurer, responseHandler)
  }

  protected final def post(final String url, final def jsonData, final Map params,  @DelegatesTo(RequestCustomizer.class) final Closure requestConfigurer = null, // Optional request customizer
                           final Closure responseHandler = null) {
    return executeRequestWithBody(url, jsonData, params, requestConfigurer, responseHandler, httpClient.&post)
  }

  protected final def put(final String url, final def jsonData,  @DelegatesTo(RequestCustomizer.class) final Closure requestConfigurer = null, // Optional request customizer
                          final Closure responseHandler = null) {
    put(url, jsonData, null, requestConfigurer, responseHandler)
  }
  protected final def put(final String url, final def jsonData, final Map params, @DelegatesTo(RequestCustomizer.class) final Closure requestConfigurer = null, // Optional request customizer
                          final Closure responseHandler = null) {
    return executeRequestWithBody(url, jsonData, params, requestConfigurer, responseHandler, httpClient.&put)
  }

  protected final def patch(final String url, final def jsonData,   @DelegatesTo(RequestCustomizer.class) final Closure requestConfigurer = null, // Optional request customizer
                            final Closure responseHandler = null) {
    patch(url, jsonData, null, requestConfigurer, responseHandler)
  }
  protected final def patch(final String url, final def jsonData, final Map params,   @DelegatesTo(RequestCustomizer.class) final Closure requestConfigurer = null, // Optional request customizer
                            final Closure responseHandler = null) {
    return executeRequestWithBody(url, jsonData, params, requestConfigurer, responseHandler, httpClient.&patch)
  }

  protected final def delete(final String url, @DelegatesTo(RequestCustomizer.class) final Closure requestConfigurer = null, // Optional request customizer
                             final Closure responseHandler = null) {
    delete(url, null, requestConfigurer, responseHandler)
  }
  protected final def delete(final String url, final Map params, @DelegatesTo(RequestCustomizer.class) final Closure requestConfigurer = null, // Optional request customizer
                             final Closure responseHandler = null) {
    def customizer = new RequestCustomizer()
    customizer.header('User-Agent', "Folio mod-agreements / ${Tenants.currentId()}")

    if (requestConfigurer) {
      requestConfigurer.setDelegate(customizer)
      requestConfigurer.setResolveStrategy(Closure.DELEGATE_FIRST)
      requestConfigurer.call()
    }

    String finalUrl = buildUrlWithParams(url, params)

    KintClientResponse response = httpClient.delete(finalUrl, customizer.headers)

    if (responseHandler) {
      responseHandler.call(response)
    }

    return response
  }
}

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
