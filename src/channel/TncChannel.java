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
 
 
package no.polaric.aprsd.channel;
import no.polaric.aprsd.*;
import no.polaric.aprsd.aprs.*;
import no.polaric.core.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.Semaphore;


/**
 * TNC channel. For devices in TNC2 compatible mode.
 */
 
public abstract class TncChannel extends AprsChannel
{
    protected String      _myCall; /* Move this ? */
    private   Semaphore   _sem = new Semaphore(1, true);
    protected Logfile     _log; 
    protected SerialComm  _serial;
    
   
    
    /*
     * These are to be defined in subclasses 
     */
    protected abstract void close();   
    protected abstract void receiveLoop() throws Exception;
    
    
 
    public TncChannel(AprsServerConfig conf, String id) 
    {
        _init(conf, "channel", id);
        _conf = conf;
        _state = State.OFF;
    }
  
    
    /* Return true if this is a RF channel */
    @Override public boolean isRf() {
        return true; 
    }
    
    
    /*
     * State of channel: OFF, STARTING, RUNNING, FAILED
     */
    public State getState() {
        if (_serial == null)
            return _state;
        return _state = _serial.getState(); 
    }
   
   
   /**
    * Start the service. 
    * Load/reload configuration parameters. Called each time channel is activated. 
    */
    public void activate(AprsServerConfig a) {
        resetCounters();
        String id = getIdent();
        _myCall = _conf.getProperty("channel."+id+".mycall", "").toUpperCase();
        if (_myCall.length() == 0)
           _myCall = _conf.getProperty("default.mycall", "NOCALL").toUpperCase(); 
        _log = new Logfile(_conf, id, "rf.log");   
        
        String port = _conf.getProperty("channel."+id+".port", "/dev/ttyS0");
        int baud = _conf.getIntProperty("channel."+id+".baud", 9600);
        int retr = _conf.getIntProperty("channel."+id+".retry", 0);
        long rtime = Long.parseLong(_conf.getProperty("channel."+id+".retry.time", "30")) * 60 * 1000; 
        _serial = new SerialComm(_conf, id, port, baud, retr, rtime);
        _serial.activate( ()-> receiveLoop(), ()->{} );
    }

  
    public boolean isActive() 
        { return (_serial != null) && _serial.running(); }
  
  
  
    /** Stop the service */
    public void deActivate() {
        if (isActive())
            close();
    }
    
    
    @Override protected void regHeard(AprsPacket p) {
        _heard.put(p.from, new Heard(new Date(), p.via));
        /* FIXME: Check for TCPxx in third party packets and consider registering 
         * more than one path instance */
    }
 

}

