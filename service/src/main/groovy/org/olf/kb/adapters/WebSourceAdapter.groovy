package org.olf.kb.adapters

import grails.gorm.multitenancy.Tenants
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import groovyx.net.http.HttpBuilder
import groovyx.net.http.HttpConfig
import groovyx.net.http.HttpObjectConfig
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.uri.UriBuilder
import org.olf.ClientService
import org.reactivestreams.Publisher

import java.net.HttpURLConnection
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

import groovy.xml.XmlSlurper
import groovyx.net.http.ChainedHttpConfig
import groovyx.net.http.FromServer

import java.util.function.Consumer

@CompileStatic
@Slf4j
public abstract class WebSourceAdapter {

  ClientService clientService
  private static HttpClient GLOBAL_CLIENT
  
  protected HttpClient instanceClient = null
  protected HttpClient getHttpClient() {
    if (!instanceClient) {
      if (!GLOBAL_CLIENT) {
        def config = new DefaultHttpClientConfiguration()
        config.setConnectTimeout(Duration.ofSeconds(5))
        config.setReadTimeout(Duration.ofMinutes(15))

        GLOBAL_CLIENT = clientService.createClient(null, config)

      }
      instanceClient = GLOBAL_CLIENT
    }
    instanceClient
  }
  
  
  WebSourceAdapter() {
    this(null)
  }
  
  WebSourceAdapter(HttpClient httpClient) {
    instanceClient = httpClient
  }
  
  protected final String stripTrailingSlash (final String uri) {
    uri.endsWith('//') ? uri.substring(0, uri.length() - 1) : uri
  }
  
  protected final def getAsync (final String url, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
    getAsync( url, null, expand)
  }

  protected final CompletableFuture getAsync (final String url, final Map params, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
    UriBuilder uriBuilder = UriBuilder.of(url)
    params.each { key, value -> uriBuilder.queryParam(key as String, value as String) }

    HttpRequest<?> initialRequest = HttpRequest.GET(uriBuilder.build())
    HttpRequest<Object> finalRequest = initialRequest

    if (expand) {
      MutableHttpRequest<Object> mutableReq = initialRequest.mutate()

      expand.setDelegate(mutableReq)
      expand.setResolveStrategy(Closure.DELEGATE_FIRST)
      expand.call()

      finalRequest = mutableReq
    }

    // Micronaut httpClient returns a Publisher instead of CompleatableFuture
    Publisher<HttpResponse<String>> responsePublisher = httpClient.exchange(finalRequest, String.class)
    return responsePublisher as CompletableFuture
  }
  
  protected final def getSync (final String url, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
    getSync( url, null, expand)
  }

  protected final def getSync (final String url, final Map params, @DelegatesTo(HttpConfig.class) final Closure expand = null) {

    UriBuilder uriBuilder = UriBuilder.of(url)
    if (params) {
      params.each { key, value -> uriBuilder.queryParam(key as String, value as String) }
    }

    def header = "Folio mod-agreements / ${Tenants.currentId()}"
    def initialRequest = HttpRequest.GET(uriBuilder.build())
      .header('User-Agent', header)

    HttpRequest<Object> finalRequest = initialRequest

    if (expand) {
      MutableHttpRequest<Object> mutableReq = initialRequest.toMutableRequest()

      expand.setDelegate(mutableReq)
      expand.setResolveStrategy(Closure.DELEGATE_FIRST)
      expand.call()

      finalRequest = mutableReq
    }

    try {
      HttpResponse<byte[]> response = httpClient.toBlocking().exchange(finalRequest, byte[])

      def contentType = response.getContentType().orElse(null)
      if (contentType?.name?.contains('xml')) {
        return new XmlSlurper().parse(new ByteArrayInputStream(response.body()))
      }
      return response.getBody(String.class).orElse('')

    } catch (HttpClientResponseException e) {
      log.error("HTTP Error: ${e.status} for URL: ${url}", e)
      throw e
    }
  }
  
  protected final def post (final String url, final def jsonData, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
    post(url, jsonData, null, expand)
  }
  protected final def post (final String url, final def jsonData, final Map params, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
    UriBuilder uriBuilder = UriBuilder.of(url)
    if (params) {
      params.each { key, value -> uriBuilder.queryParam(key as String, value as String) }
    }

    HttpRequest<?> initialRequest = HttpRequest.POST(uriBuilder.build(), jsonData).contentType(MediaType.APPLICATION_JSON_TYPE)

    HttpRequest<Object> finalRequest = initialRequest

    if (expand) {
      MutableHttpRequest<Object> mutableReq = initialRequest.mutate()

      expand.setDelegate(mutableReq)
      expand.setResolveStrategy(Closure.DELEGATE_FIRST)
      expand.call()

      finalRequest = mutableReq
    }

    return httpClient.toBlocking().exchange(finalRequest)
  }
  
  protected final def put (final String url, final def jsonData, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
    put(url, jsonData, null, expand)
  }
  protected def put (final String url, final def jsonData, final Map params, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
    UriBuilder uriBuilder = UriBuilder.of(url)
    if (params) {
      params.each { key, value -> uriBuilder.queryParam(key as String, value as String) }
    }

    HttpRequest<?> initialRequest = HttpRequest.PUT(uriBuilder.build(), jsonData).contentType(MediaType.APPLICATION_JSON_TYPE)

    HttpRequest<Object> finalRequest = initialRequest

    if (expand) {
      MutableHttpRequest<Object> mutableReq = initialRequest.mutate()

      expand.setDelegate(mutableReq)
      expand.setResolveStrategy(Closure.DELEGATE_FIRST)
      expand.call()

      finalRequest = mutableReq
    }

    return httpClient.toBlocking().exchange(finalRequest)
  }
  
  protected final def patch (final String url, final def jsonData, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
    patch(url, jsonData, null, expand)
  }
  protected final def patch (final String url, final def jsonData, final Map params, @DelegatesTo(HttpConfig.class) final Closure expand = null) {

    UriBuilder uriBuilder = UriBuilder.of(url)
    if (params) {
      params.each { key, value -> (UriBuilder) uriBuilder.queryParam(key as String, value as String) }
    }

    HttpRequest<Object> initialRequest = HttpRequest.PATCH(uriBuilder.build(), jsonData)
      .contentType(MediaType.APPLICATION_JSON_TYPE)

    HttpRequest<Object> finalRequest = initialRequest

    if (expand) {
      MutableHttpRequest<Object> mutableReq = initialRequest.mutate()

      expand.setDelegate(mutableReq)
      expand.setResolveStrategy(Closure.DELEGATE_FIRST)
      expand.call()

      finalRequest = mutableReq
    }

    return httpClient.toBlocking().exchange(finalRequest)
  }
  
  protected final def delete (final String url, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
    delete(url, null, expand)
  }
  protected final def delete (final String url, final Map params, @DelegatesTo(HttpConfig.class) final Closure expand = null) {
    UriBuilder uriBuilder = UriBuilder.of(url)
    if (params) {
      params.each { key, value -> uriBuilder.queryParam(key as String, value as String) }
    }

    HttpRequest<Object> initialRequest = HttpRequest.DELETE(uriBuilder.build())

    HttpRequest<Object> finalRequest = initialRequest

    if (expand) {
      MutableHttpRequest<Object> mutableReq = initialRequest.mutate()

      expand.setDelegate(mutableReq)
      expand.setResolveStrategy(Closure.DELEGATE_FIRST)
      expand.call()

      finalRequest = mutableReq
    }

    return httpClient.toBlocking().exchange(finalRequest)
}
}