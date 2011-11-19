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
import java.util.regex.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;
import uk.me.jstott.jcoord.*;

/**
 * Igate - Gateway between RF channel and internet channel.
 */
public class Igate implements Channel.Receiver, ManagedObject
{ 
    private Channel _inetChan, _rfChan;
    private boolean _allowRf;
    private String  _myCall; /* Move this ? */
    private long    _msgcnt = 0; 
    private boolean _active = false;
    
    public Igate(Properties config) 
    {
        _allowRf = config.getProperty("igate.rfgate.allow", "true").trim().matches("true|yes");
        _myCall = config.getProperty("igate.mycall", "").trim().toUpperCase();
        if (_myCall.length() == 0)
           _myCall = config.getProperty("default.mycall", "NOCALL").trim().toUpperCase();
    }  
       
       
    /**
     * Start the service.    
     * @param a: the server interface. 
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
            System.out.println("*** WARNING: Cannot activate igate, channel(s) not set");
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
     */   
    public synchronized void setChannels(Channel rf, Channel inet)
    {
        _inetChan = inet;
        _rfChan = rf; 
    }
    
    
    /**
     * Gate packet (from RF) to internet.
     */
    private void gate_to_inet(Channel.Packet p)
    {
       /* Note, we assume that third-party headers are stripped 
        * by the channel-implementation.  
        */
       if ( p.type == '?' /* QUERY */ ||
            p.via.matches(".*((TCP[A-Z0-9]{2})|NOGATE|RFONLY|NO_TX).*") ) 
           return;
            
       _msgcnt++;
       System.out.println("*** GATED TO INTERNET");
       p.via += (",qAR,"+_myCall);
       if (_inetChan != null) 
           _inetChan.sendPacket(p);
       
    }

    
    /**
     * Gate packet (from internet) to RF.
     */
    private void gate_to_rf(Channel.Packet p)
    {
       if (    /* Receiver heard on RF */
              ( _rfChan.heard(p.to) || (p.msgto!= null && _rfChan.heard(p.msgto)))
                
            && /* Sender NOT heard on RF */
               ! _rfChan.heard(p.from)
               
            && /* Receiver not heard on INET side */ 
               ( ! _inetChan.heard(p.to)  && !( p.msgto!=null && _inetChan.heard(p.msgto)))
               
            && /* No TCPXX, NOGATE, or RFONLY in header */
               ! p.via.matches(".*((TCP[A-Z0-9]{2})|NOGATE|RFONLY|NO_TX).*") )
       {        
          System.out.println("*** GATED TO RF");
          _rfChan.sendPacket(p);
       } 
    }
    
    
    /**
     * Respond to query be sending a capabilities report. 
     * For now, we only respond to queries on the RF channel. Should we include
     * internet side as well???
     */
    private void answer_query()
    {
        Channel.Packet p = new Channel.Packet(); 
        p.type = '<';
        p.report = "<IGATE,MSG_CNT="+_msgcnt+",LOC_CNT="+_rfChan.nHeard()+",Polaric-Aprsd";
        _rfChan.sendPacket(p);
    }
    
    
    /**
     * Receive and gate an APRS packet.
     */
    public synchronized void receivePacket(Channel.Packet p, boolean dup)
    {
        if (dup)
           return;
        if (p.source == _rfChan) {
           if (p.report.matches("\\?IGATE\\?.*"))
               answer_query();
           else
               gate_to_inet(p);
        }
        else
           if ( _allowRf )
              gate_to_rf(p);
    }
    
    
    
    public String toString() 
      { return "msg_cnt="+_msgcnt+", loc_cnt="+_rfChan.nHeard(); }
}
