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
  
  
  
public class Station extends AprsPoint implements Serializable
{

public static class Status implements Serializable
{
    public Date time;
    public String text;
    public Status(Date t, String txt)
      { time = t; text = txt; }
}


    /*
     * Static variables and functions to control expire timeouts 
     * and notifications
     */
    private static long _expiretime    = 1000 * 60 * 60;    // Default: 1 hour
    private static ColourTable _colTab = new ColourTable ("trailcolours");
        
    public static long getExpiretime()
       { return _expiretime; }
         
    public static void setExpiretime(long exp)
       { _expiretime = exp; }


    /*
     * Attributes of a station record (APRS data)
     */
    private String      _callsign; 
    private Status      _status;
    private int         _course;
    private int         _speed; 
    private int         _altitude;
       
    private History     _history = new History(); 

    
    /* 
     * Other variables (presentation, storage, etc.)
     */

    private String[]    _trailcolor = new String[] {"dddddd", "ff0000"};
    private boolean     _autotrail = true;
    private boolean     _expired = false; 
    private int         _report_ignored = 0;
    private boolean     _igate, _wdigi;
    private Date        _infra_updated = null;
       
       
    public Station(String id)
       { super(null); _callsign = id; }
        

    public Set<String> getTrafficFrom()
       {  return _db.getRoutes().getToEdges(getIdent()); }
       
                       
    public Set<String> getTrafficTo()
       { return _db.getRoutes().getFromEdges(getIdent());}
              
              
    public boolean isInfra()
       { return getTrafficFrom() != null && !getTrafficFrom().isEmpty(); }
       
       
    /**
     * Reset infrastructure settings if older than 24 hours 
     */
    private void expireInfra()
    {
        Date now = new Date();
        if (_infra_updated != null && 
            _infra_updated.getTime() + 1000*60*60*24 < now.getTime())
          _igate = _wdigi = false; 
    } 
    
    public boolean isIgate()
       { expireInfra(); 
         return _igate; }
       
       
    public void setIgate(boolean x)
       { _infra_updated = new Date(); 
         _igate = x; }
       
       
    public boolean isWideDigi()
       { expireInfra(); 
         return _wdigi; }
       
       
    public void setWideDigi(boolean x)
       { _infra_updated = new Date(); 
         _wdigi = x; }       
    
    
    public synchronized History.Item getHItem()
       { return new History.Item(_updated, _position, _speed, _course, ""); }
       
       
    public String getIdent()
       { return _callsign; }
       
    

    public synchronized void setStatus(Date t, String txt)
    {
        if (t==null)  
           t = new Date(); 
        _status = new Status(t, txt);
    }
    
    /* Vi kan kanskje legge inn en sjekk om statusrapporten er for gammel */
    public Status getStatus()
        { return _status; }
    
    
    public synchronized void reset()
    {  
        _history = new History();
        _db.getRoutes().removeNode(this.getIdent());
        super.reset(); 
    }
          
    
    public synchronized History getHistory() 
        { return _history; }        
      
   
    public boolean isAutoTrail()
       { return _autotrail; }  
    
       
    public String[] getTrailColor()
       { return _trailcolor;}


    public void nextTrailColor()
       { _trailcolor = _colTab.nextColour(); setChanging(); }

       
    public int getSpeed ()
       { return _speed; }
    
       
    public int getCourse ()
       { return _course; }
    
       
    public int getAltitude()
       { return _altitude; }
    

   
    public boolean isInside(UTMRef uleft, UTMRef lright) 
    {
       if (super.isInside(uleft, lright))
          return true;
       if (_history == null)
          return false;    
       
       /* If part of trace is inside displayed area and the station itself is within a 
        * certain distance from displayed area 
        */
       if (super.isInside(uleft, lright, 1, 1))
        for (History.Item it : _history) 
          if (it.isInside(uleft, lright))
             return true;
         
       return false;
    }
     
     
     
    public synchronized void setUpdated(Date ts)
      { _updated = ts; _expired = false; }
      
      
        
    public synchronized void update(Date ts, Reference newpos, int crs, int sp, int alt, 
                                    String descr, char sym, char altsym, String pathinfo)
    { 
        if (_position != null && _updated != null)
        { 
           /* Distance in meters */
           long distance = Math.round(_position.toLatLng().distance(newpos.toLatLng()) * 1000); 
           
           if (ts != null)
           {
              /* Time distance in seconds */
              long tdistance = (ts.getTime() - _updated.getTime()) / 1000;          
                            
              /*
               * If distance/time implies a speed more than a certain limit (500km/h), 
               * ignore the report. But not more than 3 times. Clear history if
               * ignored 3 times. 
               * FIXME: speed limit should be configurable.
               */
              if ( _report_ignored < 2 && tdistance > 0 && 
                    distance/tdistance > (500 * 0.27778)) 
              {
                 System.out.println("*** Ignore report moving beyond speed limit (500km/h)");
                 _report_ignored++;
                 return;
              }
              if (_report_ignored >= 2) {
                 _history.clear();
                 _db.getRoutes().removeOldEdges(getIdent(), ts);
                 distance = 0;
              }
              
              /* If report is older than the previous one, just save it in the 
               * history 
               */
               if (tdistance < 0) {
                   _history.add(ts, newpos, sp, crs, pathinfo);
                   System.out.println("*** Old report - update trail");
                   setChanging(); 
                   return;
               }            
           }            
           _report_ignored = 0;
                       
           
           /*
            * If distance is more than a certain threshold, indicate that object is moving/changing, 
            * save the previous position.
            */
           if ( distance > 10)  // Distance threshold. FIXME: Should be configurable
           {   
               if (getHistory().isEmpty() && _autotrail)
                   _trailcolor = _colTab.nextColour();
               _history.add(_updated, _position, _speed, _course, pathinfo); 
               _db.getRoutes().removeOldEdges(getIdent(), _history.oldestPoint());
               setChanging();
           }
        }
        updatePosition(ts, newpos);
        
        _speed = sp;
        _course = crs;
        _altitude = alt;
       
        setDescr(descr); 
        
        if (_expired) {
            _expired = false;
            setChanging();
        }
        
        if (sym != _symbol || altsym != _altsym)
        {
            _symbol = sym;
            _altsym = altsym;
            setChanging();
        }
        
        isChanging(); 
    }
    


    
    
    public synchronized boolean expired()
    {
        Date now = new Date(); 
        if (_expired) 
            return true;
        if (now.getTime() <= _updated.getTime() + _expiretime)  // If expired
            return false;
        if (!_db.getOwnObjects().mayExpire(this))
            return false;
        _db.getRoutes().removeNode(this.getIdent());
        return (_expired = true); 
        
    }
    

}
