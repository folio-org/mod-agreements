package com.k_int.folio;

import java.util.List;
import java.util.Set;

public class FolioHeaders {
  public static final Set<String> FOLIO_HEADERS = Set.of(
    "x-okapi-tenant",
    "x-okapi-token"
  );

  public static final List<String> FOLIO_COOKIE_PREFIXES = List.of(
    "folioAccessToken=",
    "folioRefreshToken="
  );

  // These MUST be pairs
  public static final List<List<String>> BASE_HEADERS = List.of(
    List.of("Content-Type", "application/json"),
    List.of("accept", "application/json")
  );

  public static boolean isFolioCookie(String cookieValue) {
    for (String prefix : FOLIO_COOKIE_PREFIXES) {
      if (cookieValue.contains(prefix)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isFolioHeader(String headerName, String headerValue) {
    return FOLIO_HEADERS.contains(headerName.toLowerCase()) || (headerName.equalsIgnoreCase("cookie") && isFolioCookie(headerValue));
  }
}