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
import uk.me.jstott.jcoord.*; 
import java.util.*;
import java.io.Serializable;
  
  
/**
 * APRS station. 
 */
public class Station extends AprsPoint implements Serializable, Cloneable
{

   public static class Status implements Serializable
   {
       public Date time;
       public String text;
       public Status(Date t, String txt)
          { time = t; text = txt; }
   }

 
    public static void init(ServerAPI api)
      { int exptime = api.getIntProperty("aprs.expiretime", 60);
        setExpiretime(exptime * 60 * 1000);
      }
    
    
    /*
     * Attributes of a station record (APRS data)
     */
    private String    _callsign; 
    private Status    _status;
    private Telemetry _telemetry; 

   
    /* 
     * Other variables (presentation, storage, etc.)
     */
    private String      _pathinfo; 
    private int         _report_ignored = 0;
    private boolean     _igate, _wdigi;
    private Date        _infra_updated = null;
    private String      _source;
       

       
    public Station(String id)
       { super(null); _callsign = id; _trailcolor = _colTab.nextColour(); }
        
    public Object clone() throws CloneNotSupportedException
       { return super.clone(); }
                      
   
    @Override public String getIdent()
       { return _callsign; }
       
    protected void setId(String id)
       { _callsign = id; }
      
    @Override public void autoTag() {
        super.autoTag();
        if (_telemetry != null)
           if (!_telemetry.valid())
              removeTag("APRS.telemetry");
    }
      
    public boolean hasTelemetry() 
      { return _telemetry != null; }
      
       
    public Telemetry getTelemetry() {
       if (_telemetry == null)
          _telemetry = new Telemetry(); 
       return _telemetry;
    }
       
    public String getPathInfo()
       { return _pathinfo; }
       
    public void setPathInfo(String p)
       { _pathinfo = p; }
       
    public Set<String> getTrafficFrom()
       {  return _db.getRoutes().getToEdges(getIdent()); }
       
                       
    public Set<String> getTrafficTo()
       { return _db.getRoutes().getFromEdges(getIdent());}
              
              
    public boolean isInfra() { 
        if (getTrafficFrom() != null && !getTrafficFrom().isEmpty()) {
           setTag("APRS.infra");
           return true;
        }
        else return false;
    }
       
       
    /**
     * Reset infrastructure settings if older than 7 days 
     */
    private void expireInfra()
    {
        Date now = new Date();
        if (_infra_updated != null && 
            _infra_updated.getTime() + 1000*60*60*24*7 < now.getTime()) {
           _igate = _wdigi = false;
           removeTag("APRS.infra");
           removeTag("APRS.infra.igate");
           removeTag("APRS.infra.wdigi");
        }
    } 
    
    
    public void setSource(Source src)
       { _source = src.getIdent(); }
   
    @Override public Source getSource()
       { return _api.getChanManager().get(_source); }
       
    
    public boolean isIgate()
       { expireInfra(); 
         return _igate; }
       
       
    public void setIgate(boolean x)
       { _infra_updated = new Date();
         _igate = x; 
         setTag("APRS.infra.igate");
       }
       
       
    public boolean isWideDigi()
       { expireInfra(); 
         return _wdigi; }
       
       
    public void setWideDigi(boolean x)
       { _infra_updated = new Date(); 
         _wdigi = x;
         setTag("APRS.infra.wdigi");
       }       
       
    

    public synchronized void setStatus(Date t, String txt)
    {
        if (t==null)  
           t = new Date(); 
        _status = new Status(t, txt);
    }
    
    /* Vi kan kanskje legge inn en sjekk om statusrapporten er for gammel */
    public Status getStatus()
        { return _status; }
    
    
    @Override public synchronized void reset()
    {  
        _db.getRoutes().removeNode(this.getIdent());
        super.reset(); 
    }
     

     
    @Override public synchronized void setUpdated(Date ts)
      { _updated = ts; _expired = false; }
      
      
        
    public synchronized void update(Date ts, AprsHandler.PosData pd, String descr, String pathinfo)
    { 
        if (saveToTrail(ts, pd.pos, pd.speed, pd.course, _pathinfo)) {
             updatePosition(ts, pd.pos, pd.ambiguity);
            _db.getRoutes().removeOldEdges(getIdent(), _trail.oldestPoint());
           
            setSpeed(pd.speed);
            setCourse(pd.course);
            setAltitude((int) pd.altitude);
            _pathinfo = pathinfo; 
            setDescr(descr); 
        
            if (pd.symbol != 0 && pd.symtab != 0 && (pd.symbol != _symbol || pd.symtab != _altsym))
            {
                if (pd.symbol != 0)  _symbol = pd.symbol;
                if (pd.symtab != 0)  _altsym = pd.symtab;
                setChanging();
            }
        }
        if (_expired) {
            _expired = false;
            setChanging();
        }
        isChanging(); 
    }
    

    
    @Override
    public synchronized boolean _expired()
    {
        if (!super._expired()) 
            return false;
        if (!_db.getOwnObjects().mayExpire(this))
            return false;
        _db.getRoutes().removeNode(this.getIdent());
        return true; 
    }
    

}
