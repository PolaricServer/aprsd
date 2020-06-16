 
/* 
 * Copyright (C) 2020 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import java.net.*;
import java.util.*;
import java.util.concurrent.*;


/**
 * TCP channel. Connect to a server on the internet. 
 */
public abstract class TcpChannel extends AprsChannel
{
    protected boolean  _close = false;
    protected String   _backup; 
    protected int      _chno;
    protected TcpComm  _comm;
        
    private static int _next_chno = 0;
    

    
    public TcpChannel(ServerAPI api, String id) 
    {
        _init(api, "channel", id);
        _api = api;
        _chno = _next_chno;
        _next_chno++;
        _state = State.OFF;
    }
 
 
 
    /*
     * State of channel: OFF, STARTING, RUNNING, FAILED
     */
    public State getState() {
        if (_comm == null)
            return _state;
        return _state = _comm.getState(); 
    }
    

 
    /** Start the service */
    public void activate(ServerAPI a) {
        String id = getIdent();
        String host = _api.getProperty("channel."+id+".host", "localhost");
        int port = _api.getIntProperty("channel."+id+".port", 21);
        int retr  = _api.getIntProperty("channel."+id+".retry", 4);
        long rtime = Long.parseLong(_api.getProperty("channel."+id+".retry.time", "10")) * 60 * 1000;
        
        /* Set up backup channel */
        _backup = _api.getProperty("channel."+id+".backup", "");
        _api.getChanManager().addBackup(_backup); 
        
        /* Set up comm device */
        _comm = new TcpComm(_api, id, host, port, retr, rtime);
        _comm.activate( 
            ()-> receiveLoop(),
            ()-> _api.getChanManager().get(_backup).activate(a)
         );
    }

    
    
    /** Stop the service */
    public void deActivate() {
        try {
            _comm.deActivate();
            Thread.sleep(1000);
            _close();
        } catch (Exception e) {}  
    }
        
    
    
    public String getHost() 
        { return _comm.getHost(); }
    
     
    
    protected abstract void _close(); // Need this???
    protected abstract void receiveLoop() throws Exception;

    
}

