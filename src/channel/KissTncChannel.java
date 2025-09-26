/* 
 * Copyright (C) 2020-2025 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
 
package no.polaric.aprsd.channel;
import no.polaric.aprsd.*;
import java.io.*;
import java.util.*;
import gnu.io.*;
import java.util.concurrent.Semaphore;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonSubTypes.*;




/**
 * TNC channel. For devices in KISS compatible mode.
 */
 
public class KissTncChannel extends TncChannel
{

    protected InputStream _istream; 
    protected OutputStream _ostream; 
    protected int _kissport;
  
    Kiss _kiss; 

    
    
    public KissTncChannel(AprsServerConfig conf, String id) 
    {
       super(conf, id);
       Properties config = conf.config();
       _kissport = conf.getIntProperty("channel."+id+".kissport", 0);
    }
    
    
        
    /* 
     * Information about config to be exchanged in REST API
     */
     
    @JsonTypeName("KISS")
    public static class  JsConfig extends Channel.JsConfig {      
        public long heard, heardpackets, duplicates, sentpackets; 
        public String port; 
        public int baud;
        public int kissport;
    }
       
       
    public JsConfig getJsConfig() {
        var cnf = new JsConfig();
        cnf.heard = nHeard();
        cnf.heardpackets = nHeardPackets(); 
        cnf.duplicates = nDuplicates(); 
        cnf.sentpackets = nSentPackets();
        
        cnf.type = "KISS";
        cnf.kissport = _conf.getIntProperty("channel."+getIdent()+".kissport", 0);
        cnf.baud = _conf.getIntProperty("channel."+getIdent()+".baud", 9600);
        cnf.port = _conf.getProperty("channel."+getIdent()+".port", "/dev/ttyS0");
        return cnf;
    }

    public void setJsConfig(Channel.JsConfig ccnf) {
        var cnf = (JsConfig) ccnf;
        var props = _conf.config();
        props.setProperty("channel."+getIdent()+".port", ""+cnf.port);
        props.setProperty("channel."+getIdent()+".baud", ""+cnf.baud);
        props.setProperty("channel."+getIdent()+".kissport", ""+cnf.kissport); 
    }
    
    
    
    /**
     * Send packet on RF. 
     */ 
    public synchronized boolean sendPacket(AprsPacket p)
    {      
        if (!isReady() || !canSend)
            return false; 
        _log.log(" [>" + this.getIdent() + "] " + p);
        try {
           _kiss.sendPacket(p);
           _sent++;
           return true;
        }
        catch (IOException e)
           { _conf.log().error("KissTncChannel", chId()+"sendPacket: "+e); }
        return false; 
    }
   
   
   
    /**
     * Close down the channel. 
     */
    @Override public void close() 
    { 
        _conf.log().info("KissTncChannel", chId()+"Closing channel");
       try {  
         _serial.deActivate(); 
         Thread.sleep(3000);
         if (_ostream != null) _ostream.close(); 
         if (_istream != null) _istream.close(); 
       }
       catch (Exception e) {}
    }
    
    
    
    /*
     * Main loop of channel. Receives packets.. 
     */
    @Override protected void receiveLoop() throws Exception
    {
        _ostream = _serial.getOutputStream();
        _istream = _serial.getInputStream(); 
        _kiss = new Kiss(_istream, _ostream, _kissport); 
        
        while (_serial.running()) 
        {
           try { 
               /* Delegate to Kiss decoder */
               AprsPacket p = _kiss.receivePacket();
               receivePacket(p, false);
           }
           catch (Kiss.Timeout e) {}
           Thread.yield();
        }
    }
    
       
}

