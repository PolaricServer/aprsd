/* 
 * Copyright (C) 2009 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
 
package no.polaric.aprsd;
import uk.me.jstott.jcoord.*; 
import java.util.*;
import java.io.Serializable;
  
  
  
public class Notifier
{
    private Date signalled = new Date(); 
    private AprsPoint signalledPt; 
    private static long _mintime  = 1000 * 10;   /* Minimum wait time: 10s */
    private static long _timeout  = 1000 * 120;  /* Maximum wait time: 2min */
    private Map<Long, Integer> _waiters = new HashMap();
    // 0 = continue waiting, 1 = return XML, 2 = abort and return nothing
    
    
    public boolean waitSignal(UTMRef uleft, UTMRef lright, long id)
    {
         long wstart = (new Date()).getTime();
         long elapsed = 0;
         boolean found = false;
         synchronized (this) {
            /* Abort any other waiter having the same id  */
            if (_waiters.containsKey(id)) {
                _waiters.put(id, 2);
                notifyAll();
            }
            else
                _waiters.put(id, 0);
         }    
         do {
              try {
                  Thread.sleep(100);
                  synchronized(this) {
                     wait(found ? _mintime-elapsed : _timeout-elapsed);
                     Integer abort = _waiters.get(id);
                     if (abort != null && abort > 0) {
                         System.out.println("*** Abort waiter: "+id);
                         _waiters.put(id, 0);
                         return (abort==1) ? true : false;        
                     }
                     
                  } 
                  /* Wait a little to allow more updates to arrive */
                  Thread.sleep(500); 
              }
              catch (Exception e) {}    
              elapsed = (new Date()).getTime() - wstart;
            
              /* Has there been events inside the interest zone */
              synchronized(this) {
                 found = found || signalledPt == null || uleft == null || 
                                  signalledPt.isInside(uleft, lright); 
              }
            /* Wait no shorter than _mintime and no longer 
             * than _timeout 
             */
         } while ( !(found && elapsed > _mintime) &&
                   elapsed < _timeout );
         _waiters.remove(id);
         return true;
    }
    
    
    public synchronized void signal(AprsPoint st)
    {   
         signalled = new Date(); 
         signalledPt = st;
         notifyAll();
    }
    
    public synchronized void abortAll(boolean retval)
    {
       for (long x: _waiters.keySet())
          _waiters.put(x, retval ? 1 : 2 );
       notifyAll();   
    }
    
}
