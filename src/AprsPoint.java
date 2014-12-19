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
import uk.me.jstott.jcoord.*; 
import java.util.*;
import java.io.Serializable;
  

/**
 * APRS geographical point.
 */
public abstract class AprsPoint extends PointObject implements Serializable, Cloneable
{
    private   static long      _nonMovingTime = 1000 * 60 * 5;   
    private   static SymTable  _symTab        = new SymTable (System.getProperties().getProperty("confdir", ".")+"/symbols");
    private   static Notifier  _change        = new Notifier();
    protected static ServerAPI _api = null;
    protected static StationDB _db  = null; 


    public AprsPoint(Reference p)
      { super(p); }
      
      
    /** 
     * Abort all waiters. See Notifier class. 
     * @param retval Return value: true=ok, false=was aborted.
     */
    public static void abortWaiters(boolean retval)
      { _change.abortAll(retval); }
      
      
    /**
     * Wait for change of state. Blocks calling thread until any object changes. 
     *
     * @param clientid Unique numbe for calling thread.
     * @return true=ok, false=was aborted.
     */
    public static boolean waitChange(long clientid)
       { return waitChange(null, null, clientid); }
       
       
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
       { return _change.waitSignal(uleft, lright, clientid); } 

       
    protected static boolean changeOf(String x, String y)
       { return x != y || (x != null && !x.equals(y)); }   
       
       
    public static void setApi(ServerAPI api)
       { _api = api; _db = _api.getDB(); }   
       
       
    protected char        _symbol; 
    protected char        _altsym; 
    protected int         _ambiguity = 0;
    private   String      _alias;  
    protected boolean     _changing = false; 
    protected Date        _updated = new Date(), _lastChanged;
    protected boolean     _persistent = false;
    private   boolean     _hidelabel = false;  
    
    
    public Date getLastChanged()
       { return _lastChanged; }
       
    public boolean isInfra() 
       { return false; }
   
    public boolean isEmergency()
       { return (_altsym=='\\' && _symbol=='!'); }
             

    public char getSymbol()
       { return _symbol; }
       
    public char getSymtab()
       { return _altsym;}
       
    public void setSymbol(char s)
       { _symbol = s; }
   
    public void setSymtab(char s)
       { _altsym = s; }
       
    public String getIcon(boolean override)
    { 
       if (override && _icon != null)
          return _icon; 
       return _symTab.getIcon(_symbol, _altsym);
    }           
    
    @Override public String getIcon()
       { return getIcon(true); }


    public int getAmbiguity()
       { return _ambiguity; }
       
       
    public abstract String getIdent();
       
       
    public String getAlias()
       { return _alias; }
       
    
    /** 
     * Get identifier for display on map. 
     * returns callsign or alias. 
     * @param usealias If true, return alias instead of callsign if set. 
     */
    public String getDisplayId(boolean usealias)
       { return (usealias && _alias != null) ? _alias : getIdent().replaceFirst("@.*",""); }    
    
    
    /** 
     * Get identifier for display on map. 
     * returns callsign or alias. 
     */
    public String getDisplayId()
       { return getDisplayId(true); }
               
        
    public synchronized void reset()
    {
       _updated  = new Date(0);
       setChanging();
    }
    
    public synchronized boolean setAlias(String a)
    {  
      if (changeOf(_alias, a)) {
        _alias = a;
         setChanging();
         return true;
      }
      return false;
    }
               
               
    public synchronized boolean setIcon(String a)
    {  
      if (changeOf(_icon, a)) {
        _icon = a;
        setChanging();
        return true;
      }
      return false;
    }
       
       
    public synchronized void setDescr(String d)
    {   
        if (d != null) 
        {
           if ((_description==null && d!=null) || (_description != null && !_description.equals(d)))
               setChanging(); 
           _description = d;  
        }
    }
              
      
    public Date getUpdated()
       { return _updated; }
       
                   
    public boolean isPersistent()
       { return _persistent; }
       
    /**
     * Set object to be persistent. I.e. it is not deleted by garbage collection
     * even if expired. 
     */
    public void setPersistent(boolean p)
       { _persistent = p; }
    
    
    public boolean isLabelHidden()
       { return _hidelabel; }
    
    
    public synchronized void setLabelHidden(boolean h)
      { if (_hidelabel != h) {
          _hidelabel = h;
          setChanging(); 
        }
      }
      
    
    /**
     * Called to indicate that something has changed.
     */
    public synchronized void setChanging()
    {
         _changing = true;
         _lastChanged = new Date();  
         _change.signal(this); 
    }
           
      
      
    /**
     * Return true and signal a change if position etc. is updated recently.
     * This must also be called periodically to ensure that asyncronous waiters
     * are updated when a station has stopped updating. 
     */ 
    public synchronized boolean isChanging() 
    {
          /* 
           * When station has not changed position or other properties for xx minutes, 
           * it is regarded as not moving. 
           */
          if (_changing && (new Date()).getTime() > _lastChanged.getTime() + _nonMovingTime) {
               _change.signal(this);
               return (_changing = false);
          }
          if (!_changing) 
               checkForChanges(); 
          return _changing; 
    }
    
        
    protected void checkForChanges() {} 
    
    
    /**
     * Update position of the object. 
     *
     * @param ts Timestamp (time of update). If null, the object will be timeless/permanent.
     * @param newpos Position coordinates.
     * @param ambiguity 
     */    
    public void updatePosition(Date ts, Reference newpos, int ambiguity)
    {
         if (_position == null)
             setChanging();
         _updated = (ts == null ? new Date() : ts);   
         _position = newpos;
         _ambiguity = ambiguity;
    }
        
        
    /**
     * Manual update of position. 
     *
     * @param ts Timestamp (time of update). If null, the object will be timeless/permanent.
     * @param pd Position data (position, symbol, ambiguity, etc..)
     * @param descr Comment field. 
     * @param path Digipeater path of containing packet. 
     */    
    public abstract void update(Date ts, AprsHandler.PosData pd, String descr, String path);        
        
        
    /** 
     * Return true if object has expired. 
     */    
    public abstract boolean expired();
    
   
    /**
     * Return false if object should not be visible on maps. If it has expired.
     */
    public boolean visible() { return !expired(); }
}
