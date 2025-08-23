/* 
 * Copyright (C) 2016-2025 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 */
 
package no.polaric.aprsd;
import no.polaric.aprsd.point.*;
import no.polaric.aprsd.channel.*;
import java.util.regex.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;




/**
 * Objects owned by this instance of Polaric Server.
 */
 
public class OwnObjects implements Runnable
{ 
    private AprsChannel     _inetChan, _rfChan;
    private boolean         _allowRf;
    private String          _pathRf;
    private int             _rangeRf;
    private int             _txPeriod;
    private String          _file;
    private AprsServerAPI   _api;
    private BufferedReader  _rd;
    private StringTokenizer _next;
    private Set<String>     _ownObjects = new LinkedHashSet<String>();
    private Thread          _thread;
    private boolean         _forceUpdate;
    private int              _tid;
    
    
    public OwnObjects(AprsServerAPI api) 
    {
        _api = api;
        _allowRf     = api.getBoolProperty("objects.rfgate.allow", false);
        _pathRf      = api.getProperty("objects.rfgate.path", ""); 
        _rangeRf     = api.getIntProperty("objects.rfgate.range", 0);
        _txPeriod    = api.getIntProperty("objects.transmit.period", 360);
        _forceUpdate = api.getBoolProperty("objects.forceupdate", false);
        
           
        /* Should not expire as long as we have objects */        
         if (_txPeriod > 0) {
            _thread = new Thread(this, "OwnObjects-"+(_tid++));
            _thread.start();
         }
    }  
       
       
    public int nItems() 
        { return _ownObjects.size(); }
    
    
    
    public Set<String> getItems()
        { return Set.copyOf(_ownObjects); }
        
        
        
    public synchronized AprsObject get(String id) 
    { 
        if (!_ownObjects.contains(id))
            return null;
        return (AprsObject) _api.getDB().getItem(id, null);
    }
        
    
    /**
     * Add an object.
     */  
    public synchronized boolean add(String id, ReportHandler.PosData pos,
                        String comment, boolean perm)
    {
         AprsObject obj = (AprsObject) _api.getDB().getItem(id, null);

         /* Ignore if object already exists.
          * FIXME: If object exists, we may take over the name, but since
          * these objects arent just local, we should ask the user first, if this is
          * intendeed. For now we only take over our own, if forceupdate = true
          */
         if (obj == null || !obj.visible() ||
             (_forceUpdate && (_ownObjects.contains(id) || obj.getOwner() == _api.getOwnPos())))
         {
            if (id.length() > 9)
                id = id.substring(0,9);
            _api.getOwnPos().setUpdated(new Date());
            obj = _api.getDB().newObject(_api.getOwnPos(), id);
            obj.update(new Date(), pos, comment,  "");
            obj.setTimeless(perm);
            _ownObjects.add(id);
            obj.setTag("own");
            obj.autoTag();
            sendObjectReport(obj, false);
            return true;
         }
       
         _api.log().warn("OwnObject", "Object "+id+" already exists somewhere else");
         return false;
    }
    
    
    
    
    /**
     * Delete all own objects. 
     */
    public synchronized void clear()
    {
        for (String oid: _ownObjects) {
           AprsObject obj = (AprsObject) _api.getDB().getItem(oid+'@'+_api.getOwnPos().getIdent(), null);
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
        AprsObject obj = (AprsObject) _api.getDB().getItem(id+'@'+_api.getOwnPos().getIdent(), null);
        if (obj==null)
            return false;
        obj.kill();
        sendObjectReport(obj, true);
        _ownObjects.remove(id);
        return true;
    }



    public synchronized boolean hasObject(String id)
    {
       return _ownObjects.contains(id);
    }

    
    public synchronized boolean mayExpire(Station s)
    {
        return (s != _api.getOwnPos()) || _ownObjects.isEmpty();
    }        
            
    
       
    public void setChannels(AprsChannel rf, AprsChannel inet)
    {
        setRfChan(rf);
        setInetChan(inet);
    }
    
    public void setRfChan(AprsChannel rf) {
      if (rf != null && !rf.isRf())
         _api.log().warn("OwnObjects", "Non-RF channel used as RF channel");
      _rfChan = rf;
    }
    
    public void setInetChan(AprsChannel inet) {
      if (inet != null && inet.isRf())
         _api.log().warn("OwnObjects", "RF channel used as internet channel");
      _inetChan = inet;
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
    
    
    protected String posReport(Date d, LatLng pos, char sym, char symtab)
    {
        _tsformat.setCalendar(_utcTime);
        return (d==null ? "111111" : _tsformat.format(d)) + "z"  
               + _latformat.format(toGpsNumber(pos.getLat())) + symtab 
               + _lngformat.format(toGpsNumber(pos.getLng())) + sym ;  
    }
    
    
   /**
     * send object report on the given channel.
     */
    protected void sendObjectReport(AprsObject obj, boolean delete)
    {
       if (obj.getPosition() == null)
         return;
       String id = (obj.getIdent().replaceFirst("@.*","") + "         ").substring(0,9);
       AprsPacket p = new AprsPacket();
       p.from = _api.getOwnPos().getIdent();
       p.to = _api.getToAddr();
       p.type = ';';
       
       /* Should type char be part of report ? */
       p.report = ";" + id + (delete ? "_" : "*") 
                   + posReport((obj.isTimeless() ? null : obj.getUpdated()), obj.getPosition(), obj.getSymbol(), obj.getSymtab())
                   + obj.getDescr(); 
       _api.log().debug("OwnObjects", "Send object report: "+ p.from+">"+p.to+":"+p.report);
       
       /* Send object report on aprs-is */
       if (_inetChan != null && !_inetChan.isRf()) 
           _inetChan.sendPacket(p);
            
       /* Send object report on RF, if appropriate */
       p.via = _pathRf;
       if (_allowRf && _rfChan != null && _rfChan.isRf() && object_in_range(obj, _rangeRf))
           _rfChan.sendPacket(p);
    }
    
    
    
    private boolean object_in_range(AprsObject obj, int range)
    {
        /* If own position is not set, object is NOT in range */
        if (_api.getOwnPos() == null || _api.getOwnPos().getPosition() == null)
            return false;
        return (obj.distance(_api.getOwnPos()) < range*1000);
    }
    
    
    
    void save(ObjectOutput ofs)
    { 
       try { 
          ofs.writeObject(_ownObjects.size());
          for (String s: _ownObjects) {
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
                _ownObjects.add(x); 
            }
        } catch (Exception e) {} 
     }

    
    public void run() 
    {
       while (true) {
         try {
            Thread.sleep(6000);
            synchronized(this) {
              String err = null; 
              if (_api.getOwnPos()==null || _api.getOwnPos().getIdent()==null)
                continue;
              for (String oid: _ownObjects) {
                 AprsObject obj = (AprsObject) _api.getDB().getItem(oid+'@'+_api.getOwnPos().getIdent(), null);
                 if (obj != null)
                    sendObjectReport(obj, false);
                 else 
                    err = oid; 
              }
              if (err != null)
                 _ownObjects.remove(err);
            }
                 
            Thread.sleep(_txPeriod * 1000 - 3000);
         } catch (Exception e) 
            { _api.log().error("OwnObjects", "Run thread: "+e); 
               e.printStackTrace(System.out); }
       }
    }
   
   
}
