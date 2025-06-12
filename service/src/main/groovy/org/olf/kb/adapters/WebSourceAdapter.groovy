package org.olf.kb.adapters

import grails.gorm.multitenancy.Tenants
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import groovy.xml.slurpersupport.GPathResult
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
import io.micronaut.reactor.http.client.ReactorHttpClient
import io.micronaut.reactor.http.client.ReactorStreamingHttpClient
import java.util.concurrent.Future
import org.olf.ClientService
import org.reactivestreams.Publisher

import javax.xml.parsers.SAXParser
import javax.xml.parsers.SAXParserFactory
import java.net.HttpURLConnection
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

import groovy.xml.XmlSlurper
import groovyx.net.http.ChainedHttpConfig
import groovyx.net.http.FromServer

import java.util.function.Consumer

class RequestCustomizer {
  Map<String, String> headers = [:]

  // This method now ensures the value is a standard String
  void header(String name, Object value) {
    headers[name] = value.toString() // .toString() on a GString produces a java.lang.String
  }
}
@CompileStatic
@Slf4j
public abstract class WebSourceAdapter {

  ClientService clientService


  private static ReactorStreamingHttpClient GLOBAL_CLIENT
  
  protected ReactorStreamingHttpClient instanceClient = null
  protected ReactorStreamingHttpClient getHttpClient() {
    if (!instanceClient) {
      if (!GLOBAL_CLIENT) {
        def config = new DefaultHttpClientConfiguration()
        config.setConnectTimeout(Duration.ofSeconds(5))
        config.setReadTimeout(Duration.ofMinutes(15))
        config.setMaxContentLength(2147483647)

        GLOBAL_CLIENT = clientService.createClientReactive(null, config)

      }
      instanceClient = GLOBAL_CLIENT
    }
    instanceClient
  }
  
  
  WebSourceAdapter() {
    this.clientService = new ClientService()
//    this(null)
  }
  
  WebSourceAdapter(ReactorStreamingHttpClient httpClient) {
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



  protected final def getSync(final String url, final Map params, @DelegatesTo(RequestCustomizer.class) final Closure expand = null) {

    UriBuilder uriBuilder = UriBuilder.of(url)
    if (params) {
      params.each { key, value -> uriBuilder.queryParam(key as String, value as String) }
    }

    def userAgentHeader = "Folio mod-agreements / ${Tenants.currentId()}"
    def customizer = new RequestCustomizer()
    customizer.header('User-Agent', userAgentHeader)

    MutableHttpRequest<Object> requestBuilder = HttpRequest.GET(uriBuilder.build())
    customizer.headers.each { key, value ->
      requestBuilder.header(key, value)
    }

    HttpRequest<Object> finalRequest = requestBuilder

    try {
      HttpResponse<byte[]> response = httpClient.toBlocking().exchange(finalRequest, byte[])

      if (expand) {
        expand.setDelegate(customizer)
        expand.setResolveStrategy(Closure.DELEGATE_FIRST)
        expand.call(response)
      }

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

  // From https://guides.micronaut.io/latest/micronaut-streamed-file-and-reactor-streaming-http-client-gradle-java.html
//  def dataStreamToOutputStream(HttpRequest<?> request,
//                           PipedOutputStream outputStream,
//                           Runnable finallyRunnable) {
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


//  protected final def getSync(final String url, final Map params, @DelegatesTo(RequestCustomizer.class) final Closure expand = null) {
//
//    UriBuilder uriBuilder = UriBuilder.of(url)
//    if (params) {
//      params.each { key, value -> uriBuilder.queryParam(key as String, value as String) }
//    }
//
//    def userAgentHeader = "Folio mod-agreements / ${Tenants.currentId()}"
//
//    def customizer = new RequestCustomizer()
//    customizer.header('User-Agent', userAgentHeader)
//    MutableHttpRequest<Object> requestBuilder = HttpRequest.GET(uriBuilder.build())
//    customizer.headers.each { key, value ->
//      requestBuilder.header(key, value)
//    }
//
//    HttpRequest<Object> finalRequest = requestBuilder
//
//    // Streaming
//    PipedOutputStream outputStream = new PipedOutputStream();
//    dataStreamToOutputStream(finalRequest, outputStream, () -> log.info("finished download"));
//
//    PipedInputStream inputStream = new PipedInputStream(1024*10);
//    inputStream.connect(outputStream);
//    def parser = new XmlSlurper()
//    return parser.parse(inputStream)
//    }


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