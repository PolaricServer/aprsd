/* 
 * Copyright (C) 2022-2023 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
 
package no.polaric.aprsd.http;
import no.polaric.aprsd.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.io.IOException;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import java.net.*;
import java.util.function.*;



/**
 * WebSocket communiction between Polaric Server backend nodes. 
 */
 
@WebSocket(maxIdleTime=1200000)
public class NodeWs extends WsNotifier 
{
    private HmacAuth _hmauth;
    private NodeWsApi.Handler<String> _handler;
    
    
    
    public class Client extends WsNotifier.Client
    {   
        public Client(Session conn) { 
            super(conn); 
        }
             
        public String nodeid;
        
       
        @Override synchronized public void onTextFrame(String text) {
            String[] parms = text.split(" ", 4);
            if (parms.length < 4) {
                _api.log().warn("NodeWs", "Format error in message");
                _conn.close();
            }
            else
            if ( !_hmauth.checkAuth(parms[1], parms[2], parms[3])) {
                _api.log().warn("NodeWs", "Authentication failed");
                _conn.close();
            }
            else
            switch (parms[0]) {
                /* subscribe: 
                 * arguments: nonce, mac, ident
                 */
                case "SUBSCRIBE":
                    nodeid = parms[3];
                    _subscribers.put(parms[3], this);
                    break;
                   
                 /* unsubscribe:
                 * arguments: nonce, mac, ident
                 */
                case "UNSUBSCRIBE":
                    _subscribers.remove(parms[3]);
                    break;
                    
                /* post
                 * arguments: nonce, mac, JSON-encoded content
                 */
                case "POST": 
                    if (_handler != null) _handler.recv(nodeid, parms[3]);
                    break;
            
                default: 
                    break;
            }
        }
    }
   
   
    private HashMap<String, Client> _subscribers = new HashMap<String,Client>(); 
    
            
        
    public NodeWs(ServerAPI api, NodeWsApi.Handler<String> hdl) { 
        super(api, false); 
        _hmauth = new HmacAuth(api, "httpserver.auth.key");
        _handler = hdl;
    }  
   
   
   
    public Set<String> getSubscribers() {
        return _subscribers.keySet();
    }
    
    
    
    public void setHandler(NodeWsApi.Handler<String> h) {
        _handler = h;
    }
    
   
    /**
     * Websocket close handler.
     */
    @OnWebSocketClose
    public void onClose(Session conn, int statusCode, String reason) {
       String user = _getUid(conn);
       closeSes(conn);
       Client c = (Client) _clients.get(user);
       _subscribers.remove(c.nodeid);
    }
   
    
    
    /** 
      * Post a message to node. Returns true if sending was successful.
      */
    public boolean putText(String nodeid, String msg) {
        _api.log().debug("NodeWs", "Post message to: "+nodeid);
        
        Client client = (Client) _subscribers.get(nodeid);
        if (client == null) {
            _api.log().warn("NodeWs", "Node not connected: "+nodeid);
            return false;
        }
        try {               
            client.sendText("POST "+_hmauth.genAuthPrefix(msg) + " " + msg);
            return true;
               
        } catch (java.nio.channels.ClosedChannelException e) {   
            _clients.remove(client.getUid());
            _subscribers.remove(nodeid);
            _api.log().debug("NodeWs", "Unsubscribing closed client channel: "+nodeid);
            return false;
        } catch (IOException e) {
            e.printStackTrace(System.out);
            _api.log().warn("NodeWs", "IOException, client channel: "+nodeid);
            return false;
        }
    }
        
            
    /** Post a object to a node (JSON encoded) */
    public boolean put(String nodeid, Object obj) 
        { return putText(nodeid, toJson(obj)); }
        

    
    /** Factory method. */
    @Override public WsNotifier.Client newClient(Session conn) 
        { return new Client(conn); }


}
