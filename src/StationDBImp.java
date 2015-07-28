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
import java.io.*;
import uk.me.jstott.jcoord.*; 
import java.util.concurrent.*;
import java.util.regex.*;



/**
 * In-memory implementation of StationDB.
 * Data is saved to a file. Periodically and when program ends.
 */
public class StationDBImp implements StationDB, StationDB.Hist, Runnable
{
    private SortedMap<String, TrackerPoint> _map = new ConcurrentSkipListMap();
    private String     _file;
    private boolean   _hasChanged = false; 
    private RouteInfo _routes;
    private OwnObjects _ownobj;
    private MessageProcessor _msgProc;
    private StationDB.Hist _histData = null;
    private ServerAPI _api; 

    
    public StationDBImp(ServerAPI api)
    {
        _api = api;
        _file = api.getProperty("stations.file", "stations.dat");
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

    
    public void setHistDB(StationDB.Hist d)
        { _histData = d; }

        
            
    public TrackerPoint getItem(String id, Date t)
    { 
       if (t==null)
          return _map.get(id); 
       else if (_histData !=null) 
          return _histData.getItem(id, t); 
       else 
          return null;
     }
       
       
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
   
   
   
    public int nItems() 
        { return _map.size(); }
        
    public OwnObjects getOwnObjects()
        { return _ownobj; }

    public MessageProcessor getMsgProcessor()
        { return _msgProc; }
    
    public RouteInfo getRoutes()
        { return _routes; }
    
    
    /** 
     * Save (checkpoint) station data to disk file. 
     */
    public synchronized void save()
    {
        if (_hasChanged)
          try {
             System.out.println("*** Saving station data...");
             FileOutputStream fs = new FileOutputStream(_file);
             ObjectOutput ofs = new ObjectOutputStream(fs);
             
             ofs.writeObject(_routes);
             _msgProc.save();
             _ownobj.save(ofs);
             for (TrackerPoint s: _map.values())
                { ofs.writeObject(s); }
           }
           catch (Exception e) {
               System.out.println("*** StationDBImp: cannot save: "+e);
           } 
         _hasChanged = false; 
    }
    
    
    /**
     * Restore station data from disk file. 
     */
    private synchronized void restore()
    {
        try {
          System.out.println("*** Restoring station data...");
          FileInputStream fs = new FileInputStream(_file);
          ObjectInput ifs = new ObjectInputStream(fs);
          
          _routes = (RouteInfo) ifs.readObject();
          _msgProc.restore();
          _ownobj.restore(ifs);
          while (true)
          { 
              TrackerPoint st = (TrackerPoint) ifs.readObject(); 
              if (!_map.containsKey(st.getIdent()))
                  _map.put(st.getIdent(), st);
          }

        }
        catch (EOFException e) { }
        catch (Exception e) {
            System.out.println("*** StationDBImp: cannot restore: "+e);
            _map.clear();
            _routes = new RouteInfo();
        } 
    }
    
    
    /**
     * Garbage collection.
     * Removes expired objects and tries to to a little more
     * agressive JVM garbage collection. 
     */
    public synchronized void garbageCollect()
    {
         Date now = new Date();  
         System.out.println("*** Garbage collection...");
         Iterator<TrackerPoint> stn = _map.values().iterator();
         while (stn.hasNext()) 
         {
             TrackerPoint st = stn.next();
             if (st.expired() && !st.isPersistent() ) 
             {
                System.out.println("        Removing: "+st.getIdent()); 
                removeItem(st.getIdent());
                _routes.removeNode(st.getIdent()); 
             } 
         }
         System.out.println("        Removing old edges from route table");
         Calendar t = Calendar.getInstance();
         t.add(Calendar.DAY_OF_YEAR, -1);
         _routes.removeOldEdges(t.getTime());
         
         System.out.println("        Doing JVM finalization and GC");              
         s_runtime.runFinalization ();
         s_runtime.gc ();
         Thread.currentThread ().yield ();
         System.out.println("        Free memory  = "+s_runtime.freeMemory ());
         System.out.println("*** Garbage collection finished");  
    }
    
    
    
    private synchronized void checkMoving()
    {
         for (TrackerPoint s: _map.values())
            if (s.isChanging())
                _hasChanged = true;
    }
    
    
    public synchronized void removeItem(String id)
    {   
           String[] idd = id.split("@");
           
           if (idd != null && idd.length > 1 && idd[1].equals(_api.getOwnPos().getIdent()))
                 _ownobj.delete(idd[0]);
                 
           _map.remove(id);
           _hasChanged = true; 
    }
        
                
       
    public synchronized Station getStation(String id, Date t)
    { 
         TrackerPoint x = getItem(id, t);
         if (x instanceof Station) return (Station) x;
         else return null;
    }   

    
    public synchronized Station newStation(String id)
    {  
        Station st = new Station(id);
        _map.put(id, st); 
        return st;
    }
        
        
    public synchronized void addPoint(TrackerPoint s)
        { _map.put(s.getIdent(), s); }
        
        
    /** 
     * Create a new APRS object.
     * Note that an object is in this database identified by 'ident@owner'
     */
    public synchronized AprsObject newObject(Station owner, String id)
    {
        AprsObject st = new AprsObject(owner, id);
        _map.put(id+'@'+owner.getIdent(), st); 
        return st;
    }
    
    
    
    /**
     * Deactivate objects having the same name. 
     */
    public synchronized void deactivateSimilarObjects(String id, Station owner)
    {
        Collection<TrackerPoint> dupes =  _map.subMap(id+'@', id+'@'+"zzzzz").values();
        for (TrackerPoint x : dupes)
           if (x instanceof AprsObject && !((AprsObject)x).isTimeless() && 
                  !owner.getIdent().equals(((AprsObject)x).getOwner().getIdent())) {
               System.out.println("*** Object overtaken/deactivated: "+id+"@"+((AprsObject)x).getOwner().getIdent());
               ((AprsObject)x).kill();
           }
    }
    
    /**
     * Return a list of trackerpoints where the ident has the given prefix. 
     * @Param srch Prefix 
     */
    public synchronized List<TrackerPoint> getAllPrefix(String srch)
    {
        LinkedList<TrackerPoint> result = new LinkedList();
        if (srch == null)
           return result;
        srch = srch.toUpperCase(); 
        for (TrackerPoint s: _map.values())
           if (s.getIdent().toUpperCase().startsWith(srch) ) 
               result.add(s);
        return result;  
    }    
        
        
    /**
     * Return a list of trackerpoints where ident or description matches the given expression.
     * If expression is prefixed with "REG:", it is regarded as a regular expression, otherwise
     * it is a simple wildcard expression. 
     * @Param srch Search expression.
     */
    public synchronized List<TrackerPoint> getAll(String srch)
    {
        LinkedList<TrackerPoint> result = new LinkedList();
        if (srch == null)
           return result;
        srch = srch.toUpperCase(); 
        if (srch.matches("REG:.*"))
           srch = srch.substring(4);
        else {
           srch = srch.replaceAll("\\.", Matcher.quoteReplacement("\\."));
           srch = srch.replaceAll("\\*", Matcher.quoteReplacement("(\\S*)"));
        }
        for (TrackerPoint s: _map.values())
           if  (s.getIdent().toUpperCase().matches(srch) ||
               s.getDescr().toUpperCase().matches("(.*\\s+)?\\(?("+srch+")\\)?\\,?(\\s+.*)?") ) 
               result.add(s);
        return result;
    }
    
    
       
       
    /**
     * Simple geographical search. Return list of stations within the rectangle defined by
     * uleft (upper left corner) and lright (lower right corner). The coordinate system
     * should be the same as the visual map. Currently, we assume that the map 
     * is using a UTM projection. This also means that coordinates used here, and 
     * for the map must be in the same UTM zones. 
     */   
    public synchronized List<TrackerPoint>
          search(Reference uleft, Reference lright)
    {
         if (uleft==null || lright==null)
            return getAll(null);
            
         LinkedList<TrackerPoint> result = new LinkedList();
         for (TrackerPoint s: _map.values())
            if (s.isInside(uleft, lright, 0.1, 0.1))
                result.add(s);
        return result;
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
               { System.out.println("*** StationDB GC thread: "+e); e.printStackTrace(System.out);}                  
        }  
    }   

}
