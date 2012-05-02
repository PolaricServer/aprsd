/* 
 * Copyright (C) 2010 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import java.util.List;  
import uk.me.jstott.jcoord.*;
import java.util.Date;

/**
 * Interface to database of stations, objects, etc.
 */
public interface StationDB
{
    public interface Hist
    {
        /**
         * Get item. 
         * @param id: identifier (typically a callsign) of item.
         * @param t: time of capture, null if realtime
         */
        public AprsPoint getItem(String id, Date d);
    }
    
    


       /** 
        * Return the number of items. 
        */
       public int nItems(); 
    
    
       /**
        * Get info on routes. Where APRS packets have travelled.
        */
       public RouteInfo getRoutes();
    
    
       /**
        * Get APRS objects owned by this server. 
        */
       public OwnObjects getOwnObjects(); 
    
    
       /**
        * Get messaging processor.
        */
       public MessageProcessor getMsgProcessor();
    
       
        /**
         * Get item. 
         * @param id: identifier (typically a callsign) of item.
         * @param t: time of capture, null if realtime
         */
        public AprsPoint getItem(String id, Date t);       
       
       
       /**
         * Get an APRS station. 
         * @param id: identifier (typically a callsign) of item.
         * @param t: time of capture, null if realtime
         */    
        public Station getStation(String id, Date d);
       
       
       
       /**
        * Remove item.
        * @param id: identifier (typically a callsign) of item.
        */
       public void removeItem(String id);

    
       /**
        * Create a new APRS station. 
        * @param id: identifier (typically a callsign) of item.
        */
       public Station newStation(String id);
    
        /**
        * Add an existing APRS station. 
        * @param s: existing station
        */
       public void addStation(Station s);
        
       /**
        * Create a new APRS object. 
        * @param owner: identifier (typically a callsign) of owner station.
        * @param id: identifier of object.
        */    
       public AprsObject newObject(Station owner, String id);
    
    
       /**
        * Deactivate other objects having the given owner and id. 
        */ 
       public void deactivateSimilarObjects(String id, Station owner);
    
    
       public List<AprsPoint> getAll(String arg);
    
       public List<AprsPoint> getAllPrefix(String arg);
        
        
        
       /**
        * Search. Return a list of all items within the given geographical area.
        */
       public List<AprsPoint>
          search(Reference x1, Reference y1, Reference x2, Reference y2);
   
      /**
       * Search. Return a list of all items within the given geographical area.
       */
       public List<AprsPoint>
             search(UTMRef uleft, UTMRef lright);       
    
    
       // FIXME: These seems to be implementation specific
       public void save(); 
       public void garbageCollect();
  
}
