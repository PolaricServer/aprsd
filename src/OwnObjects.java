/* 
 * Copyright (C) 2011 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import java.util.regex.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;
import uk.me.jstott.jcoord.*;

/**
 * Objects owned by this instance of Polaric Server.
 */
 
public class OwnObjects implements Runnable
{ 
    private Channel         _inetChan, _rfChan;
    private boolean         _allowRf;
    private String          _pathRf;
    private int             _txPeriod;
    private String          _myCall, _file;
    private StationDB       _db;
    private BufferedReader  _rd;
    private StringTokenizer _next;
    private Set<String>     _ownObjects = new LinkedHashSet<String>();
    private Thread          _thread;
    private Station         _myself;
    private boolean         _forceUpdate;
    private int              _tid;
    
    
    public OwnObjects(Properties config, StationDB db) 
    {
        _allowRf = config.getProperty("objects.rfgate.allow", "false").trim().matches("true|yes");
        _pathRf = config.getProperty("objects.rfgate.path", "").trim(); 
        _myCall = config.getProperty("objects.mycall", "").trim().toUpperCase();
        if (_myCall.length() == 0)
           _myCall = config.getProperty("default.mycall", "NOCALL").trim().toUpperCase();
           
        _txPeriod = Integer.parseInt(config.getProperty("objects.transmit.period", "0").trim());
        _forceUpdate = config.getProperty("objects.forceupdate", "false").trim().matches("true|yes");
        _db = db;
        
        /* If station identified by mycall is not in database, create it
         */
        _myself = _db.getStation(_myCall);
        if (_myself == null)
           _myself = _db.newStation(_myCall);
           
        /* Should not expire as long as we have objects */
           
         if (_txPeriod > 0) {
            _thread = new Thread(this, "OwnObjects-"+(_tid++));
            _thread.start();
         }
    }  
       
       
    public int nItems() 
        { return _ownObjects.size(); }
        
    
    /**
     * Add an object.
     */  
    public synchronized boolean add(String id, Reference pos, char symtab, char sym, 
                        String comment, boolean perm)
    {
         AprsObject obj = (AprsObject) _db.getItem(id);

         /* Ignore if object already exists.
          * FIXME: If object exists, we may take over the name, but since
          * these objects arent just local, we should ask the user first, if this is
          * intended. For now we only take over our own, if forceupdate = true
          */
         if (obj == null || !obj.visible() ||
             (_forceUpdate && (_ownObjects.contains(id) || obj.getOwner() == _myself)))
         {
            if (id.length() > 9)
                id = id.substring(0,9);
            _myself.setUpdated(new Date());
            obj = _db.newObject(_myself, id);
            obj.update(new Date(), pos, 0, 0, 0, comment, sym, symtab,  "");
            obj.setTimeless(perm);
            _ownObjects.add(id);
            sendObjectReport(obj, false);
            return true;
         }
       
         System.out.println("WARNING: Object "+id+" already exists somewhere else");
         return false;
    }
    
    /**
     * Delete all own objects. 
     */
    public synchronized void clear()
    {
        for (String oid: _ownObjects) {
           AprsObject obj = (AprsObject) _db.getItem(oid+'@'+_myself.getIdent());
           if (obj!=null) {
              sendObjectReport(obj, true);
              obj.kill();
           }
        }
        _ownObjects.clear();
    }

    /** 
     * Delete object with the given id.
     */
    public synchronized boolean delete(String id)
    {
        AprsObject obj = (AprsObject) _db.getItem(id+'@'+_myself.getIdent());
        if (obj==null)
            return false;
        obj.kill();
        sendObjectReport(obj, true);
        _ownObjects.remove(id);
        return true;
    }




    protected void finalize() throws Throwable {
       // deleteAll(); 
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
        return (d==null ? "111111" : _tsformat.format(d)) + "z"  
               + _latformat.format(toGpsNumber(llref.getLatitude())) + symtab 
               + _lngformat.format(toGpsNumber(llref.getLongitude())) + sym ;  
    }
    
    
    
    /**
     * send object report on the given channel.
     */
    protected void sendObjectReport(AprsObject obj, boolean delete)
    {
       String id = (obj.getIdent().replaceFirst("@.*","") + "         ").substring(0,9);
       Channel.Packet p = new Channel.Packet();
       p.from = _myCall;
       p.to = Main.toaddr;
       p.type = ';';
       
       /* Should type char be part of report ? */
       p.report = ";" + id + (delete ? "_" : "*") 
                   + posReport((obj.isTimeless() ? null : obj.getUpdated()), obj.getPosition(), obj.getSymbol(), obj.getSymtab())
                   + obj.getDescr(); 
       System.out.println("*** OBJECTREPORT SEND: "+ p.from+">"+p.to+":"+p.report);
       
       /* Send object report on aprs-is */
       if (_inetChan != null) 
           _inetChan.sendPacket(p);
            
       /* Send object report on RF, if appropriate */
       p.via = _pathRf;
       if (_allowRf && _rfChan != null)
           _rfChan.sendPacket(p);
       /* FIXME: Should only tx on rf if object is local. We can do this only 
        * if we know our position. Maybe this decision should be moved to 
        * the igate/router as well!
        */
    }

    
    void save(ObjectOutput ofs)
    { 
       try { 
          ofs.writeObject(_ownObjects.size());
          for (String s: _ownObjects) {
              System.out.println("Save ownobj: "+s);
              ofs.writeObject(s); 
          }
       } catch (Exception e) {} 
    }


    void restore(ObjectInput ifs)
     {
        try { 
            int n = (Integer) ifs.readObject(); 
            for (int i=0; i<n; i++) {
                String x = (String) ifs.readObject();
                System.out.println("Restore ownobj: "+x);
                _ownObjects.add(x); 
            }
        } catch (Exception e) {} 
     }

    
    public void run() 
    {
       while (true) {
         try {
            Thread.sleep(3000);
            synchronized(this) {
              for (String oid: _ownObjects) {
                 AprsObject obj = (AprsObject) _db.getItem(oid+'@'+_myself.getIdent());
                 sendObjectReport(obj, false);
              }
            }
                 
            Thread.sleep(_txPeriod * 1000 - 3000);
         } catch (Exception e) {}
       }
    }
   
   
}
