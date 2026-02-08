package org.olf.kb.adapters

interface AdapterClient {
  Object getData(String url, Map<String, Object> params)

}