/* 
 * Copyright (C) 2026 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import no.polaric.core.*;
import no.polaric.aprsd.*;
import no.polaric.aprsd.aprs.*;
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
    
    
    public Router(AprsServerConfig conf, String id) {  
        _init(conf, "channel", id);
        _conf = conf;
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
        
        String chans  =  _conf.getProperty("channel."+getIdent()+".channels", "");
        if (chans.length() > 0) {
            String[] chnames = chans.split(",(\\s)*");
            for (String ch : chnames) {
                /* Add subchannel if a corresponding channel exists in config */
                if (_conf.getProperty("channel."+ch+".type",null) != null) {
                    String filt =  _conf.getProperty("channel."+getIdent()+".filter."+ch, "*");
                    cnf.channels.add(new SubChan(ch, filt));
                }
                else
                    /* Clean up */
                    _conf.config().remove("channel."+getIdent()+".filter."+ch); 
            }
        }
        cnf.type  = "ROUTER";
        return cnf;
    }
    
    
    
    public void setJsConfig(Channel.JsConfig ccnf) {
        var cnf = (JsConfig) ccnf;
        var props = _conf.config();  
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
    @Override public void activate(AprsServerConfig a) {        
        String id = getIdent();
       _state = State.RUNNING;
        resetCounters();
        
        log = new Logfile(_conf, "channel."+id, "channel."+id+".log");  
        log.info(null, "Channel activated");
        _conf.log().info(null, "Channel activated: "+id);       
               
        /* Configure, Get contained channels */
        String chn = _conf.getProperty("channel."+getIdent()+".channels", "");
        _chnames = chn.split(",(\\s)*");
        _channels = new AprsChannel[_chnames.length];
        _filters = new AprsFilter[_chnames.length];
        log.info(null, "Connecting to "+_chnames.length+" channels");
        
        for (int i=0; i<_chnames.length; i++) {
            String type = _conf.getProperty("channel."+_chnames[i]+".type", "");
            Channel ch = _conf.getChanManager().get(_chnames[i]);
            if (ch == null) {
                ch = _conf.getChanManager().newInstance(_conf, type, _chnames[i]); 
                if (_conf.getBoolProperty("channel."+_chnames[i]+".on", false))
                    ch.activate(_conf);
            }    
            
            log.info(null, "--> Channel "+_chnames[i]+" : " + type);
            if (ch instanceof AprsChannel ach) {
                ach.setInRouter(this);
                _channels[i] = ach;
                String filt = _conf.getProperty("channel."+getIdent()+".filter."+_chnames[i], "*");
                _filters[i] = AprsFilter.createFilter( filt, null );
            }
            else {
                log.warn(null, "Channel '"+_chnames[i]+"' isn't an AprsChannel");
                _channels[i] = null;
            }
            if (_channels[i] == null)
                log.warn(null, "Channel '"+_chnames[i]+"' is set to null");
        }
       
       /* Set up a receiver and use it to subscribe to the contained channels */
       _recv = new Receiver() {
            @Override public void receivePacket(AprsPacket p, boolean dup) {
                if (dup)
                    return;
                sendPacket(p);
                Router.this.receivePacket(p, dup);
            }
        };
       
       for (AprsChannel ch: _channels)
            if (ch != null) {
                ch.removeReceiver(_conf.getAprsParser()  );
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
        log.info(null, "Channel deactivated");
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
                if (ch == null) 
                    continue;
                else if (p.source == null || !ch.getIdent().equals(p.source.getIdent()))
                {
                    if ( (filt==null || filt.test(p)) && ch.sendPacket(p)) 
                        sent = true;
                }
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
       
       if (_rfilterPattern != null && !_rfilterPattern.matcher(p.toString()).matches())
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

