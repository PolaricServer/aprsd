 
/* 
 * Copyright (C) 2016-20 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import uk.me.jstott.jcoord.*; 
import java.util.*;
import java.io.Serializable;
import no.polaric.aprsd.filter.ViewFilter; 
import java.time.format.DateTimeFormatter;  


/**
 * Geographical point. Movable, with label, etc..
 */
public abstract class TrackerPoint extends PointObject implements Serializable, Cloneable
{
    private   static long        _nonMovingTime = 1000 * 60 * 5; 
    private   static Notifier    _change;
    protected static ServerAPI   _api = null;
    protected static StationDB   _db  = null;
    protected static ColourTable _colTab = null;
    private   static long        _posUpdates = 0;
    
    
    public static void setNotifier(Notifier n)
        { _change = n; }
    
    public static void setApi(ServerAPI api) { 
       
       _api = api; 
       _db = _api.getDB(); 
       _colTab = new ColourTable (api, System.getProperties().getProperty("confdir", ".")+"/trailcolours");
       AprsPoint.setApi(api);
    }
       
    public static ServerAPI getApi() 
        { return _api; }
        
        
    /*
     * Static variables and functions to control expire timeouts 
     * and notifications
     */
    private static long _expiretime    = 1000 * 60 * 60 * 2;    // Default: 2 hour
        
    public static long getExpiretime()
       { return _expiretime; }
         
    public static void setExpiretime(long exp)
       { _expiretime = exp; }
  
    public static long getPosUpdates()
       { return _posUpdates; }
       
       
      
    protected Trail     _trail = new Trail(); 
    protected String[]  _trailcolor = new String[] {"dddddd", "ff0000"};   
    
    private int        _course = -1;
    private int        _speed = -1; 
    private int        _altitude = -1;
    
    private boolean    _changing = false; 
    protected Date     _updated = new Date();  
    private Date       _lastChanged;        
    protected boolean  _expired = false; 
            
    private   String   _alias;    
    private   boolean  _hidelabel = false; 
    protected boolean  _persistent = false; 
    private   String   _user; 
  
  
  
    public TrackerPoint(Reference p)
      { super(p); }
    
    
    public void autoTag()
    {
       if (ViewFilter.getTagRules() != null) 
           ViewFilter.getTagRules().apply(this);
    }
       
   
    /************** Position, trail, Course, speed and altitude ***************/
        
        
    /**
     * Update position of the object. Note that this does not add to the trail. This must
     * be done explicitly by a subclass by calling saveToTrail() BEFORE calling updatePosition! 
     *
     * @param ts Timestamp (time of update).
     * @param newpos Position coordinates.
     */    

    public synchronized void updatePosition(Date ts, Reference newpos)
    { 
         if (_position == null)
             setChanging();
         setUpdated(ts == null ? new Date() : ts);
         _position = newpos;
         _posUpdates++;
    }



    private boolean _fastmove = false;

    /**
     * Save position to trail if there is a significant change. 
     * If we want a trail, this should be done before updatePosition
     *
     * @param ts timestamp of the NEXT position.
     * @param newpos the NEXT position.
     * @param pathinfo optional extra information. E.g. path info.
     * @return true to indicate if position is ok and can be saved to realtime point. 
     */

    public synchronized boolean saveToTrail(Date ts, Reference newpos, int speed, int course, String pathinfo) 
    {         
        /*
         * If timestamp is in the future, do nothing.
         */
        if (ts.getTime() > (new Date()).getTime() + 10000)
            return false;
        
        if (_position == null || newpos == null) 
            return (newpos != null);
        

        /* Time distance in seconds */
        long tdistance = (ts.getTime() - _updated.getTime()) / 1000;          
           
        /* Downsample. Time resolution is 10 seconds or more */
        if (tdistance < 5 && tdistance > -5)
             return false; 
        
        /* If moving incredibly fast (i.e. over 500 km/h) ignore, but only 
         * first time. Second time, clear trail. 
         */
        if ( tdistance > 0 && distance(newpos)/tdistance > (500 * 0.27778)) {
            if (!_fastmove) {
               _fastmove = true; 
               return false; 
            }
            _trail.clear(); 
        }
        _fastmove = false;     
    
        /* If report is older than the previous one, just save it in the 
         * history 
         */
        if (tdistance < 0 && _trail.add(ts, newpos, speed, course, pathinfo)) {
            setChanging(); 
            return false;
        }

        
        if (getSpeed() == -1)
            setSpeed((int) Math.round(3.6 * (distance(newpos) / tdistance)));
            
            
        /*
         * If object has moved, indicate that object is moving/changing, 
         * save the previous position.
         */
        if (distance(newpos) > Trail.mindist && 
            _trail.add(_updated, _position, getSpeed(), getCourse(), pathinfo)) 
        {
           if (_trail.length() == 1)
               _trailcolor = _colTab.nextColour();
           if (_db.getRoutes() != null)
              _db.getRoutes().removeOldEdges(getIdent(), _trail.oldestPoint());
           setChanging();   
        }
        return true;
    }
    
    
    
    /**
     * Return true if point or parts of its trail is inside the given area. 
     *
     * @param uleft upper left corner of area.
     * @param lright lower right corner of area. 
     */
    @Override public boolean isInside(Reference uleft, Reference lright) 
    {
       if (super.isInside(uleft, lright))
          return true;
       if (_trail == null)
          return false;    
       
       /* If part of trace is inside displayed area and the station itself is within a 
        * certain distance from displayed area 
        */
       if (super.isInside(uleft, lright, 1, 1))
        for (TPoint it : _trail) 
          if (it.isInside(uleft, lright))
             return true;
         
       return false;
    }          
    
    
    public Trail getTrail() 
        { return _trail; }       
        
    public int getTrailLen()
        { return _trail.length(); }
    
    public synchronized Trail.Item getHItem()
       { return new Trail.Item(_updated, _position, getSpeed(), getCourse(), ""); }
           
    public String[] getTrailColor()
       { return _trailcolor;}

    public synchronized void nextTrailColor()
       { _trailcolor = _colTab.nextColour(); setChanging(); }  
       
    public int getSpeed ()
       { return _speed; }
       
    public int getAvgSpeed ()
       { return _trail.getAvgSpeed(); }
       
    public int getMaxSpeed ()
       { return _trail.getMaxSpeed(); }

    public void setSpeed (int s)
       { _speed = s; }
       
    public int getCourse ()
       { return _course; }
       
    public void setCourse(int c)
       { _course = c; }
       
    public int getAltitude()
       { return _altitude; }
    
    protected void setAltitude(int a)
       { _altitude = a; }
    
    
    
    /************** Methods related to tracking of changes ***************/
          
    /** 
     * Abort all waiters. See Notifier class. 
     * @param retval Return value: true=ok, false=was aborted.
     */
    public static void abortWaiters(boolean retval)
      { /* _change.abortAll(retval); */ }
     
     /* FIXME: Remove this. */ 
      
      
    /**
     * Wait for change of state. Blocks the calling thread until there is 
     * a change in at least one object. 
     *
     * @param clientid Unique numbe for calling thread.
     * @return true=ok, false=was aborted.
     */
    public static boolean waitChange(long clientid)
       { return waitChange(null, null, clientid); }
    /* FIXME: Remove this */   
       
       
     /**
     * Wait for change of state within an area. Blocks calling thread until any object
     * inside the specified area changes. 
     *
     * @param uleft Upper left corner of the rectangular area of interest.
     * @param lright Lower right corner of the rectangular area of interest.
     * @param clientid Unique numbe for calling thread.
     * @return true=ok, false=was aborted.
     */
    public static boolean waitChange(Reference uleft, Reference lright, long clientid) 
       { return false; /* _change.waitSignal(uleft, lright, clientid); */ } 
    /* FIXME: Remove this */
       
     
    /** Return time of last significant change in position, etc.. */
    public Date getLastChanged()
       { return _lastChanged; }
    
    
    
    /** Return time when last updated */              
    public Date getUpdated()
       { return _updated; }
    
    
    
    /** Set time when last updated. */   
    public synchronized void setUpdated(Date t)
       { _updated = t; }
       
    
    /** Reset trail, etc.. */
    public synchronized void reset()
    {      
       _trail = new Trail();
       _updated  = new Date(0);
       setChanging();
    }
    
          
        
    /**
     * Called to indicate that something has changed.
     */
    public synchronized void setChanging()
    {
         _changing = true;
         _lastChanged = new Date();  
         if ( _change!= null ) 
            _change.signal(this); 
    }
           
   
   
   /**
    * Return true and signal a change if position etc. is updated recently.
    */
    public boolean isChanging() 
       { return isChanging(false); }
       
      
    /**
     * Return true and signal a change if position etc. is updated recently.
     * This must be called periodically to ensure that asyncronous waiters
     * are updated when a station has stopped updating. 
     */ 
    public synchronized boolean isChanging(boolean signal) 
    {
          /* 
           * When station has not changed position or other properties for xx minutes, 
           * it is regarded as not moving. 
           */
          if (_changing && (new Date()).getTime() > _lastChanged.getTime() + _nonMovingTime) {
               if (signal && _change != null)
                  _change.signal(this);
               return (_changing = false);
          }
          if (!_changing) 
               checkForChanges(); 
          return _changing; 
    }
     
                
    protected void checkForChanges()
    { 
        if (_trail.itemsExpired()) 
           setChanging(); 
    }     
     
     
    
    
    
    /************ Methods related to descriptions, icons, labels and aliases *************/
    
    
    protected static boolean changeOf(String x, String y)
       { return x != y || (x != null && !x.equals(y)); }  
       
       
    public String getAlias()
       { return _alias; }
       
    
    public synchronized boolean setAlias(String a)
    {  
      if (changeOf(_alias, a)) {
        _alias = a;
         setChanging();
         return true;
      }
      return false;
    }
    
    
    /**
     * Change the icon explicitly.
     * @param a file path of the icon. 
     * @return true if there was a change. 
     */
    public synchronized boolean setIcon(String a)
    {  
      if (changeOf(_icon, a)) {
        _icon = a;
        setChanging();
        return true;
      }
      return false;
    }
    
  
    /* FIXME: do override logic here */
    public abstract String getIcon(boolean override);
    
    
    @Override public String getIcon()
       { return getIcon(true); }
    
    
    public boolean iconOverride()
       { return _icon != null; }
    
    
    
    /** 
     * Get identifier for display on map. 
     * 
     * @param usealias If true, return alias instead of ident, if set. 
     * @return ident or alias.
     */
    public String getDisplayId(boolean usealias)
       { return (usealias && _alias != null) ? _alias : _getDisplayId(); }    
    
    protected String _getDisplayId()
       { return  getIdent().replaceFirst("@.*",""); }
       
       
    /** 
     * Get identifier for display on map. 
     * @return ident or alias. 
     */
    public String getDisplayId()
       { return getDisplayId(true); }
               
               
    /** Return true if label is hidden (not displayed on map). */           
    public boolean isLabelHidden()
       { return _hidelabel; }
    
    
    /** Hide label (do not display it on map). */
    public synchronized void setLabelHidden(boolean h)
      { if (_hidelabel != h) {
          _hidelabel = h;
          setChanging(); 
        }
      }
     
     
    /** Set description. */
    public synchronized void setDescr(String d)
    {   
        if (d != null) 
        {
           if (_description==null || !_description.equals(d))
               setChanging(); 
           _description = d;  
        }
    }
    
   
   
   /************ Methods related to persistence and expiry *************/
    
    public String getUser()
       { return _user; }
       
    public void setUser(String user)
       { _user = user; }
                       
    public boolean isPersistent()
       { return _persistent; }
     
     
    /**
     * Set object to be persistent. I.e. it is not deleted by garbage collection
     * even if expired, OR it may be saved to a database (if supported).
     */
    public void setPersistent(boolean p, String user, boolean dontsave) {
        /* FIXME: Only owner should have permission to change ? */
        if (getUser() == null)
           setUser(user);
        _persistent = p; 
        _db.saveItem(this);
    }
    
    public void setPersistent(boolean p, String user)
       { setPersistent(p, user, true); }
       
     
            
    /** 
     * Return true if object has expired. 
     */    
    public boolean expired() {
        if (_expired) 
            return true;
        return _expired = _expired();
    }
    
    protected boolean _expired() {
        Date now = new Date();
        return (now.getTime() > _updated.getTime() + _expiretime);    
    }
    
    
    
    /**
     * Return false if object should not be visible on maps. If it has expired.
     */
    public boolean visible() { return !expired(); }
    
    
    
    
}
