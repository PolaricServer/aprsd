/* 
 * Copyright (C) 2002 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
 
  
public class StationDBImp implements StationDB, Runnable
{
    private SortedMap<String, AprsPoint> _map = new ConcurrentSkipListMap();
    private String _file;
    private boolean _hasChanged = false; 
    private RouteInfo _routes;
    private OwnObjects _ownobj;

    
    public StationDBImp(Properties config)
    {
        _file = config.getProperty("stations.file", "stations.dat");
        _ownobj = new OwnObjects(config, this); 
        restore();
        Thread t = new Thread(this, "StationDBImp");
        t.start(); 
    }
    
    
    private static final Runtime s_runtime = Runtime.getRuntime ();
    public static long usedMemory ()
      { return s_runtime.totalMemory () - s_runtime.freeMemory (); }
    


    public int nItems() 
        { return _map.size(); }
    
    
    public OwnObjects getOwnObjects()
        { return _ownobj; }

    
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
             _ownobj.save(ofs);
             for (AprsPoint s: _map.values())
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
          _ownobj.restore(ifs);
          while (true)
          { 
              AprsPoint st = (AprsPoint) ifs.readObject(); 
              System.out.println("        Read object: "+st.getIdent() );
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
         System.out.println("        Memory usage = "+usedMemory());
         Iterator<AprsPoint> stn = _map.values().iterator();
         while (stn.hasNext()) 
         {
             AprsPoint st = stn.next();
             if (st.expired() && !st.isPermanent() ) 
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
         System.out.println("        Memory usage = "+usedMemory());
         System.out.println("        Free memory  = "+s_runtime.freeMemory ());
         System.out.println("*** Garbage collection finished");  
    }
    
    
    
    private synchronized void checkMoving()
    {
         for (AprsPoint s: _map.values())
            if (s.isChanging())
                _hasChanged = true;
    }
    
    
    public synchronized void removeItem(String id)
    {   
           _map.remove(id);
           _hasChanged = true; 
    }
        
        
        
    public AprsPoint getItem(String id)
       { return _map.get(id); }
       
       
       
    public synchronized Station getStation(String id)
    { 
         AprsPoint x = getItem(id);
         if (x instanceof Station) return (Station) x;
         else return null;
    }   

    
    public synchronized Station newStation(String id)
    {  
        Station st = new Station(id);
        _map.put(id, st); 
        return st;
    }
    
    
    public AprsObject newObject(Station owner, String id)
    {
        AprsObject st = new AprsObject(owner, id);
        _map.put(id, st); 
        return st;
    }
    
    
        
    public synchronized List<AprsPoint> getAll()
    {
        LinkedList<AprsPoint> result = new LinkedList();
        for (AprsPoint s: _map.values())
           result.add(s);
        return result;
    }
       
       
    /**
     * Simple search. Return list of stations within the rectangle defined by
     * uleft (upper left corner) and lright (lower right corner). The coordinate system
     * should be the same as the visual map. Currently, we assume that the map 
     * is using a UTM projection. This also means that coordinates used here, and 
     * for the map must be in the same UTM zones. 
     */   
    public synchronized List<AprsPoint>
          search(UTMRef uleft, UTMRef lright)
    {
         if (uleft==null || lright==null)
            return getAll();
            
         LinkedList<AprsPoint> result = new LinkedList();
         for (AprsPoint s: _map.values())
            if (s.isInside(uleft, lright))
                result.add(s);
        return result;
    }
    
    
          
    /**
     * Search. Return list of stations within the polygon (rectangle) defined by
     *, p1, p2, p3 and p4. 
     */               
    public synchronized List<AprsPoint>
          search(Reference p1, Reference p2, Reference p3, Reference p4)
    {
        LinkedList<AprsPoint> result = new LinkedList();
        LatLng pp1 = p1.toLatLng();
        LatLng pp2 = p2.toLatLng();
        LatLng pp3 = p3.toLatLng();
        LatLng pp4 = p4.toLatLng();
     
        for (AprsPoint s: _map.values())
        {
           LatLng ss = s.getPosition().toLatLng();
           
           if 
           (((ss.getLng() - pp1.getLng())*(pp2.getLat() - pp1.getLat())  -  (ss.getLat() - pp1.getLat())*(pp2.getLng() - pp1.getLng()) > 0)  
           &&
           ((ss.getLng() - pp2.getLng())*(pp3.getLat() - pp2.getLat())  -  (ss.getLat() - pp2.getLat())*(pp3.getLng() - pp2.getLng()) > 0)
           && 
           ((ss.getLng() - pp3.getLng())*(pp4.getLat() - pp3.getLat())  -  (ss.getLat() - pp3.getLat())*(pp4.getLng() - pp3.getLng()) > 0)
           &&  
           ((ss.getLng() - pp4.getLng())*(pp1.getLat() - pp4.getLat())  -  (ss.getLat() - pp4.getLat())*(pp1.getLng() - pp4.getLng()) > 0))
              result.add(s);
        }
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
