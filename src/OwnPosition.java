 
/* 
 * Copyright (C) 2012 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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



/**
 * Tracking of our own position
 */
public class OwnPosition extends Station implements Runnable
{
    transient private  Channel    _inetChan, _rfChan;
    transient private  Thread     _thread;
    transient private  ServerAPI  _api;
    transient private  int        _tid;
    transient private  boolean    _txOn, _allowRf;
    transient protected String    _pathRf, _comment;
    transient protected int       _maxPause, _minPause;
    
    private final static int _trackTime = 10;
    
    
    
    private class LocalSource extends Source
     {
         public LocalSource(ServerAPI api, String prefix, String id)
            { _style="own"; _init(api, prefix, "ownposition"); }
         public String getShortDescr() 
            { return "OWN"; }
     }
    
    
    
    
    public OwnPosition(ServerAPI api) 
    {
        super(null);     
        _api = api;
        
        setSource(new LocalSource(api, "localsrc", "ownposition"));
        String ownpos = api.getProperty("ownposition.pos", ""); 
        String myCall = api.getProperty("ownposition.mycall", "").toUpperCase();
        if (myCall.length() == 0)
           myCall = api.getProperty("default.mycall", "NOCALL").toUpperCase();
        String sym = api.getProperty("ownposition.symbol", "/."); 
        setSymtab(sym.charAt(0));
        setSymbol(sym.charAt(1));
        _txOn     = api.getBoolProperty("ownposition.tx.on", false);        
        _allowRf  = api.getBoolProperty("ownposition.tx.allowrf", false);
        _pathRf   = api.getProperty("ownposition.tx.rfpath", "WIDE1-1"); 
        _comment  = api.getProperty("ownposition.tx.comment", "");
        _maxPause = api.getIntProperty("ownposition.tx.maxpause", 600);
        _minPause = api.getIntProperty("ownposition.tx.minpause", 120);
        if (_minPause == 0)
           _minPause = _maxPause; 
           
        String[] pp = ownpos.split("[-\\s]+");
        if (pp.length == 3) {
           Reference p = new UTMRef(Double.parseDouble(pp[1]), Double.parseDouble(pp[2]), pp[0].charAt(2), 
                      Integer.parseInt( pp[0].substring(0,2)));
           updatePosition(new Date(), p, 0);
           System.out.println("*** Own Position: "+p);
        }
        setId(myCall);
        setAltitude(-1);
        _description = _comment;
           
        if (_txOn) {
           _thread = new Thread(this, "OwnPosition-"+(_tid++));
           _thread.start();
        }
    }
    
    /**
     * Set channels for APRS position updates. 
     *  @param rfc RF channel (radio)
     *  @param ic  Internet channel (APRS-IS)
     */
    public synchronized void setChannels(Channel rfc, Channel ic)
    {
       _inetChan = ic; 
       _rfChan = rfc;
    }
    
    
    @Override 
    public synchronized boolean expired()
       { return false; }
       
       
    
       
    // FIXME: This is also defined in HttpServer.java and AprsParser.java
    public static Calendar utcTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.getDefault());       
       
    protected String timeStamp(Date t)
      { 
          Calendar ts = (Calendar) utcTime.clone();
          ts.setTimeInMillis( t.getTime()) ;
          return String.format("%1$tH%1$tM%1$tSh", ts);
      }
       
       
   protected String showDMstring(float ll, int ndeg)
   {
       int deg = (int) Math.floor(ll);
       float minx = ll - deg;
       if (ll < 0 && minx != 0.0) 
          minx = 1 - minx; // FIXME: is this correct. Check code in PT as well?
          
       float mins = ((float) Math.round( minx * 60 * 100)) / 100;
       return String.format("%0"+ndeg+"d%05.2f", deg, mins);  
   }  
   
   /**
    * Encode position for use in APRS.
    */
    protected String encodePos()
    {
        LatLng pos = getPosition().toLatLng();
        /* Format latitude and longitude values, etc. */
        char lat_sn = (pos.getLatitude() < 0 ? 'S' : 'N');
        char long_we = (pos.getLongitude() < 0 ? 'W' : 'E');
        float latf = Math.abs((float) pos.getLatitude());
        float longf = Math.abs((float) pos.getLongitude());
        return showDMstring(latf,2) + lat_sn + getSymtab() + showDMstring(longf, 3)+ long_we + getSymbol();
    }
    
       
   /**
     * Send APRS position report on the given channel.
     */
    protected void sendPosReport()
    {
       Channel.Packet p = new Channel.Packet();
       p.from = getIdent();
       p.to = _api.getToAddr();
       p.type = '/';
       
       /* Should type char be part of report ? */
       p.report = "/" + timeStamp(new Date()) + encodePos()+_comment;
       System.out.println("*** POSREPORT SEND: "+ p.from+">"+p.to+":"+p.report);
       
       /* Send object report on RF, if appropriate */
       p.via = _pathRf; 
       if (_allowRf && _rfChan != null)
           _rfChan.sendPacket(p);
       
       /* Send object report on aprs-is */
       try { Thread.sleep(2000); } catch (Exception e) {}
       p.via = null;
       if (_inetChan != null) 
           _inetChan.sendPacket(p);
            
    }

    
    protected int _timeSinceReport = 0;
    
    
    /**
     * Manual update of position. To be called from user interface. 
     *
     * @param ts Timestamp (time of update). If null, the object will be timeless/permanent.
     * @param pd Position data (position, symbol, ambiguity, etc..)
     * @param descr Comment field. 
     * @param path Digipeater path of containing packet. 
     */
    @Override
    public synchronized void updatePosition(Date t, Reference pos, char symtab, char symbol)
    {
          update(new Date(),new AprsHandler.PosData(pos, symbol, symtab), _comment, "");
          _timeSinceReport = 0;
          sendPosReport();
    }

    
    
    /**
     * Returns true if its time to send an APRS update. 
     * Can be overridden in subclasses to do advanced tracking, smart beaconing, etc..
     */
    protected boolean should_update () 
    {
       int t = Math.min(_maxPause, 120); 
       t = Math.max(_minPause, t); 
       return (isChanging() && _timeSinceReport >= t); 
    }



    public void run() 
    {
       /* 
        * Simple periodic reporting of position if it exists. Note that this 
        * could easily be extended to do real tracking, possibly with smart
        * beaconing. 
        */
       while (true) {
         try {  
            Thread.sleep(_trackTime * 1000);
            synchronized (this) {
              _timeSinceReport += _trackTime;
              if (getPosition() != null  && 
                  (_timeSinceReport >= _maxPause || should_update())) {
                 _timeSinceReport = 0;
                 sendPosReport();
              }
           }    
         } catch (Exception e) {
             e.printStackTrace(System.out);
         }
       }
    }
}

