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
         * Save item info to database. 
         * @param tp Item to update
         */
        public void saveItem(TrackerPoint tp); 
        
        
        /**
         * Update item from database. 
         * @param tp Item to update
         */
        public void updateItem(TrackerPoint tp); 
       
     
        /**
         * Get item at a particular point in time. 
         * @param id identifier (typically a callsign) of item.
         * @param d time of capture, null if realtime
         */
        public TrackerPoint getItem(String id, Date d);
        
        
        /**
         * Get trail point. 
         * @param src identifier (typically a callsign) of station owning trail.
         * @param t time of capture
         */
        public Trail.Item getTrailPoint(String src, java.util.Date t);
        
        
        /**
         * Remove item from someone's myTrackers list
         * @param id identifier (callsign) of item to remove. 
         */
        public void removeManagedItem(String id); 
        
    }
    
    public StationDB.Hist getHistDB();

    public TrackerPoint getItem(String id, Date d);
      
    public TrackerPoint getItem(String id, Date t, boolean usedb);
      
    public void saveItem(TrackerPoint x);
      
      
    public Trail.Item getTrailPoint(String src, java.util.Date t);
        
        
        
        
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
         * Get an APRS station. 
         * @param id identifier (typically a callsign) of item.
         * @param d time of capture, null if realtime.
         */    
        public Station getStation(String id, Date d);
       
       
       
       /**
        * Remove item.
        * @param id identifier (typically a callsign) of item.
        */
       public void removeItem(String id);

    
       /**
        * Create a new APRS station. 
        * @param id identifier (typically a callsign) of item.
        */
       public Station newStation(String id);
    
    
        /**
        * Add an existing tracker point. 
        * @param s existing station
        */
       public void addPoint(TrackerPoint s);
        
        
       /**
        * Create a new APRS object. 
        * @param owner identifier (typically a callsign) of owner station.
        * @param id identifier of object.
        */    
       public AprsObject newObject(Station owner, String id);
    
    
       /**
        * Deactivate other objects having the given owner and id. 
        */ 
       public void deactivateSimilarObjects(String id, Station owner);
    
       public List<TrackerPoint> getAllPrefix(String arg);

       public List<TrackerPoint> search(String srch, String[] tags);
       
      /**
       * Search. Return a list of all items within the given geographical area.
       */
       public List<TrackerPoint>
             search(Reference uleft, Reference lright);       
    
    
       // FIXME: These seems to be implementation specific
       public void save(); 
       public void garbageCollect();
  
}
