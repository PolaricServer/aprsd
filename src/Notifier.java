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
  
  
/**
 * This class allows threads to wait for certain events to occur within
 * a geographical area. 
 */
 
public class Notifier
{
    private Date signalled = new Date(); 
    private AprsPoint signalledPt; 
    private static long _mintime  = 1000 * 10;   /* Minimum wait time: 10s */
    private static long _timeout  = 1000 * 120;  /* Maximum wait time: 2min */
    private Map<Long, Integer> _waiters = new HashMap();
    // 0 = continue waiting, 1 = return XML, 2 = abort and return nothing
    
    /**
     * Wait for an event to happen within the given geographical area. 
     * @param uleft: Upper left corner of the area.
     * @param lright: Lower right corner of the area. 
     * @param id: Identity of the waiter thread. Assumed to be a unique number. 
     *            Other waiters with the same id will be aborted.
     */
    public boolean waitSignal(UTMRef uleft, UTMRef lright, long id)
    {
         long wstart = (new Date()).getTime();
         long elapsed = 0;
         boolean found = false;
         boolean noAbort = false;
                  
         synchronized (this) {
            /* Abort any other waiter having the same id  */
            if (_waiters.containsKey(id)) {
                _waiters.put(id, 2);
                notifyAll();
                noAbort = true; 
            }
            else
                _waiters.put(id, 0);
         }    
         do {
              try {
                  synchronized(this) {
                     wait(found ? _mintime-elapsed : _timeout-elapsed);
                     Integer abort = _waiters.get(id);
                     if (abort != null && abort > 0 && !noAbort) {
                         if (abort == 1) 
                            _waiters.remove(id);
                         else
                            _waiters.put(id, 0);
                         return (abort==1) ? true : false;        
                     }
                     noAbort = false; 
                     /* Has there been events inside the interest zone */
                     found = found || signalledPt == null || uleft == null || 
                                  signalledPt.isInside(uleft, lright); 
                  } 
              }
              catch (Exception e) {}    
              elapsed = (new Date()).getTime() - wstart;

            /* Wait no shorter than _mintime and no longer 
             * than _timeout 
             */
         } while ( !(found && elapsed > _mintime) &&
                   elapsed < _timeout );
         _waiters.remove(id);
         return true;
    }
    
    /**
     * Signal an event on a certain geographical point. This will wake up
     * waiters that subscribes to an area containing this location.
     */
    public synchronized void signal(AprsPoint st)
    {   
         signalled = new Date(); 
         signalledPt = st;
         notifyAll();
    }
    
    /**
     * Abort all waiters.
     */
    public synchronized void abortAll(boolean retval)
    {
       for (long x: _waiters.keySet())
          _waiters.put(x, retval ? 1 : 2 );
       notifyAll();   
    }
    
}
