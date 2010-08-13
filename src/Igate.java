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


public class Igate implements Channel.Receiver
{ 
    private Channel _inetChan, _rfChan;
    private boolean _allowRf;
    private String  _myCall; /* Move this ? */
    private long    _msgcnt = 0; 
    
    public Igate(Properties config) 
    {
        _allowRf = config.getProperty("igate.rfgate.allow", "false").trim().matches("true|yes");
        _myCall = config.getProperty("igate.mycall", "N0CALL").trim();
    }  
       
    /**
     * Configure the igate with the channels to gate between. 
     * must be one RF channel and one APRS-IS channel.
     */   
    public void setChannels(Channel rf, Channel inet)
    {
        _inetChan = inet;
        _rfChan = rf; 
    }
    
    

    private void gate_to_inet(Channel.Packet p)
    {
       /* Note, we assume that third-party headers are stripped 
        * by the channel-implementation.  
        */
       if ( p.type == '?' /* QUERY */ ||
            p.via.matches(".*((TCP[A-Z0-9]{2})|NOGATE|RFONLY|NO_TX).*") ) 
           return;
            
       _msgcnt++;
       System.out.println("*** IGATED");
       p.via += (",qAR,"+_myCall);
       if (_inetChan != null) 
           _inetChan.sendPacket(p);
       
    }

    
    
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
    public void receivePacket(Channel.Packet p)
    {
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
    
}
