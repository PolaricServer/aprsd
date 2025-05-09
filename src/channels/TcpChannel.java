 
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
    protected TcpComm  _comm;
    

    
    public TcpChannel(ServerAPI api, String id) 
    {
        _init(api, "channel", id);
        _api = api;
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
        resetCounters();
        String id = getIdent();
        String host = _api.getProperty("channel."+id+".host", "localhost");
        int port = _api.getIntProperty("channel."+id+".port", 21);
        int retr  = _api.getIntProperty("channel."+id+".retry", 4);
        long rtime = Long.parseLong(_api.getProperty("channel."+id+".retry.time", "10")) * 60 * 1000;
        
        /* Set up backup channel */
        _backup = _api.getProperty("channel."+id+".backup", "");
        if (!_api.getChanManager().isBackup(_backup))
            _api.getChanManager().addBackup(_backup); 
        
        /* If backup channel is running, stop it */
        Channel back = _api.getChanManager().get(_backup);
        if (back !=null && back.isActive())
            back.deActivate();
        
        /* Set up comm device */
        _comm = new TcpComm(_api, id, host, port, retr, rtime);
        _comm.activate( 
            ()-> receiveLoop(),
            ()-> {
                    var bu = _api.getChanManager().get(_backup);
                    if (bu != null) 
                        bu.activate(a);
               }
         );
    }

    
    
    /** Stop the service */
    public void deActivate() {
        try {
            _state = State.OFF;
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

