/* 
 * Copyright (C) 2016-2023 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import no.polaric.aprsd.filter.*;

import com.github.davidmoten.rtree2.*;
import static com.github.davidmoten.rtree2.geometry.Geometries.point;
import com.github.davidmoten.rtree2.geometry.Point;
import com.github.davidmoten.rtree2.geometry.Geometries;
import static com.github.davidmoten.rtree2.Entries.entry;


/**
 * In-memory implementation of StationDB.
 * Data is saved to a file. Periodically and when program ends.
 */
public class StationDBImp extends StationDBBase implements StationDB, Runnable
{
    private SortedMap<String, TrackerPoint> _map = new ConcurrentSkipListMap<String,TrackerPoint>();
    private String     _file;
    private String     _stnsave;
    private boolean    _kill = false; 
    RTree<TrackerPoint, Point> _geoindex = RTree.star().maxChildren(6).create();
    
    
    public StationDBImp(ServerAPI api)
    {
        super(api);
        _file = api.getProperty("stations.file", "stations.dat");
        _stnsave = api.getProperty("stations.save", ".*");
        if (_file.charAt(0) != '/')
           _file = System.getProperties().getProperty("datadir", ".")+"/"+_file;   
        restore();
        Thread t = new Thread(this, "StationDBImp");
        t.start(); 
    }
    
        
    
    public void kill() {
        _kill = true; 
    }
    
    
    /***********************************
     * Item methods (get/add, remove)
     ***********************************/
    
    /** 
     * Return the number of realtime items. 
     */   
    public int nItems() 
        { return _map.size(); }
        
        
     
    @Override protected TrackerPoint _getRtItem(String id) {
        return _map.get(id);
    }
        
     
    @Override protected synchronized void _addRtItem(TrackerPoint s) {
        if (s == null || s.getPosition() == null)
            return;
        
        if ( s != null && s.getIdent() != null) 
            _map.remove(s.getIdent());
        _map.put(s.getIdent(), s);

        LatLng pos = s.getPosition().toLatLng();
        var point = Geometries.pointGeographic(pos.getLng(), pos.getLat());
        _geoindex = _geoindex.delete(s, point, true)
                             .add(s, point);
    }
    
    
    /**
     * Update an existing tracker point. 
     * @param s existing station
     */
    public synchronized void updateItem(TrackerPoint s, LatLng prevpos) {
        /* Remove existing point in r-tree index */
        _removeGItem(s, prevpos);
        _map.remove(s.getIdent());
        
        /* Add it with new position */
        _addRtItem(s);
    }

    
    private void _removeGItem(TrackerPoint s, LatLng pos) {
        if (s != null && pos != null) {
            var point = Geometries.pointGeographic(pos.getLng(), pos.getLat());
            _geoindex = _geoindex.delete(s, point, true);
        }
    }
    
    
    
    @Override protected synchronized void _removeRtItem(String id) {
        TrackerPoint pt = _getRtItem(id);
        if (pt.getPosition() != null)
            _removeGItem(pt, pt.getPosition().toLatLng());
        _map.remove(id);
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
           return new LinkedList<TrackerPoint>();
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
        LinkedList<TrackerPoint> result = new LinkedList<TrackerPoint>();
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
        search(Reference uleft, Reference lright, RuleSet filter)
    { 
        LatLng ul = uleft.toLatLng();
        LatLng lr = lright.toLatLng();
        
        List<TrackerPoint> res = new ArrayList<TrackerPoint>(2000);
        Iterable<Entry<TrackerPoint, Point>> entries =
            _geoindex.search(Geometries.rectangleGeographic(ul.getLng(), lr.getLat(), lr.getLng(), ul.getLat()));  

        for (Entry<TrackerPoint, Point> pt : entries) 
            res.add(pt.value());
            
        return res;
    }
    
    
    
    
    
    /****************************
     * Other methods
     ****************************/
    
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
             ObjectOutputStream ofs = new ObjectOutputStream(fs);
             
             ofs.writeObject(_routes);
             _api.getMsgProcessor().save();
             _ownobj.save(ofs);
             PointObject.saveTags(ofs);
             ofs.writeInt(_map.size());
             for (TrackerPoint s: _map.values()) { 
                if (s.getIdent().matches("("+_stnsave+")|.*\\@("+_stnsave+")"))
                    ofs.writeObject(s); 
             }
             ofs.close();
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
        ObjectInputStream ifs = null; 
        try {
          _api.log().info("StationDBImp", "Restoring point data...");
          FileInputStream fs = new FileInputStream(_file);
          ifs = new ObjectInputStream(fs);
          
          _api.log().debug("StationDBImp", "Restoring routes...");
          _routes = (RouteInfo) ifs.readObject();
          _api.log().debug("StationDBImp", "Restoring msgproc and ownobj...");    
          _api.getMsgProcessor().restore();
          _ownobj.restore(ifs);
          _api.log().debug("StationDBImp", "Restoring tags...");
          PointObject.restoreTags(ifs);
          _api.log().debug("StationDBImp", "Restoring points...");
          int size = ifs.readInt();
          for (int i=0; i<size; i++)
          { 
              TrackerPoint st = (TrackerPoint) ifs.readObject(); 
             _addRtItem(st);
          }
          ifs.close();
        }
        catch (Exception e) {
            _api.log().warn("StationDBImp", "Cannot restore data: "+e);
            _map.clear();
            _routes = new RouteInfo();
        } 
    }
    
    
    
    private synchronized void rebuildIndex() {
        _geoindex = RTree.star().maxChildren(6).create();
        for (TrackerPoint s: _map.values()) { 
            LatLng pos = s.getPosition().toLatLng();
            var point = Geometries.pointGeographic(pos.getLng(), pos.getLat());
            _geoindex = _geoindex.add(s, point);
        }
    }
    
    
    
    /**
     * Garbage collection.
     * Removes expired objects and tries to to a little more
     * agressive JVM garbage collection. 
     */
    private void garbageCollect()
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
                
                 /* If not managed, remove it. */
                 if (!st.hasTag("MANAGED")) {
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
                    
         _api.log().info("StationDBImp", "rebuilding index");
         rebuildIndex();
         
         s_runtime.runFinalization ();
         s_runtime.gc ();
         Thread.currentThread ().yield ();
         _api.log().debug("StationDBImp", "Garbage collection finished");
    }
    
        
    
    private void checkMoving()
    {
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
        long period = 1000 * 60 * 1;       // 1 minutes
        long periods_gc = 15;              // 15 minutes
        
        while(true) {
           if (_kill) 
               break;
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
                e.printStackTrace(System.out);}                  
        }  
        _api.log().error("StationDBImp", "GC thread finishing.. "); 
    }   

}
