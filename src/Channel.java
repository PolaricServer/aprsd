 /* 
 * Copyright (C) 2015 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import java.util.regex.*;
import java.io.*;
import se.raek.charset.*;
import java.text.*;
import java.lang.reflect.Constructor; 


/**
 * Channel for sending/receiving APRS data. 
 */
public abstract class Channel extends Source implements Serializable, ManagedObject
{
 
     /* State. RUNNING if running ok. */
 
     public enum State {
         OFF, STARTING, RUNNING, FAILED
     }
     transient protected State _state = State.OFF;

     
     
     public State getState() 
        { return _state; }
        
        
   
     /** Return true if service is running */
     public boolean isActive() {
        return _state == State.STARTING || _state == State.RUNNING;
     }
 
 
     
     /**
      * Abstract factory for Channel objects. 
      */
      
     /* FIXME: Consider writing the interface spec */

     public static class Manager {
         private HashMap<String, String>  _classes = new HashMap();
         private HashMap<String, Channel> _instances = new LinkedHashMap();
         private Set<String> _backups = new HashSet();
         
         
         /**
          * Register a channel class. 
          * @param tname A short name for the class/type.
          * @param cls The full name of the class.
          */
         public void addClass(String tname, String cls)
         {
            _classes.put(tname, cls);
         }

         
         /**
          * Get the set of typenames for channel classes (see addClass). 
          */
          
         public Set<String> getTypes() 
         {
             return _classes.keySet();
         }
         
         
         /**
          * Instantiate a channel.
          * @param api
          * @param tname A short name for the type. See addClass method.
          * @param id A short name for the channel instance to allow later lookup.  
          */
         public Channel newInstance(ServerAPI api, String tname, String id)
         {
            try {
               String cname = _classes.get(tname); 
               if (cname == null)
                  return null; // Or throw exception??
               Class<Channel> cls = (Class<Channel>) Class.forName(cname);
               Constructor<Channel> constr = (Constructor<Channel>) cls.getConstructors()[0];
               Channel  c = constr.newInstance(api, id);
               _instances.put(id, c);
               return c;
            }
            catch (Exception e) {
               e.printStackTrace(System.out);
               return null; 
            }
         }
         
         
         /**
          * Get the full set of channel names. Keys for lookup.
          */
         public Set<String> getKeys()
           { return _instances.keySet(); }
           
           
         /**
          * Look up a channel from a name.
          */
         public Channel get(String id)
           { return _instances.get(id); }
           
           
         /**
          * Return true if the named channel is a backup-channel
          */
         public boolean isBackup(String id)
            { return _backups.contains(id); }
         
         
         public void addBackup(String id)
            { _backups.add(id); }
     }

          
     public String toString() { return "Channel"; } 
    
}

