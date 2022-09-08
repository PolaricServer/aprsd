/* 
 * Copyright (C) 2016-2020 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
 * In-memory implementation of StationDB.
 * Data is saved to a file. Periodically and when program ends.
 */
public class StationDBImp implements StationDB, Runnable
{
    private SortedMap<String, TrackerPoint> _map = new ConcurrentSkipListMap();
   
    private String     _file;
    private String     _stnsave;
    private boolean    _hasChanged = false; 
    private RouteInfo  _routes;
    private OwnObjects _ownobj;
    private MessageProcessor _msgProc;
    private StationDB.Hist _histData = null;
    private ServerAPI  _api; 

    
    public StationDBImp(ServerAPI api)
    {
        _api = api;
        _file = api.getProperty("stations.file", "stations.dat");
        _stnsave = api.getProperty("stations.save", ".*");
        if (_file.charAt(0) != '/')
           _file = System.getProperties().getProperty("datadir", ".")+"/"+_file;   
        _ownobj = new OwnObjects(api); 
        _msgProc = new MessageProcessor(api);
        restore();
        Thread t = new Thread(this, "StationDBImp");
        t.start(); 
    }
    

    
    
    private static final Runtime s_runtime = Runtime.getRuntime ();
    public static long usedMemory ()
        { return s_runtime.totalMemory () - s_runtime.freeMemory (); }

    
    public MessageProcessor getMsgProcessor()
        { return _msgProc; }
        
        
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
     * Return the number of realtime items. 
     */   
    public int nItems() 
        { return _map.size(); }
        
        
        
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
          x = _map.get(id); 
          /* If requested, check database storage for updates */
          if (checkdb && x != null && _histData != null && x.hasTag("MANAGED"))
             _histData.updateManagedItem(x);
       }
       /* Another time in history. We need to get it from database storage plugin */
       else if (_histData !=null) 
          x = _histData.getItem(id, t); 
       return x;
    }
     
     
                 
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
     * Add an existing tracker point. 
     * @param s existing station
     */
    public void addItem(TrackerPoint s)
    { 
        if (_histData != null && s != null && s.getIdent() != null)
            _histData.updateManagedItem(s);
        if ( s != null && s.getIdent() != null) {
            _map.remove(s.getIdent());
            _map.put(s.getIdent(), s);
        }
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
        _map.put(id+'@'+owner.getIdent(), st); 
        return st;
    }
    
        
    
    /**
     * Remove item.
     * @param id identifier (typically a callsign) of item.
     */
    public synchronized void removeItem(String id)
    {   
        String[] idd = id.split("@");
          
        if (idd != null && idd.length > 1 && idd[1].equals(_api.getOwnPos().getIdent()))
            _ownobj.delete(idd[0]);
            
        TrackerPoint x = _map.get(id);
        _map.remove(id);
        _hasChanged = true; 
    }
        
        
        
        
        
        
    /****************************
     * Item search methods
     ****************************/
     
    /**
     * Return a list of trackerpoints where the ident has the given prefix. 
     * @Param srch Prefix 
     */
    public List<TrackerPoint> searchPrefix(String srch)
    {
        if (srch == null)
           return new LinkedList();
        return _map.values().stream().filter( s ->
           ( s.getIdent().toUpperCase().startsWith(srch) )).collect(Collectors.toList());
    }
     
     
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
    public List<TrackerPoint> search(String srch, String[] tags)
    {
         LinkedList<TrackerPoint> result = new LinkedList();
         srch = srch.toUpperCase();
         if (srch.matches("REG:.*"))
           srch = srch.substring(4);
         else {
           srch = srch.replaceAll("\\.", Matcher.quoteReplacement("\\."));
           srch = srch.replaceAll("\\*", Matcher.quoteReplacement("(\\S*)"));
         }
         
         final String _srch = srch;
         return _map.values().stream().filter( s -> 
              ( s.getIdent().toUpperCase().matches(_srch) ||
                   s.getDisplayId().toUpperCase().matches(_srch) ||
                   s.getDescr().toUpperCase().matches("(.*\\s+)?\\(?("+_srch+")\\)?\\,?(\\s+.*)?") ) &&
              ( tags==null ? true : 
                   (Arrays.stream(tags).map(x -> s.tagIsOn(x))
                                       .reduce((x,y) -> (x && y))).get())
          ).collect(Collectors.toList());
    } 
     
     
    /**
     * Geographical search in the database of trackerpoints. 
     * Return list of stations within the rectangle defined by uleft (upper left 
     * corner) and lright (lower right corner).
     * @param uleft Upper left corner.
     * @param lright Lower right corner.
     */
    public List<TrackerPoint>
          search(Reference uleft, Reference lright)
    { 
          long start = System.nanoTime();

          var res = _map.values().stream().filter( s ->
               (uleft==null || lright==null || s.isInside(uleft, lright, 0.1, 0.1))
          ).collect(Collectors.toList()); 
          
          long finish = System.nanoTime();
          long timeElapsed = finish - start;
          System.out.println("Search Trackerpoints - Time Elapsed (us): " + timeElapsed/1000);
          return res;
    }
    
    
    
    
    
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

    
    
    /**
     * Deactivate objects having the given owner and id.
     */
    public void deactivateSimilarObjects(String id, Station owner)
    {
        Collection<TrackerPoint> dupes =  _map.subMap(id+'@', id+'@'+"zzzzz").values();
        for (TrackerPoint x : dupes)
           if (x instanceof AprsObject && !((AprsObject)x).isTimeless() && 
                  !owner.getIdent().equals(((AprsObject)x).getOwner().getIdent())) {
               ((AprsObject)x).kill();
           }
    }
    
    
    
    /**
     * Shutdown. May save state, etc.. 
     */
    public void shutdown()
       { save(); }
    
    
    
    
    /******************************
     * Private helper methods
     ******************************/
    
    /** 
     * Save (checkpoint) station data to disk file. 
     */
    private synchronized void save()
    {
        if (_hasChanged)
          try {
             _api.log().info("StationDBImp", "Saving data...");
             FileOutputStream fs = new FileOutputStream(_file);
             ObjectOutput ofs = new ObjectOutputStream(fs);
             
             ofs.writeObject(_routes);
             _msgProc.save();
             _ownobj.save(ofs);
             PointObject.saveTags(ofs);
             for (TrackerPoint s: _map.values()) { 
                if (s.getIdent().matches("("+_stnsave+")|.*\\@("+_stnsave+")"))
                    ofs.writeObject(s); 
             }

           }
           catch (Exception e) {
               _api.log().warn("StationDBImp", "Cannot save data: "+e);
               e.printStackTrace();
           } 
         _hasChanged = false; 
    }
    
    
    
    /**
     * Restore station data from disk file. 
     */
    private synchronized void restore()
    {
        try {
          _api.log().info("StationDBImp", "Restoring point data...");
          FileInputStream fs = new FileInputStream(_file);
          ObjectInput ifs = new ObjectInputStream(fs);
          
          _api.log().debug("StationDBImp", "Restoring routes...");
          _routes = (RouteInfo) ifs.readObject();
          _api.log().debug("StationDBImp", "Restoring msgproc and ownobj...");    
          _msgProc.restore();
          _ownobj.restore(ifs);
          _api.log().debug("StationDBImp", "Restoring tags...");
          PointObject.restoreTags(ifs);
          _api.log().debug("StationDBImp", "Restoring points...");
          while (true)
          { 
              TrackerPoint st = (TrackerPoint) ifs.readObject(); 
              if (!_map.containsKey(st.getIdent())) {
                  _map.put(st.getIdent(), st);
              }
          }
        }
        catch (EOFException e) { }
        catch (Exception e) {
            _api.log().warn("StationDBImp", "Cannot restore data: "+e);
            _map.clear();
            _routes = new RouteInfo();
        } 
    }
    
    
    /**
     * Garbage collection.
     * Removes expired objects and tries to to a little more
     * agressive JVM garbage collection. 
     */
    private synchronized void garbageCollect()
    {
         Date now = new Date();  
         _api.log().debug("StationDBImp", "Garbage collection...");
         Iterator<TrackerPoint> stn = _map.values().iterator();
         int n=0;
         while (stn.hasNext()) 
         {
             TrackerPoint st = stn.next();
             if (st.expired()) {
                 /* 
                  * If managed and a backing database is present, save it there. 
                  * else keep it in memory (and allow it to be saved to file). 
                  */
                 if ( st.hasTag("MANAGED") && _histData != null)
                    _histData.saveManagedItem(st); 
                    
                 /* If not managed or saved to database, remove it. */
                 if (!st.hasTag("MANAGED") || _histData != null) {
                    /* Remove expired items from remotectl log */
                    if (_api.getRemoteCtl() != null)
                        _api.getRemoteCtl().removeExpired(st.getIdent());
            
                    st.removeAllTags();
                    removeItem(st.getIdent());
                    _routes.removeNode(st.getIdent());
                    n++;
                 }
             } 
             else
                 st.autoTag();
         }
         _api.log().info("StationDBImp", "GC Removed: "+n+" items"); 
         Calendar t = Calendar.getInstance();
         t.add(Calendar.DAY_OF_YEAR, -1);
         _routes.removeOldEdges(t.getTime());
                    
         s_runtime.runFinalization ();
         s_runtime.gc ();
         Thread.currentThread ().yield ();
         _api.log().debug("StationDBImp", "Garbage collection finished");
    }
    
        
    
    private synchronized void checkMoving()
    {
         // FIXME. We do not need to do this for all?? 
         for (TrackerPoint s: _map.values())
            if (s.isChanging(true))
                _hasChanged = true;
    }
    
    
    /**
     * Thread to periodically checkpoint station data to disk, and remove
     * obsolete stations. 
     */   
    public void run()
    {
        long period = 1000 * 60;           // 1 minute
        long periods_gc = 30;              // 30 minutes
           
        while(true) {
           try {
              for (int count=0; count < periods_gc; count++) 
              {
                  Thread.sleep(period); 
                  checkMoving(); 
              }
              garbageCollect();            
              save();
           }
           catch (Exception e)
             {  _api.log().error("StationDBImp", "GC thread: "+e); 
                e.printStackTrace(System.out); }                  
        }  
    }   

}
