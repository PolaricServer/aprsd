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
import java.text.*;



/**
 * Duplicate checking.
 */
public class DupCheck 
{
     private static final Boolean TRUE = true;
     private static final long RT_TIMEOUT = 1000 * 30; /* 30 seconds */
     
     private LinkedHashMap<String, Date> _realtime  = 
        new LinkedHashMap<String, Date>(); 
        
     private LinkedHashMap<String, Boolean> _timestamped = 
        new LinkedHashMap<String, Boolean>() 
        {
            protected boolean removeEldestEntry(Map.Entry e)
                { return size() > 75000; }
        };
    
     
     private void removeOldRtEntries()
     {
          Iterator<Date> it = _realtime.values().iterator();
          Date now = new Date();
          while (it.hasNext()) {
              Date x = it.next();
              if (now.getTime() > x.getTime() + RT_TIMEOUT) 
                 it.remove();
              else
                 return;
          }
     } 
     
     
     private static DateFormat _dhmsFormat = new SimpleDateFormat("ddHHmmss");
     static {
        _dhmsFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
     }
     
     public synchronized boolean checkTS(String from, Date ts)
     {
         String composed = from + ":"+_dhmsFormat.format(ts);
         if (_timestamped.containsKey(composed))
             return true;
         _timestamped.put(composed, TRUE);
         return  false;
     }
     
     
     /**
      *  Returns true if packet is a duplicate.
      */
     public synchronized boolean checkPacket(String from, String to, String report)
     {
         String composed = from+to+report;
         if (report == null || report.length() < 1)
            return false;
         switch(report.charAt(0))
         {
              /* Timestamped position reports are unique and 
               * any reports which are seen before, can be
               * therefore be regarded as duplicates 
               */
              case '@': case '/':
              {
                  if (_timestamped.containsKey(composed))
                    return true;
                  _timestamped.put(composed, TRUE);
              }
    
              /* For any other report types, we should only look for
               * duplicates within a timeframe of a few minutes. 
               */
              default: 
              {
                  removeOldRtEntries();
                  if (_realtime.containsKey(composed))
                     return true; 
                  _realtime.put(composed, new Date());
              }
         }
         return false;
     } 
}

