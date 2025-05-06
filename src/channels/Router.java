 
/* 
 * Copyright (C) 2025 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonSubTypes.*;


/**
 * Channel that just combines other channels. 
 * Act as a router.  
 * 
 */
 
public class Router extends AprsChannel
{
    private String[] _chnames;
    private AprsChannel[] _channels;
    private AprsFilter[] _filters;
    private Receiver _recv;
    
    
    public Router(ServerAPI api, String id) {  
        _init(api, "channel", id);
        _api = api;
    }
       
    
    /* 
     * Information about config to be exchanged in REST API
     */
    
    @JsonTypeName("ROUTER")
    public static class JsConfig extends Channel.JsConfig {
        public long heardpackets, heard, duplicates, sentpackets; 
        public List<SubChan> channels;
    }
    
       
    public static record SubChan (String name, String filter) {}
    
    
       
    public JsConfig getJsConfig() {
        var cnf = new JsConfig();
        cnf.heard = nHeard();
        cnf.heardpackets = nHeardPackets(); 
        cnf.duplicates = nDuplicates(); 
        cnf.sentpackets = nSentPackets();
        cnf.channels = new ArrayList<SubChan>();
        
        String chans  =  _api.getProperty("channel."+getIdent()+".channels", "");
        if (chans.length() > 0) {
            String[] chnames = chans.split(",(\\s)*");
            for (String ch : chnames) {
                /* Add subchannel if a corresponding channel exists in config */
                if (_api.getProperty("channel."+ch+".type",null) != null) {
                    String filt =  _api.getProperty("channel."+getIdent()+".filter."+ch, "*");
                    cnf.channels.add(new SubChan(ch, filt));
                }
                else
                    /* Clean up */
                    _api.getConfig().remove("channel."+getIdent()+".filter."+ch); 
            }
        }
        cnf.type  = "ROUTER";
        return cnf;
    }
    
    
    
    public void setJsConfig(Channel.JsConfig ccnf) {
        var cnf = (JsConfig) ccnf;
        var props = _api.getConfig();  
        var chanlist = ""; 
        
        for (SubChan ch: cnf.channels) {
            /* Insert subchannel if a corresponding channel exists in config */
            if (props.getProperty("channel."+ch.name+".type", null) != null) {
                chanlist += ch.name + ",";
                props.setProperty("channel."+getIdent()+".filter."+ch.name, ch.filter);
            }
        }
        if (chanlist.length() > 0)
            chanlist = chanlist.substring(0, chanlist.length()-1);
        props.setProperty("channel."+getIdent()+".channels", ""+chanlist);
    }
    
    
    
    /** 
     * Start the service 
     */
    @Override public void activate(ServerAPI a) {        
       _state = State.RUNNING;
       _sent=0;
       
       /* Configure, Get contained channels */
       String chn = _api.getProperty("channel."+getIdent()+".channels", "");
        _chnames = chn.split(",(\\s)*");
        _channels = new AprsChannel[_chnames.length];
        _filters = new AprsFilter[_chnames.length];
        
        for (int i=0; i<_chnames.length; i++) {
            Channel ch = _api.getChanManager().get(_chnames[i]);
            if (ch == null) {
                String type = _api.getProperty("channel."+_chnames[i]+".type", "");
                ch = _api.getChanManager().newInstance(_api, type, _chnames[i]); 
                if (_api.getBoolProperty("channel."+_chnames[i]+".on", false))
                    ch.activate(_api);
                // See also Main.java
            }    
            if (ch instanceof AprsChannel ach) {
                ach.setInRouter(this);
                _channels[i] = ach;
                String filt = _api.getProperty("channel."+getIdent()+".filter."+_chnames[i], "*");
                _filters[i] = AprsFilter.createFilter( filt );
            }
            else
                _channels[i] = null;
        }
       
       /* Set up a receiver and use it to subscribe to the contained channels */
       _recv = new Receiver() {
            @Override public void receivePacket(AprsPacket p, boolean dup) {
                sendPacket(p);
                Router.this.receivePacket(p, dup);
            }
        };
       
       for (AprsChannel ch: _channels)
            if (ch != null) {
                ch.removeReceiver(_api.getAprsParser()  );
                ch.addReceiver(_recv);
            }
    }
    
    
        
    /** 
     * Stop the service. 
     */
    @Override public void deActivate() {
        _state = State.OFF;
              
        if (_channels != null)
            for (AprsChannel ch: _channels)
                if (ch != null) {
                    ch.removeReceiver(_recv);
                    ch.setInRouter(null);
                }
        _recv = null;
    }
    
    
    
    /** 
     * Send packet to contained channels, except the one it came from 
     */
    public boolean sendPacket(AprsPacket p) {
        boolean sent = false; 
        int i=0;
        
        if (_state == State.RUNNING)
            for (AprsChannel ch : _channels) {
                AprsFilter filt = _filters[i++];  
                if (p.source == null || 
                   (ch != null && p.source != null && !ch.getIdent().equals(p.source.getIdent())))
                   
                    if ( (filt==null || filt.test(p)) && ch.sendPacket(p)) 
                        sent = true;
            }
        if (sent)
            _sent++;
        return sent;
    }
        
        
        
    /**
     * Process incoming packet. 
     * @param p Pre-parsed packet.
     * @param dup True if packet is known to be a duplicate.
     * @return true if accepted.
     */
    @Override protected boolean receivePacket(AprsPacket p, boolean dup)
    {      
       if (p == null)
          return false; 
      
       p = checkReport(p); 
       if (p==null)
          return false;
       
       if (_rfilter != null && !_rfilter.equals("") && !p.toString().matches(_rfilter))
          return false; 
          
       _heardPackets++;
          
       /* Pass the packet to registered receivers: Aprs-parser, igate, etc.. */
       for (Receiver r: _rcv)
           r.receivePacket(p, dup);
       return !dup;
    }
        
        
    protected void regHeard(AprsPacket p) {
    }
    
    
    
}

