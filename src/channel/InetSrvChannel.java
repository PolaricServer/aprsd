/* 
 * Copyright (C) 2025 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.net.*;
import java.io.*;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonSubTypes.*;


/**
 * Channel that can act as a simple APRS-IS server.
 * It is primary aimed at igates to let them connect directly to a Polaric Server instance. 
 * A set of filters are supported. It listens to a port. To listen to multiple ports, start 
 * multiple instances of this. To connect it to the APRS-IS network use it with a Router-channel
 * and an APRS-IS channel. 
 */
 
public class InetSrvChannel extends AprsChannel implements Runnable {
        
    private List<InetSrvClient> _clients = new LinkedList<InetSrvClient>();
    private Set<String> _logins = new HashSet<String>();
    
    private int _nclients = 0;
    private int _portnr;
    private AprsFilter _filter;
    private String _defaultfilt;
    private Thread _serverthread;

    public static class Client {
    }
    
    
    public InetSrvChannel(AprsServerAPI api, String id) 
    {
        _init(api, "channel", id);
        _api = api;
        _state = State.OFF;
    }
    
       
    /** Start the service */
    @Override public void activate(AprsServerAPI a) {
        resetCounters();
        String id = getIdent();            
        _state = State.STARTING;
        _portnr = _api.getIntProperty("channel."+id+".port", 14580);
        String filt =  _api.getProperty("channel."+id+".infilter", "*");
        _filter = AprsFilter.createFilter( filt, null);
        _defaultfilt = _api.getProperty("channel."+id+".defaultfilt", "");
        
        _serverthread = new Thread(this);
        _serverthread.start(); 
    }
    
        
    /** Stop the service */
    @Override public synchronized void deActivate() {
        if (_state == State.OFF)
            return;
        _state = State.OFF;
        try {
            /* Close all clients */
            for (InetSrvClient x: _clients)
                x.close();
            _serverthread.join();
        }
        catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }
    
    
    
    /* 
     * Information about config to be exchanged in REST API
     */
    
    @JsonTypeName("APRSIS-SRV")
    public static class JsConfig extends Channel.JsConfig {
        public long nclients, heardpackets, heard, duplicates, sentpackets; 
        public int port; 
        public String filter;
        public String defaultfilt;
    }
       
       
    public JsConfig getJsConfig() {
        var cnf = new JsConfig();
        cnf.nclients = _nclients;
        cnf.heard = nHeard();
        cnf.heardpackets = nHeardPackets(); 
        cnf.duplicates = nDuplicates(); 
        cnf.sentpackets = nSentPackets();
        cnf.type  = "APRSIS-SRV";
        cnf.port  = _api.getIntProperty("channel."+getIdent()+".port", 14580);
        cnf.filter = _api.getProperty("channel."+getIdent()+".infilter", "*");
        cnf.defaultfilt = _api.getProperty("channel."+getIdent()+".defaultfilt", "");
        return cnf;
    }
    
    
    public void setJsConfig(Channel.JsConfig ccnf) {
        if (getIdent().equals(""))
            return;
        var cnf = (JsConfig) ccnf;
        var props = _api.getConfig();
        props.setProperty("channel."+getIdent()+".port", ""+cnf.port);
        props.setProperty("channel."+getIdent()+".infilter", ""+cnf.filter);
        props.setProperty("channel."+getIdent()+".defaultfilt", ""+cnf.defaultfilt);
    }
    
    
    public String defaultfilt() {
        return _defaultfilt;
    }
    
    public List<InetSrvClient> getClients() {
        return _clients;
    }
    
    public synchronized void removeClient(InetSrvClient c) {
        _clients.remove(c);
        _nclients--;
    }
    
    public void addLogin(String login) {
        _logins.add(login);
    }
    
    public void removeLogin(String login) {
        _logins.remove(login);
    }
    
    public boolean hasLogin(String login) {
        return _logins.contains(login);
    }
    
    
    
    // Do we need some modifications here? 
    @Override protected void regHeard(AprsPacket p)
    {
        if (p.via.matches(".*(TCPIP\\*|TCPXX\\*).*"))
           _heard.put(p.from, new Heard(new Date(), p.via));
    }
    
    
    /**
     * Outgoing packet. Distribute to connected clients.
     */
    public synchronized boolean sendPacket(AprsPacket p, InetSrvClient except)
    {     
        boolean sent = false; 
        for (Client x : _clients)
            if (x instanceof InetSrvClient c && c != except)
                if (c.sendPacket(p)) sent = true;
        if (sent)
            _sent++;
        return sent;
    }
    
    public boolean sendPacket(AprsPacket p) {
        return sendPacket(p, null);
    }
    
    
    
    /**
     * Handle incoming packet. To be called from clients. 
     */
    public void handlePacket(AprsPacket p) {
        if (_filter.test(p))
            receivePacket(p, false);
    }
    
    
    
    public void run() {
        ServerSocket server = null;

        try {
            server = new ServerSocket(_portnr);
            server.setSoTimeout(10000);
            _api.log().info("InetSrvChannel", "Listening on port "+_portnr);
            _state = State.RUNNING;
            
            while (_state == State.RUNNING) {
                /* Wait for incoming connections */
                try {
                    Socket conn = server.accept(); 
                    synchronized(this) {
                        InetSrvClient worker = new InetSrvClient(_api, conn, this);
                        _clients.add(worker);
                        _nclients++;
                    }
                }
                catch (SocketTimeoutException e) {
                }
            }
        }
        catch (Exception ex) {
            _api.log().warn("InetSrvChannel", "ERROR: "+ex.getMessage());
        }
        finally {
            try {
                _api.log().info("InetSrvChannel","CLOSING");
                if (server!=null) 
                    server.close();
            } catch (Exception e) {}
        }
    }
    
}

































































































