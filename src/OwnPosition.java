 
/* 
 * Copyright (C) 2016-2022 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
    transient private  AprsChannel _inetChan, _rfChan;
    transient private  Thread      _thread;
    transient protected  ServerAPI _api;
    transient private  int         _tid;
    transient private  boolean     _txOn, _allowRf, _compress;
    transient protected String     _pathRf, _comment;
    transient protected int        _maxPause, _minPause;
    
    private final static int _trackTime = 10;
    private final static byte ASCII_BASE = 33;
    
    
    
    private class LocalSource extends Source
     {
         public LocalSource(ServerAPI api, String prefix, String id)
            { _tag="own"; _init(api, prefix, "ownposition"); }
         public String getShortDescr() 
            { return "OWN"; }
     }
    
    
    
    
    public OwnPosition(ServerAPI api) 
    {
        super(null);     
        _api = api;
        
        setSource(new LocalSource(api, "localsrc", "ownposition"));
        init();
        
        if (_txOn) {
           _thread = new Thread(this, "OwnPosition-"+(_tid++));
           _thread.start();
        }
    }
    
    
    
    public void init() {
        String ownpos = _api.getProperty("ownposition.pos", ""); 
        String myCall = _api.getProperty("ownposition.mycall", "").toUpperCase();
        if (myCall.length() == 0)
           myCall = _api.getProperty("default.mycall", "NOCALL").toUpperCase();
        String sym = _api.getProperty("ownposition.symbol", "/."); 
        setSymtab(sym.charAt(0));
        setSymbol(sym.charAt(1));
        _txOn     = _api.getBoolProperty("ownposition.tx.on", false);        
        _allowRf  = _api.getBoolProperty("ownposition.tx.allowrf", false);
        _pathRf   = _api.getProperty("ownposition.tx.rfpath", "WIDE1-1"); 
        _comment  = _api.getProperty("ownposition.tx.comment", "");
        _compress = _api.getBoolProperty("ownposition.tx.compress", false);
        _maxPause = _api.getIntProperty("ownposition.tx.maxpause", 600);
        _minPause = _api.getIntProperty("ownposition.tx.minpause", 120);
        if (_minPause == 0)
           _minPause = _maxPause;
        _description = _comment;
        
        String[] pp = ownpos.split("[-\\s]+");
        if (pp.length == 3) {
           Reference p = new UTMRef(Double.parseDouble(pp[1]), Double.parseDouble(pp[2]), pp[0].charAt(2), 
                      Integer.parseInt( pp[0].substring(0,2)));
           p = p.toLatLng();
           /* FIXME: Don't reset pos if GPS is active */
           updatePosition(new Date(), p, 0);
           _api.log().info("OwnPosition", "Position is: "+p);
        }
        setId(myCall);
        setAltitude(-1);
    }
    
    
    
    /**
     * Set channels for APRS position updates. 
     *  @param rfc RF channel (radio)
     *  @param ic  Internet channel (APRS-IS)
     */
    public synchronized void setChannels(AprsChannel rfc, AprsChannel ic)
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
        return String.format((Locale)null, "%0"+ndeg+"d%05.2f", deg, mins); 
    }  



    private String compressLL(double pos, boolean is_longitude) {
        double x = (is_longitude ? 190463 *(180+pos) : 380926 *(90-pos));;
        byte[] out = new byte[4];

        out[0] = (byte) (Math.floor(x / 753571) + ASCII_BASE);
        x %= 753571;
        out[1] = (byte) (Math.floor(x / 8281) + ASCII_BASE);
        x %= 8281;
        out[2] = (byte) (Math.floor(x / 91) + ASCII_BASE);
        x %= 91;
        out[3] = (byte) (Math.floor(x) + ASCII_BASE);
        return new String(out);
    }
   
   
   
    private String compressCST() {
        /* Send course/speed (default) */
       String res="";
       res += (char) (getCourse() / 4 + ASCII_BASE);
       res += (char) (Math.round(Math.log(getSpeed()+1)/0.076961) + ASCII_BASE); 
       res += (char) (0x18 + ASCII_BASE);
       return res;
    }
    
    
   
   /**
    * Encode position for use in APRS.
    */
    protected String encodePos()
    {
        LatLng pos = getPosition().toLatLng();
        
        if (_compress) { 
            var lat_c = compressLL(pos.getLatitude(), false);
            var lon_c = compressLL(pos.getLongitude(), true);
            var cst_c = compressCST();
            return getSymtab() + lat_c + lon_c + getSymbol() + cst_c;
        }
        else {
            /* Format latitude and longitude values, etc. */
            char lat_sn = (pos.getLatitude() < 0 ? 'S' : 'N');
            char long_we = (pos.getLongitude() < 0 ? 'W' : 'E');
            float latf = Math.abs((float) pos.getLatitude());
            float longf = Math.abs((float) pos.getLongitude());
            return showDMstring(latf,2) + lat_sn + getSymtab() + showDMstring(longf, 3)+ long_we + getSymbol();
        }
    }
    
    
    
    /* Extra reports (in comment field) */
    protected String xReports(Date ts, LatLng pos) {
        return "";
    }
    
    
    
    
   /**
     * Send APRS position report on the given channel.
     */
    protected void sendPosReport()
    {
        if ( !_txOn )
            return;
        boolean sentRf = false;
        AprsPacket p = new AprsPacket();
        p.from = getIdent();
        p.to = _api.getToAddr();
        p.type = '/';

        String xrep = xReports(new Date(), getPosition().toLatLng());
        p.report = "/" + timeStamp(new Date()) + encodePos() + xrep + _comment;
        
        _api.log().debug("OwnPosition", "Send position report: "+ p.from+">"+p.to+": "+p.report);
        _api.log().debug("OwnPosition", "XREP: "+ xrep);
        
        /* Send object report on RF, if appropriate */
        p.via = _pathRf; 
        if (_allowRf && _rfChan != null) {
           _rfChan.sendPacket(p);
           sentRf = true;
        }
        
        /* Send pos report on aprs-is */
        try { Thread.sleep(2000); } catch (Exception e) {}
        p.via = null;
          
        Igate ig = _api.getIgate();
        if (sentRf && ig != null && ig.isActive()) 
            ig.gate_to_inet(p);
        else
            if (_inetChan != null) 
                _inetChan.sendPacket(p);
            
    }

    
    protected int _timeSinceReport = 0;
    
    
    /**
     * Manual update of position. To be called from user interface. 
     *
     * @param t Timestamp (time of update). If null, the object will be timeless/permanent.
     * @param pos Position data (position, symbol, ambiguity, etc..)
     * @param symtab APRS symbol table 
     * @param symbol APRS symbol. 
     */
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
                  (should_update())) {
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

