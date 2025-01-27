 
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
import java.util.concurrent.*;
import java.net.*;
import java.io.*;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonSubTypes.*;


/**
 * Channel that can act as a simple APRS-IS server.
 * It is primary aimed at igates to let them connect directly to a Polaric Server instance. 
 * A limited set of filters are supported. It listens to a port. To listen to multiple ports, 
 * start multiple instances of this. To connect it to the APRS-IS network use it with a Router-channel
 * and an APRS-IS channel. 
 */
 
public class InetSrvChannel extends AprsChannel implements Runnable {
        
    private List<Client> _clients = new LinkedList<Client>();
    private int _nclients = 0;
    private int _portnr;
    private Thread _serverthread = new Thread(this);

    public static class Client {
    }
    
    
    public InetSrvChannel(ServerAPI api, String id) 
    {
        _init(api, "channel", id);
        _api = api;
        _state = State.OFF;
    }
    
       
    /** Stop the service */
    @Override public void activate(ServerAPI a) {
        String id = getIdent();            
        _state = State.STARTING;
        _portnr = _api.getIntProperty("channel."+id+".port", 14580);
        _serverthread.start(); 
    }
    
        
    /** Stop the service */
    @Override public void deActivate() {
        _state = State.OFF;
        try {
            _serverthread.join();
        }
        catch (Exception e) {}
    }
    
    
    
    /* 
     * Information about config to be exchanged in REST API
     */
    
    @JsonTypeName("APRSIS-SRV")
    public static class JsConfig extends Channel.JsConfig {
        public long nclients, heardpackets, heard, duplicates, sentpackets; 
        public int port; 
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
        return cnf;
    }
    
    
    public void setJsConfig(Channel.JsConfig ccnf) {
        var cnf = (JsConfig) ccnf;
        var props = _api.getConfig();
        props.setProperty("channel."+getIdent()+".port", ""+cnf.port);
    }
    
    
    
    public List<Client> getClients() {
        return _clients;
    }
    
    public void removeClient(InetSrvClient c) {
        _clients.remove(c);
        _nclients--;
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
    public boolean sendPacket(AprsPacket p, InetSrvClient except)
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
        boolean accepted = receivePacket(p, false);
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
                        Client worker = new InetSrvClient(_api, conn, this);
                        _clients.add(worker);
                        _nclients++;
                    }
                }
                catch (SocketTimeoutException e) {}
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
































































































