/* 
 * Copyright (C) 2010-2025 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 */
 
package no.polaric.aprsd.aprs;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;



/**
 * Duplicate checking.
 *
 * Uses ConcurrentHashMap so that multiple channel threads can check and
 * register packets concurrently without serialising on a single lock.
 * putIfAbsent() provides atomic check-and-insert semantics.  Cleanup of
 * the short-term realtime map is amortised: a full scan is triggered only
 * every CLEANUP_INTERVAL new insertions rather than on every call.
 */
public class DupCheck 
{
     private static final long RT_TIMEOUT      = 1000 * 30; /* 30 seconds */
     private static final int  CLEANUP_INTERVAL = 200;      /* clean _realtime every N new entries */
     private static final int  MAX_TIMESTAMPED  = 75000;    /* max entries in _timestamped */

     /* composed-key → first-seen time (ms); short-term window */
     private final ConcurrentHashMap<String, Long> _realtime = new ConcurrentHashMap<>();

     /* composed-key → true; permanent store, size-bounded */
     private final ConcurrentHashMap<String, Boolean> _timestamped = new ConcurrentHashMap<>();

     /* counts new insertions into _realtime to amortise cleanup */
     private final AtomicLong _insertions = new AtomicLong(0);

     /* Thread-safe date formatter – replaces SimpleDateFormat */
     private static final DateTimeFormatter _dhmsFormatter =
         DateTimeFormatter.ofPattern("ddHHmmss").withZone(ZoneOffset.UTC);


     /** Remove entries older than RT_TIMEOUT from _realtime. */
     private void cleanupRealtime()
     {
         long cutoff = System.currentTimeMillis() - RT_TIMEOUT;
         _realtime.entrySet().removeIf(e -> e.getValue() < cutoff);
     }


     /** Trim _timestamped to below MAX_TIMESTAMPED when needed. */
     private void trimTimestamped()
     {
         if (_timestamped.size() > MAX_TIMESTAMPED)
             _timestamped.keySet().removeIf(k -> _timestamped.size() > MAX_TIMESTAMPED * 9 / 10);
     }


     /**
      * Timestamp-based duplicate check (used for extra position reports).
      * Returns true if the (callsign, timestamp) pair has been seen before.
      */
     public boolean checkTS(String from, Date ts)
     {
         String composed = from + ":" + _dhmsFormatter.format(ts.toInstant());
         if (_timestamped.putIfAbsent(composed, Boolean.TRUE) != null)
             return true;
         trimTimestamped();
         return false;
     }


     /**
      * Returns true if the packet is a duplicate.
      */
     public boolean checkPacket(String from, String to, String report)
     {
         if (report == null || report.isEmpty())
             return false;

         String composed = from + to + report;
         long   now      = System.currentTimeMillis();

         switch (report.charAt(0))
         {
             /* Timestamped position reports are unique; any repeat seen before
              * can therefore be regarded as a duplicate.
              */
             case '@': case '/':
             {
                 if (_timestamped.putIfAbsent(composed, Boolean.TRUE) != null)
                     return true;
                 trimTimestamped();
                 /* Fall through: also register in the short-term store. */
             }

             /* For any other report types, look for duplicates only within
              * the short-term window.
              */
             default:
             {
                 Long existing = _realtime.putIfAbsent(composed, now);
                 if (existing != null) {
                     if (now - existing < RT_TIMEOUT)
                         return true;              /* within window → duplicate */
                     _realtime.put(composed, now); /* entry expired → overwrite, treat as new */
                 } else if (_insertions.incrementAndGet() % CLEANUP_INTERVAL == 0) {
                     cleanupRealtime();            /* amortised background cleanup */
                 }
             }
         }
         return false;
     }
}

