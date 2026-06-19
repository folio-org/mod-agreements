package org.olf.general

import java.util.regex.Matcher

import groovy.transform.CompileStatic

/**
 * Helpers for reading buffer-style env vars that gate scheduled jobs.
 *
 * Supported values (matches the long-standing KB_HARVEST_BUFFER contract):
 *   - any integer string  -> parsed as milliseconds
 *   - the literal "ZERO"  -> 0
 *   - anything else / unset -> the caller-supplied default
 */
@CompileStatic
class EnvUtils {

  static final String KB_HARVEST_BUFFER = "KB_HARVEST_BUFFER"
  static final String EHOLDINGS_SYNC_BUFFER = "EHOLDINGS_SYNC_BUFFER"

  static long readBufferMs(String envVarName, long defaultMs) {
    String value = System.getenv(envVarName)
    if (!value) {
      return defaultMs
    }
    switch (value) {
      case ~/([0-9]+)/:
        return "${Matcher.lastMatcher.group(1)}".toLong()
      case 'ZERO':
        return Constants.Time.ZERO
      default:
        return defaultMs
    }
  }
}