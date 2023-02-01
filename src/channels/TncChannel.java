/* 
 * Copyright (C) 2016-2020 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import java.io.*;
import java.util.*;
import gnu.io.*;
import java.util.concurrent.Semaphore;


/**
 * TNC channel. For devices in TNC2 compatible mode.
 */
 
public abstract class TncChannel extends AprsChannel
{
    protected String      _myCall; /* Move this ? */
    private   Semaphore   _sem = new Semaphore(1, true);
    protected Logfile     _log; 
    private   int         _chno;
    protected SerialComm  _serial;
    
    private static int _next_chno = 0;
    
   
    
    /*
     * These are to be defined in subclasses 
     */
    protected abstract void close();   
    protected abstract void receiveLoop() throws Exception;
    
    
 
    public TncChannel(ServerAPI api, String id) 
    {
        _init(api, "channel", id);
        _api = api;
        _chno = _next_chno;
        _next_chno++;
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
    public void activate(ServerAPI a) {
        String id = getIdent();
        _myCall = _api.getProperty("channel."+id+".mycall", "").toUpperCase();
        if (_myCall.length() == 0)
           _myCall = _api.getProperty("default.mycall", "NOCALL").toUpperCase(); 
        _log = new Logfile(_api, id, "rf.log");   
        
        String port = _api.getProperty("channel."+id+".port", "/dev/ttyS0");
        int baud = _api.getIntProperty("channel."+id+".baud", 9600);
        int retr = _api.getIntProperty("channel."+id+".retry", 0);
        long rtime = Long.parseLong(_api.getProperty("channel."+id+".retry.time", "30")) * 60 * 1000; 
        _serial = new SerialComm(_api, id, port, baud, retr, rtime);
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
 
    
    @Override public String getShortDescr()
       { return "rf"+_chno; }


}

