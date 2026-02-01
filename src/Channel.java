/* 
 * Copyright (C) 2015-2026 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
 
package no.polaric.aprsd;
import no.polaric.core.*;
import no.polaric.aprsd.point.*;
import java.util.*;
import java.util.regex.*;
import java.io.*;
import java.text.*;
import java.lang.reflect.Constructor; 
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonSubTypes.*;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;



/**
 * Channel for sending/receiving APRS data. 
 */
public abstract class Channel extends Source implements ManagedObject
{
      
     /* 
      * Defined in concrete subclasses to generate or receive configurations (JSON encoded). 
      * We may make this a concrete class here?? 
      */
     @JsonTypeInfo (
         use = JsonTypeInfo.Id.NAME, 
         include = As.PROPERTY, 
         property = "type", visible=true)        
     public abstract static class JsConfig {
         public String type; 
     }
     
     public abstract JsConfig getJsConfig();
     public abstract void setJsConfig(JsConfig conf);
 
     
 
     /* State. RUNNING if running ok. */
 
     public enum State {
         OFF, STARTING, RUNNING, FAILED
     }
     protected State _state = State.OFF;

     protected Logfile log = new Logfile.Dummy();
     
 
     protected String chId() {
        return "["+getIdent()+"] "; 
     }
    
    
     public State getState() 
        { return _state; }
        
     
     public boolean isRf() 
        { return false; }
     
     
     public abstract void activate(AprsServerConfig conf);
     public abstract void deActivate();
     
   
     /** Return true if service is running */
     public boolean isActive() {
        return getState() == State.STARTING || getState() == State.RUNNING;
     }
      public boolean isReady() {
        return getState() == State.RUNNING;
     }
 
     
     
     
     /**
      * Abstract factory for Channel objects. 
      */
      
     /* FIXME: Consider writing the interface spec */

     public static class Manager {
         private HashMap<String, String>  _classes = new HashMap<String, String>();
         private HashMap<String, Channel> _instances = new LinkedHashMap<String, Channel>();
         private Set<String> _backups = new HashSet<String>();
         
         
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
         
         
         
         public Class<Channel> getClass(String tname) {
            try {
               String cname = _classes.get(tname);
               if (cname == null)
                  return null;
               return (Class<Channel>) Class.forName(cname);
            }
            catch (Exception e) {
               e.printStackTrace(System.out);
               return null; 
            }
         }
         
         
         
         
         /**
          * Instantiate a channel.
          * @param conf
          * @param tname A short name for the type. See addClass method.
          * @param id A short name for the channel instance to allow later lookup.  
          */
        @SuppressWarnings("unchecked")
         public Channel newInstance(AprsServerConfig conf, String tname, String id)
         {
            try {
               String cname = _classes.get(tname); 
               if (cname == null)
                  return null; // Or throw exception??
               Class<Channel> cls = (Class<Channel>) Class.forName(cname);
               Constructor<Channel> constr = (Constructor<Channel>) cls.getConstructors()[0];
               Channel  c = constr.newInstance(conf, id);
               _instances.put(id, c);
               return c;
            }
            catch (Exception e) {
               e.printStackTrace(System.out);
               return null; 
            }
         }
         
         
         public void removeInstance(String id) {
            _instances.remove(id);
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

