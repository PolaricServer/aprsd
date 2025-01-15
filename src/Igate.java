/* 
 * Copyright (C) 2016-2023 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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


/**
 * Igate - Gateway between RF channel and internet channel.
 */
public class Igate implements AprsChannel.Receiver, ManagedObject
{ 
    private AprsChannel  _inetChan, _rfChan;
    private boolean  _allowRf, _gateObj;
    private String   _myCall; /* Move this ? */
    private long     _msgcnt = 0; 
    private boolean  _active = false;
    private String   _defaultPath, _pathObj, _alwaysRf;
    private int      _rangeObj;
    private Logfile  _log;
    private ServerAPI   _api;
    
    
    public Igate(ServerAPI api) 
    {
        _api = api;
        init();
    }  
       
       
    public void init() 
    {
        _allowRf     = _api.getBoolProperty("igate.rfgate.allow", true);
        _gateObj     = _api.getBoolProperty("igate.rfgate.objects", false);
        _pathObj     = _api.getProperty("objects.rfgate.path", ""); 
        _rangeObj    = _api.getIntProperty("objects.rfgate.range", 0);
        _myCall      = _api.getProperty("igate.mycall", "").trim().toUpperCase();
        _defaultPath = _api.getProperty("message.rfpath", "WIDE1-1");
        _alwaysRf    = _api.getProperty("message.alwaysRf", "");       
        if (_myCall.length() == 0)
           _myCall   = _api.getProperty("default.mycall", "NOCALL").toUpperCase();      
        _log         = new Logfile(_api, "igate", "igate.log");  
    }
    
       
    /**
     * Start the service.    
     * @param a The server interface. 
     */
    public synchronized void activate(ServerAPI a) {
         /* Activating means subscribing to traffic from the two channels */
         if (_inetChan != null && _rfChan != null) {
            _inetChan.addReceiver(this); 
            _rfChan.addReceiver(this);
            _active = true;
         }
         else
            /* FIXME: Should perhaps throw exception here? */
            _api.log().warn("Igate", "Cannot activate igate, channel(s) not set");
    }
   
   
    /** stop the service. */
    public synchronized void deActivate() {
         _inetChan.removeReceiver(this); 
         _rfChan.removeReceiver(this);
         _active = false;
    }
    
   
    /** Return true if service is running. */
    public boolean isActive()
       { return _active; }
    
    
    
    
    /**
     * Configure the igate with the channels to gate between. 
     * must be one RF channel and one APRS-IS channel. Note that the igate cannot 
     * be activated before these are properly set. 
     * @param rf RF channel.
     * @param inet Internet channel (APRS-IS).
     */   
    public synchronized void setChannels(AprsChannel rf, AprsChannel inet)
    {
        setRfChan(rf);
        setInetChan(inet);
    }
    public void setRfChan(AprsChannel rf) {
      if (rf != null && !rf.isRf())
         _api.log().warn("Igate", "Non-RF channel used as RF channel");
      _rfChan = rf;
    }
    public void setInetChan(AprsChannel inet) {
      if (inet != null && inet.isRf())
         _api.log().warn("Igate", "RF channel used as internet channel");
      _inetChan = inet;
    }
    
    
    
    
    /**
     * Gate packet (from RF) to internet.
     */
    public void gate_to_inet(AprsPacket p)
    {
       /* Note, we assume that third-party headers are stripped 
        * by the channel-implementation.  
        */
       if ( p.type == '?' /* QUERY */ ||
            (p.via != null && p.via.matches(".*((TCP[A-Z0-9]{2})|NOGATE|RFONLY|NO_TX).*") ) )
           return;
            
       _msgcnt++;
       _api.log().debug("Igate", "Gated to internet");
       _log.log(" [" + _rfChan.getIdent() + ">" + _inetChan.getIdent() + "] " + p);       
       
       p.via += (",qAR,"+_myCall);
       if (_inetChan != null && !_inetChan.isRf()) 
           _inetChan.sendPacket(p);
       
    }

    
    /**
     * Gate packet (from internet) to RF.
     */
    private void gate_to_rf(AprsPacket p)
    {
        if ( ( (  /* Receiver heard on RF */
                 ( _rfChan.heard(p.to) || (p.msgto!= null && !"".equals(p.msgto) &&
                    (_rfChan.heard(p.msgto) || p.msgto.matches(_alwaysRf))))
            
               && /* AND Receiver not heard on INET side */ 
                 ( ! _inetChan.heard(p.to)  && !( p.msgto!=null && _inetChan.heard(p.msgto)))      
            )
            || /* OR object and in range */
                ( _gateObj && p.type == ';' && object_in_range(p, _rangeObj) ) 
          ) 
          && /* AND Sender NOT heard on RF */
               ! _rfChan.heard(p.from)
                      
          && /* AND No TCPXX, NOGATE, or RFONLY in header */
               ! p.via.matches(".*((TCPXX)|NOGATE|RFONLY|NO_TX).*") 
       )    
       {        
          _api.log().debug("Igate", "Gated to RF");
          _log.log(" [" + _inetChan.getIdent() + ">" + _rfChan.getIdent() + "] " 
               + p + (p.thirdparty ? " (was thirdparty)" : ""));
               
          /* Now, get a proper path for the packet. 
           * For messages, if possible, a reverse of the path last heard from the recipient.
           */
          String path = _rfChan.heardPath(p.msgto); 
          p.via_orig = p.via;
          p.via = _defaultPath;
          
          /* Object */
          if (p.type == ';' && _pathObj != null)
             p.via = _pathObj;
             
          /* Message */
          if (p.type == ':' && path != null) 
             p.via = AprsChannel.getReversePath(path); 
             
          /* Send as third party report */
          p.report = AprsChannel.thirdPartyReport(p, "TCPIP,"+_myCall+"*");
          p.from = _myCall;
          p.to = _api.getToAddr();
          p.thirdparty = false;               
          if (_inetChan != null && _rfChan.isRf()) 
              _rfChan.sendPacket(p);
       } 
    }

    
    
    private boolean object_in_range(AprsPacket p, int range)
    {
       /* We assume that object's position is already parsed and in database. */
       if (_api.getOwnPos() == null || _api.getOwnPos().getPosition() == null)
            return false;
       AprsPoint obj = (AprsPoint) _api.getDB().getItem(p.msgto, null);
       if (obj == null)
            return false;
       return (obj.distance(_api.getOwnPos()) < range*1000);
    }
         

    
    
    /**
     * Respond to query be sending a capabilities report. 
     * For now, we only respond to queries on the RF channel. Should we include
     * internet side as well???
     */
    private void answer_query()
    {
        AprsPacket p = new AprsPacket(); 
        p.type = '<';
        p.report = "<IGATE,MSG_CNT="+_msgcnt+",LOC_CNT="+_rfChan.nHeard()+",Polaric-Aprsd";
        _rfChan.sendPacket(p);
    }
    
    
    /**
     * Receive and gate an APRS packet.
     */
    public synchronized void receivePacket(AprsPacket p, boolean dup)
    {
        if (dup || p==null || 
            (_api.getOwnPos().getIdent() != null && _api.getOwnPos().getIdent().equals(p.msgto)))
           return;
        if (p.source == _rfChan) {
            if (p.report.matches("\\?IGATE\\?.*"))
               answer_query();
            else if (_inetChan.isActive())
               gate_to_inet(p);
        }
        else
           if ( _allowRf && _rfChan.isActive())
              gate_to_rf(p);
    }
    
    
    
    public String toString() 
      { return "msg_cnt="+_msgcnt+", loc_cnt="+(_rfChan == null ? 0 : _rfChan.nHeard()); }
}
