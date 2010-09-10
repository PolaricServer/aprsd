/* 
 * Copyright (C) 2009 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
  


public abstract class AprsPoint implements Serializable
{
    private static long     _nonMovingTime = 1000 * 60 * 5;   
    private static SymTable _symTab        = new SymTable ("symbols");
    private static Notifier _change        = new Notifier();
    protected static StationDB _db         = null;
    
    public static boolean waitChange(long clientid)
       { return waitChange(null, null, clientid); }
       
    public static boolean waitChange(UTMRef uleft, UTMRef lright, long clientid) 
       { return _change.waitSignal(uleft, lright, clientid); } 

    protected static boolean changeOf(String x, String y)
       { return x != y || (x != null && !x.equals(y)); }   
         
    public static void setDB(StationDB db) 
       { _db = db; }   
         
    protected Reference   _position;  
    protected char        _symbol; 
    protected char        _altsym; 
    protected String      _icon; 
    private String        _alias;  
    protected boolean     _changing = false; 
    protected Date        _updated = new Date(), _lastChanged;
    protected boolean     _permanent = false;
    private boolean       _hidelabel = false; 
    private String        _description;    
    
    /**
     * Test if position is inside of the rectangular area defined by uleft (upper left corner)
     * and lright (lower right corner). Assume that uleft and lright are the same
     * UTM zone. 
     */          
    public boolean isInside(UTMRef uleft, UTMRef lright)
    {
         /* FIXME: Add lat zone as well */
        if (_position == null)
           return false;
        try {
           UTMRef ref = _position.toLatLng().toUTMRef(uleft.getLngZone());
           return ( ref.getEasting() >= uleft.getEasting() && ref.getNorthing() >= uleft.getNorthing() &&
                    ref.getEasting() <= lright.getEasting() && ref.getNorthing() <= lright.getNorthing() );
        }
        catch (Exception e) { return false; }
    }
    
    public boolean isInfra() 
       { return false; }
   
    public boolean isEmergency()
       { return (_altsym=='\\' && _symbol=='!'); }
             
             
    public Reference getPosition ()   
       { return _position; } 


    public char getSymbol()
       { return _symbol; }
       
    public char getSymtab()
       { return _altsym;}
       
    public boolean iconIsNull()
       { return _icon == null; }
    
       
    public String getIcon()
    { 
       if (_icon != null)
          return _icon; 
       return _symTab.getIcon(_symbol, _altsym);
    }           
    
    
    public abstract String getIdent();

    public String getDisplayId()
       { return _alias != null ? _alias : getIdent(); }    
    
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


    public String getDescr()
       { return (_description == null ? "" : _description); }
       
       
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
       
                   
    public boolean isPermanent()
       { return _permanent; }
       
       
    public void setPermanent(boolean p)
       { _permanent = p; }
    
    
    public boolean isLabelHidden()
       { return _hidelabel; }
    
    
    public synchronized void setLabelHidden(boolean h)
      { if (_hidelabel != h) {
          _hidelabel = h;
          setChanging(); 
        }
      }
      
      
    public synchronized void setChanging()
    {
         _changing = true;
         _lastChanged = _updated;  
        _change.signal(this); 
    }
           
      
      
    /*
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
        
        
    public abstract void update(Date ts, Reference newpos, int crs, int sp, int alt, 
                                String descr, char sym, char altsym);        
        
    public abstract boolean expired();
    public boolean visible() { return !expired(); }
}
