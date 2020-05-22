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
    protected String  _myCall; /* Move this ? */
    
    transient private   Semaphore    _sem = new Semaphore(1, true);
    transient protected Logfile      _log; 
    transient private   int          _chno;
    transient protected SerialComm   _serial;
    
    private static int _next_chno = 0;
    
    
    /*
     * We need to define the receiveLoop method for the SerialComm class. 
     */
    protected class Comm extends SerialComm {
        public @Override void receiveLoop() throws Exception 
            { receiveLoop(); }
        public Comm(ServerAPI api, String id, String pname, int bd, int retr, long rtime)
            { super(api, id, pname, bd, retr, rtime); }
    }
       
    
    /*
     * These are to be defined in subclasses 
     */
    public abstract void close();   
    protected abstract void receiveLoop() throws Exception;
    
    
 
    public TncChannel(ServerAPI api, String id) 
    {
        _init(api, "channel", id);
        _api = api;
        _chno = _next_chno;
        _next_chno++;
        _state = State.OFF;
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

        _serial = new Comm(_api, id, port, baud, retr, rtime);
    }

  
    /** Stop the service */
    public void deActivate() {
        close();
    }
    
    
    @Override protected void regHeard(AprsPacket p) {
        _heard.put(p.from, new Heard(new Date(), p.via));
        /* FIXME: Check for TCPxx in third party packets and consider registering 
         * more than one path instance */
    }
 
    
    @Override public String getShortDescr()
       { return "rf"+_chno; }
 


    public String toString() { return "TNC Channel"; }

}

