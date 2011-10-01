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
    private   static SymTable  _symTab        = new SymTable ("symbols");
    private   static Notifier  _change        = new Notifier();
    protected static StationDB _db    = null;
    
    public AprsPoint(Reference p)
      { super(p); }
      
    public static void abortWaiters(boolean retval)
      { _change.abortAll(retval); }
      
    public static boolean waitChange(long clientid)
       { return waitChange(null, null, clientid); }
       
    public static boolean waitChange(UTMRef uleft, UTMRef lright, long clientid) 
       { return _change.waitSignal(uleft, lright, clientid); } 

    protected static boolean changeOf(String x, String y)
       { return x != y || (x != null && !x.equals(y)); }   
         
    public static void setDB(StationDB db) 
       { _db = db; }   
         
    protected char        _symbol; 
    protected char        _altsym; 
    private   String      _alias;  
    protected boolean     _changing = false; 
    protected Date        _updated = new Date(), _lastChanged;
    protected boolean     _persistent = false;
    private   boolean     _hidelabel = false;  
    
    
    public boolean isInfra() 
       { return false; }
   
    public boolean isEmergency()
       { return (_altsym=='\\' && _symbol=='!'); }
             

    public char getSymbol()
       { return _symbol; }
       
    public char getSymtab()
       { return _altsym;}
    
       
    public String getIcon()
    { 
       if (_icon != null)
          return _icon; 
       return _symTab.getIcon(_symbol, _altsym);
    }           
    
    
    public abstract String getIdent();

    public String getDisplayId(boolean usealias)
       { return (usealias && _alias != null) ? _alias : getIdent().replaceFirst("@.*",""); }    
    
    public String getDisplayId()
       { return getDisplayId(true); }
       
       
    public String getAlias()
       { return _alias; }
               
        
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
         _lastChanged = _updated;  
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
          if (_changing && _updated.getTime() > _lastChanged.getTime() + _nonMovingTime) {
               _change.signal(this);
               return (_changing = false);
          }
          return _changing; 
    }
    
    
    
    protected void updatePosition(Date ts, Reference newpos)
    {
         if (_position == null)
         setChanging();
         _updated = (ts == null ? new Date() : ts);   
         _position = newpos;
    }
        
        
    public abstract void update(Date ts, AprsHandler.PosData pd, String descr, String pathinfo);        
        
    public abstract boolean expired();
    public boolean visible() { return !expired(); }
}
