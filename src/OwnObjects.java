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
package aprs;
import java.util.regex.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;
import uk.me.jstott.jcoord.*;


public class OwnObjects implements Runnable
{ 
    private Channel         _inetChan, _rfChan;
    private boolean         _allowRf;
    private int             _txPeriod;
    private String          _myCall, _file;
    private StationDB       _db;
    private BufferedReader  _rd;
    private StringTokenizer _next;
    private Set<String>    _ownObjects = new LinkedHashSet<String>();
    private Thread          _thread;
    private Station	    _myself;
    private boolean         _forceUpdate;
    
    
    public OwnObjects(Properties config, StationDB db) 
    {
        _allowRf = config.getProperty("objects.rfgate.allow", "false").trim().matches("true|yes");
        _myCall = config.getProperty("objects.mycall", "N0CALL").trim();
        _file = config.getProperty("objects.file", null);
        _txPeriod = Integer.parseInt(config.getProperty("objects.transmit.period", "0").trim());
        _forceUpdate = config.getProperty("objects.forceupdate", "false").trim().matches("true|yes");
        _db = db;
        
        /* If station identified by mycall is not in database, create it
         */
        _myself = _db.getStation(_myCall);
        if (_myself == null)
           _myself = _db.newStation(_myCall);
           
        /* Should not expire as long as we have objects */
        
        if (_file != null)
           try {
              _rd = new BufferedReader(new FileReader(_file));
              while (_rd.ready())
              {
                  String line = _rd.readLine();
                  if (!line.startsWith("#")) 
                  {                 
                      String[] x = line.split(",\\s*");  
                      if (x.length < 5)
                          continue;
                      if (!x[0].matches("[0-9]{2}[a-zA-Z]") || 
                          !x[1].matches("[0-9]{6}") || !x[2].matches("[0-9]{7}"))
                          continue;    
                      double easting = Long.parseLong(x[1]);
                      double northing = Long.parseLong(x[2]);
                      Reference pos = new UTMRef( Integer.parseInt( x[0].substring(0,2)), 
                                                  x[0].charAt(2),
                                                  easting, northing);                       

                      String sym = x[4].trim();
                      add (x[3].trim(), pos, sym.charAt(0), sym.charAt(1), x[5].trim());
                  }
              }      
           }
           catch (Exception e) 
                { System.out.println("OBJECTLIST WARNING: "+e); }
    
           if (_txPeriod > 0) {
              _thread = new Thread(this);
              _thread.start();
           }
    }  
       

    public synchronized boolean add(String id, Reference pos, char symtab, char sym, String comment)
    {
         AprsObject obj = (AprsObject) _db.getItem(id);

         /* Ignore if object already exists.
          * FIXME: If object exists, we may take over the name??  */
         if (obj == null || !obj.visible() ||
             (_forceUpdate && (_ownObjects.contains(id) || obj.getOwner() == _myself)))
         {
            if (id.length() > 9)
                id = id.substring(0,9);
            _myself.setUpdated(new Date());
            obj = _db.newObject(_myself, id);
            obj.update(new Date(), pos, 0, 0, 0, comment, sym, (symtab != '/'));
            _ownObjects.add(id);
            sendObjectReport(obj, false);
            return true;
         }
       
         System.out.println("WARNING: Object "+sym+" already exists somewhere else");
         return false;
    }
    
    
    public synchronized void deleteAll()
    {
        for (String oid: _ownObjects) {
           AprsObject obj = (AprsObject) _db.getItem(oid);
           if (obj!=null) {
              sendObjectReport(obj, true);
              obj.kill();
           }
        }
        _ownObjects.clear();
    }


    public synchronized boolean delete(String id)
    {
        AprsObject obj = (AprsObject) _db.getItem(id);
        if (obj==null)
            return false;
        obj.kill();
        sendObjectReport(obj, true);
        _ownObjects.remove(id);
        return true;
    }

    protected void finalize() throws Throwable {
       deleteAll(); 
    }

    public synchronized boolean hasObject(String id)
    {
       return _ownObjects.contains(id);
    }

    
    public synchronized boolean mayExpire(Station s)
    {
        return (s != _myself) || _ownObjects.isEmpty();
    }        
            
    
       
    public void setChannels(Channel rf, Channel inet)
    {
        _inetChan = inet;
        _rfChan = rf; 
    }
    
    

    
     // Redundant. see HttpServer
    private static Calendar _utcTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.getDefault());

    
     // Use for parsing as well?
    private DecimalFormat _latformat = new DecimalFormat("0000.00'N';0000.00'S'");
    private DecimalFormat _lngformat = new DecimalFormat("00000.00'E';00000.00'W'");
    private DateFormat    _tsformat = new SimpleDateFormat("ddHHmm");    

 
    /* Somewhat redundant - see HttpServer.showDMstring */
    protected double toGpsNumber(double ll)
    {
       int deg = (int) Math.floor(ll);
       double minx = ll - deg;
       if (ll < 0 && minx != 0.0) 
          minx = 1 - minx;
       double mins = ((double) Math.round( minx * 60 * 100)) / 100;
       return deg * 100 + mins; 
    }
    
    
    protected String posReport(Date d, Reference pos, char sym, char symtab)
    {
        LatLng llref = pos.toLatLng();
        _tsformat.setCalendar(_utcTime);
        return _tsformat.format(d) + "z"  
               + _latformat.format(toGpsNumber(llref.getLatitude())) + symtab 
               + _lngformat.format(toGpsNumber(llref.getLongitude())) + sym ;  
    }
    
    
    
    
    protected void sendObjectReport(Channel chan, AprsObject obj, boolean delete)
    {
       if (chan == null)
          return;
       String id = (obj.getIdent() + "         ").substring(0,9);
       Channel.Packet p = new Channel.Packet();
       p.from = _myCall;
       p.to = "APRS";
       p.type = ';';
       /* Should type char be part of report ? */
       p.report = ";" + id + (delete ? "_" : "*") + posReport(obj.getUpdated(), obj.getPosition(), obj.getSymbol(), obj.getSymtab())
                     + obj.getDescr(); 
       System.out.println("*** OBJECTREPORT SEND: "+ p.from+">"+p.to+":"+p.report);
       chan.sendPacket(p);
    }

    protected void sendObjectReport(AprsObject obj, boolean delete)
    {
        sendObjectReport(_inetChan, obj, delete);
        if (_allowRf)
            sendObjectReport(_rfChan, obj, delete);
    }
    
    
    
    public void run() 
    {
       while (true) {
         try {
            Thread.sleep(3000);
            synchronized(this) {
              for (String oid: _ownObjects) {
                 AprsObject obj = (AprsObject) _db.getItem(oid);
                 sendObjectReport(obj, false);
              }
            }
                 
            Thread.sleep(_txPeriod * 1000 - 3000);
         } catch (Exception e) {}
       }
    }
   
   
}
