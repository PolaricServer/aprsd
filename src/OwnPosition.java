 
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
    transient private   Channel    _inetChan, _rfChan;
    transient private   Thread     _thread;
    transient private   StationDB  _db;
    transient private   int        _tid;
    transient private   boolean    _txOn, _allowRf;
    transient protected String     _pathRf, _comment;
    transient protected int        _maxPause, _minPause;
    
    private final static int _trackTime = 10;
    
    
    
    public OwnPosition(Properties config) 
    {
        super(null);      
        String ownpos = config.getProperty("ownposition.pos", "").trim(); 
        String myCall = config.getProperty("ownposition.mycall", "").trim().toUpperCase();
        if (myCall.length() == 0)
           myCall = config.getProperty("default.mycall", "NOCALL").trim().toUpperCase();
        String sym = config.getProperty("ownposition.symbol", "/.").trim(); 
        setSymtab(sym.charAt(0));
        setSymbol(sym.charAt(1));
        _txOn     = config.getProperty("ownposition.tx.on", "false").trim().matches("true|yes");        
        _allowRf  = config.getProperty("ownposition.tx.allowrf", "false").trim().matches("true|yes");
        _pathRf   = config.getProperty("ownposition.tx.rfpath", "WIDE1-1").trim(); 
        _comment  = config.getProperty("ownposition.tx.comment", "").trim();
        _maxPause = Integer.parseInt(config.getProperty("ownposition.tx.maxpause", "600").trim());
        _minPause = Integer.parseInt(config.getProperty("ownposition.tx.minpause", "180").trim());
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
    
    
    public synchronized void setChannels(Channel rfc, Channel ic)
    {
       _inetChan = ic; 
       _rfChan = rfc;
    }
    
    
    @Override public synchronized boolean expired()
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
          minx = 1 - minx; 
         /* FIXME: is this correct? Check code in PT as well? */
          
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
       p.to = Main.toaddr;
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
     * Manual update of position. 
     * To be called from user interface. 
     */
    public synchronized void updatePosition(Date t, Reference pos, char symtab, char symbol)
    {
          update(new Date(), pos, -1, -1, -1, -1, _comment, symbol, symtab, null);
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


    /* Main thread */
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
              if ( getPosition() != null  && 
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

