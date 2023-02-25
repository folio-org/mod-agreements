package org.olf.general.jobs

import java.time.Instant
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.core.AppenderBase

public class JobAwareAppender extends AppenderBase<ILoggingEvent> {
  
  @Override
  protected void append(final ILoggingEvent eventObject) {
    try {
      final String jid = "${JobContext.current.get()?.jobId ?: ''}"
      
      // Grab the mdc map.
      Map<String, String> mdc = null
      
      if (eventObject instanceof LoggingEvent) {
        mdc = [:]
        for(Map.Entry entry : eventObject.getMDCPropertyMap()) {
          mdc["${entry.key}"] = "${entry.value}"
        }
      }
      
      if (jid) {
        switch (eventObject.level) {
          case Level.INFO:
//          case Level.WARN:
          
            final String tid = "${JobContext.current.get()?.tenantId ?: ''}"
            JobLoggingService.handleLogEvent(tid, jid, eventObject.formattedMessage, Level.INFO.levelStr, Instant.ofEpochMilli(eventObject.timeStamp), mdc)
            break
          
          case Level.ERROR:
            final String tid = "${JobContext.current.get()?.tenantId ?: ''}"
            JobLoggingService.handleLogEvent(tid, jid, eventObject.formattedMessage, Level.ERROR.levelStr, Instant.ofEpochMilli(eventObject.timeStamp), mdc)
            break
        }
      }
    } catch (Throwable t) {
      t.printStackTrace()
    }
  }
}