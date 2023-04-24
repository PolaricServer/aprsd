 /* 
 * Copyright (C) 2022 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import java.io.*;
import uk.me.jstott.jcoord.*; 
import java.util.concurrent.*;
import java.util.regex.*;
import java.util.stream.*;


/**
 * Abstract class for Station DB implementations. Common stuff.
 */
public abstract class StationDBBase
{
    protected boolean    _hasChanged = false; 
    protected RouteInfo  _routes;
    protected OwnObjects _ownobj; 
    protected StationDB.Hist _histData = null;
    protected ServerAPI  _api; 
    
    
    public StationDBBase(ServerAPI api)
    {
        _api = api;
        _routes = new RouteInfo();
        _ownobj = new OwnObjects(api); 
    }
    

    
    
    protected static final Runtime s_runtime = Runtime.getRuntime ();
       // FIXME In which class(es) is this used?
    
    public static long usedMemory ()
        { return s_runtime.totalMemory () - s_runtime.freeMemory (); }

        
    /** Register the implementation of database storage (plugin) */
    public void setHistDB(StationDB.Hist d)
        { _histData = d; }
        

    /** Get interface to database storage (plugin) */
    public StationDB.Hist getHistDB() 
        {return _histData; }
        
    
    
    /***********************************
     * Item methods (get/add, remove)
     ***********************************/
     
    /**
     * Get an item.  At a specific time or if d is null, get realtime item. 
     * @param id identifier (typically a callsign) of item.
     * @param d time of capture, null if realtime.
     */
    public TrackerPoint getItem(String id, Date t)
       { return getItem(id, t, false); }
     
     
     

    /** 
     * Get item at a particular time. Realtime if t is null.
     * If requested, check database for updates (if item is managed).
     * @param id identifier (typically a callsign) of item.
     * @param t time of capture, null if realtime.
     * @param checkdb Check for updates on managed object...
     */ 
    public TrackerPoint getItem(String id, Date t, boolean checkdb)
    { 
       TrackerPoint x = null;
       
       /* Real time. */
       if (t==null) {
          x = _getRtItem(id); 
          /* If requested, check database storage for updates */
          if (checkdb && x != null && _histData != null && x.hasTag("MANAGED"))
             _histData.updateManagedItem(x);
       }       
       /* 
        * Another time in history. We need to get it from database storage plugin 
        * Note that this is currently limited to APRS stations or objects. 
        */
       else if (_histData !=null) 
          x = _histData.getItem(id, t); 
       return x;
    }

    
    
    /** 
     * Get realtime item. To be implemented by subclass 
     */
    protected abstract TrackerPoint _getRtItem(String id);
    
    
            
    /**
      * Get an APRS station. 
      * @param id identifier (typically a callsign) of item.
      * @param t time of capture, null if realtime.
      */ 
    public Station getStation(String id, Date t)
    { 
         TrackerPoint x = getItem(id, t);
         if (x instanceof Station) return (Station) x;
         else return null;
    }      
     
    
   
    /**
     * Create a new APRS station. 
     * @param id identifier (typically a callsign) of item.
     */
    public Station newStation(String id)
    {  
        Station st = new Station(id);
        addItem(st);
        return st;
    }
         
    
    
    /**
     * Create a new APRS object. 
     * @param owner identifier (typically a callsign) of owner station.
     * @param id identifier of object.
     */    
    public AprsObject newObject(Station owner, String id)
    {
        AprsObject st = new AprsObject(owner, id);
        _addRtItem(st);
        return st;
    }

     

    /**
     * Add a tracker point. If it exists, replace it. 
     * @param s item
     */
    public void addItem(TrackerPoint s)
    { 
        if (_histData != null && s != null && s.getIdent() != null)
            _histData.updateManagedItem(s);
        _addRtItem(s);
    }
    
        
    
    /**
     * Add realtime item. To be implmented in subclass.
     */
    protected abstract void _addRtItem(TrackerPoint s);
     
    
    
    /**
     * Remove item.
     * @param id identifier (typically a callsign) of item.
     */
    public synchronized void removeItem(String id)
    {   
        String[] idd = id.split("@");
          
        if (idd != null && idd.length > 1 && idd[1].equals(_api.getOwnPos().getIdent()))
            _ownobj.delete(idd[0]);
            
        _removeRtItem(id);
        _hasChanged = true; 
    }
        
        
    protected abstract void _removeRtItem(String id);
     
         
         
         
    /****************************
     * Other methods
     ****************************/

    /**
     * Get trail point for an item at a particular time.
     * @param id identifier (typically a callsign) of item.
     * @param d time of capture
     */
    public Trail.Item getTrailPoint(String src, java.util.Date t)
    {
        TrackerPoint st = getItem(src, null);
        if (st != null && st.getTrail() != null) {
           Trail.Item x = st.getTrail().getPointAt(t);
           if (x != null)
              return x;
        }
        if (_histData != null)
           return _histData.getTrailPoint(src, t);
        return null;
    }
   
    
    /**
     * Get info on routes. Where APRS packets have travelled.
     */
    public RouteInfo getRoutes()
        { return _routes; }
        
    
    /**
     * Get APRS objects owned by this server. 
     */    
    public OwnObjects getOwnObjects()
        { return _ownobj; }

     
}
