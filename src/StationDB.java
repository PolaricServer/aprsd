/* 
 * Copyright (C) 2015-2022 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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

    /* Interface to Db plugin */
    public interface Hist
    {
        /**
         * Save info for managed item in database.
         * @param tp Item to update
         */
        public void saveManagedItem(TrackerPoint tp); 
        
        
        /**
         * Update managed item from database. 
         * @param tp Item to update
         */
        public void updateManagedItem(TrackerPoint tp); 
       
             
        /**
         * Remove mangaged item from database (from someone's myTrackers list)
         * @param id identifier (callsign) of item to remove. 
         */
        public void removeManagedItem(String id); 
        
        
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
    }
    
    
    
        
    /** 
     * Get interface to database storage (plugin) 
     */
    public StationDB.Hist getHistDB();
    
        
            
            
    /***********************************
     * Item methods (get/add, remove)
     ***********************************/
     
     
    /** 
     * Return the number of realtime items. 
     */
    public int nItems(); 
    
    
    /**
     * Get an item.  At a specific time or if d is null, get realtime item. 
     * @param id identifier (typically a callsign) of item.
     * @param d time of capture, null if realtime.
     */
    public TrackerPoint getItem(String id, Date d);
    
    
    /** 
     * Get item at a particular time. Realtime if t is null.
     * If requested, check database for updates (if item is managed).
     * @param id identifier (typically a callsign) of item.
     * @param t time of capture, null if realtime.
     * @param checkdb Check for updates on managed object...
     */ 
    public TrackerPoint getItem(String id, Date t, boolean checkdb);
      
            
    
     /**
      * Get an APRS station. 
      * @param id identifier (typically a callsign) of item.
      * @param t time of capture, null if realtime.
      */    
     public Station getStation(String id, Date t);
     
     
     
    /**
     * Add a tracker point. 
     * @param s existing item
     */
    public void addItem(TrackerPoint s);
    
    
    
    /**
     * Create a new APRS station. 
     * @param id identifier (typically a callsign) of item.
     */
    public Station newStation(String id);
    

     
    /**
     * Create a new APRS object. 
     * @param owner identifier (typically a callsign) of owner station.
     * @param id identifier of object.
     */    
    public AprsObject newObject(Station owner, String id);
    
    
    /**
     * Update an existing tracker point. 
     * @param s existing station
     */
    public void updateItem(TrackerPoint s); 
    
    
    /**
     * Remove item.
     * @param id identifier (typically a callsign) of item.
     */
    public void removeItem(String id);
    

        
    
    /****************************
     * Item search methods
     ****************************/
    
    /**
     * Return a list of trackerpoints where the ident has the given prefix. 
     * @Param srch Prefix 
     */
    public List<TrackerPoint> searchPrefix(String arg);
    
    
    
    /**
     * Search in the database of trackerpoints. 
     * Return a list of trackerpoints where ident or description matches the given search 
     * expression AND all the tags in the list.
     *
     * If the search expression is prefixed with "REG:", it is regarded as a regular 
     * expression, otherwise it is a simple wildcard expression.
     *
     * @param srch Search expression.
     * @param tags Array of tags (keywords).
     */
    public List<TrackerPoint> search(String srch, String[] tags);
     
    
    
    /**
     * Geographical search in the database of trackerpoints. 
     * Return list of stations within the rectangle defined by uleft (upper left 
     * corner) and lright (lower right corner).
     * @param uleft Upper left corner.
     * @param lright Lower right corner.
     */
    public List<TrackerPoint>
          search(Reference uleft, Reference lright);       
        
        
        
        
    /****************************
     * Other methods
     ****************************/
    
    /**
     * Get trail point for an item at a particular time.
     * @param id identifier (typically a callsign) of item.
     * @param d time of capture
     */
    public Trail.Item getTrailPoint(String id, java.util.Date t);
    
    
    /**
     * Get info on routes. Where APRS packets have travelled.
     */
    public RouteInfo getRoutes();
    
        
    /**
     * Get APRS objects owned by this server. 
     */
    public OwnObjects getOwnObjects(); 
    
    
    /**
     * Deactivate other objects having the given owner and id. 
     */ 
    public void deactivateSimilarObjects(String id, Station owner);
    
    
    /**
     * Shutdown. May save state, etc.. 
     */
    public void shutdown(); 
  
}
