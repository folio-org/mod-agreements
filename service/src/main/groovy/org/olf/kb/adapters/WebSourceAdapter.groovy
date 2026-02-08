package org.olf.kb.adapters

import grails.gorm.multitenancy.Tenants
import groovy.transform.CompileStatic
import groovyx.net.http.HttpBuilder
import groovyx.net.http.HttpConfig
import groovyx.net.http.HttpObjectConfig
import java.net.HttpURLConnection
import java.util.concurrent.CompletableFuture
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

import groovy.xml.XmlSlurper
import groovyx.net.http.ChainedHttpConfig
import groovyx.net.http.FromServer

@CompileStatic
public abstract class WebSourceAdapter {

  protected final AdapterClient httpClient // Classes that extend WSA MUST inject a httpClient in their constructor.

  WebSourceAdapter(AdapterClient client) {
    if (client == null) {
      throw new IllegalArgumentException("AdapterClient cannot be null")
    }
    this.httpClient = client
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
  
  protected final def getSync (final String url) {
    getSync( url, null)
  }

  protected final def getSync (final String url, final Map params) {
    // todo: do headers need to be an argument, or are they always the same for a given client implementation?
    // todo: e.g. does the GoKb client always need the header below (in which case it can be on the client implementation, not here).
//    def header = "Folio mod-agreements / ${Tenants.currentId()}"

    return httpClient.getData(url, params)
  }

  // Todo: Need to rework the remaining methods in webSourceAdapter to use AdapterClient methods. These are currently unused though.
  
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
}
