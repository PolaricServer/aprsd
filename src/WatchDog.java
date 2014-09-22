/* 
 * Copyright (C) 2014 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import java.util.*; 
 
public class WatchDog {
    
    /** 
     * Static thread to periodically go through all active watchdogs and check if they have expired. 
     */
    private class DogList extends Thread {
       List<WatchDog> dogs = new LinkedList<WatchDog>(); 
       
       public void add(WatchDog x) { 
          if (dogs.size() == 0)
             start();
          dogs.add(x); 
       }
       
       public void remove(WatchDog x)
          { dogs.remove(x); }
          
       public void run() {
           while (true) {
              try { Thread.sleep(10000); } 
              catch (Exception e) {}
              
              for (WatchDog x: dogs)
                  if (x.expired())
                     x.fireAction();
           }
       }
    }
    
    private DogList _check;
    private Date    _checkedIn;
    private long    _maxTime;
    private String  _logmsg; 
 
    
    
    public WatchDog(long maxTime, String logmsg) {
       _maxTime = maxTime*1000; 
       _logmsg = logmsg; 
    }
    
    
    
    public void checkIn() {
        _checkedIn = new Date();
    }
    
    
    public void close() {
       _check.remove(this);
    }
    
    
    public boolean expired() {
        Date d = new Date(); 
        return (d.getTime() > _checkedIn.getTime() + _maxTime);
    } 
    
    
    protected void fireAction() {
        if (_logmsg != null)
           System.out.println("*** WatchDog: "+_logmsg);
        action();  
    }
 
 
    /** 
     * Override this in subclass.
     */
    public void action() {}
}
