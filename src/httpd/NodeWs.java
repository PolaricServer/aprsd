/* 
 * Copyright (C) 2022-2024 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import org.eclipse.jetty.websocket.api.WebSocketException;
import java.net.*;
import java.util.function.*;



/**
 * WebSocket communiction between Polaric Server backend nodes. 
 */
 
@WebSocket(maxIdleTime=1200000)
public class NodeWs extends WsNotifier 
{
    private NodeWsApi.Handler<String> _handler;
    private Timer hb = new Timer();
    
    
    public class Client extends WsNotifier.Client
    {   
        public Client(Session conn) { 
            super(conn); 
        }
             
        public String nodeid;
      

      
      
        @Override synchronized public void onTextFrame(String text) {
            String[] parms = text.split(" ", 2);
            if (parms.length < 2) {
                _api.log().warn("NodeWs", "Format error in message");
                _conn.close();
            }
            else
            switch (parms[0]) {
                /* subscribe: 
                 * arguments: ident
                 */
                case "SUBSCRIBE":
                case "SUB":
                    nodeid = parms[1];
                    _subscribers.put(parms[1], this);
                    break;
                   
                 /* unsubscribe:
                 * arguments: ident
                 */
                case "UNSUBSCRIBE":
                case "UNSUB":
                    _subscribers.remove(parms[1]);
                    break;
                    
                /* post
                 * arguments: JSON-encoded content
                 */
                case "POST": 
                case "MSG":
                    if (_handler != null) _handler.recv(nodeid, parms[1]);
                    break;
            
                default: 
                    break;
            }
        }
    }
   
   
    private HashMap<String, Client> _subscribers = new HashMap<String,Client>(); 
    
            
        
    public NodeWs(ServerAPI api, NodeWsApi.Handler<String> hdl) { 
        super(api, false); 
        _handler = hdl;
        
        hb.schedule( new TimerTask() { 
            public void run() {
                for (Client c : _subscribers.values())
                    putText(c.nodeid, null); // PING each node every 2 minutes
            } 
        }, 120000, 120000); 
    }  
   
   
   
    public Set<String> getSubscribers() {
        return _subscribers.keySet();
    }
    
    
    public void removeSubscriber(String id) {
        _subscribers.remove(id);
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
            if (msg == null) 
                return client.sendText("PING");
            else    
                return client.sendText("POST " + msg);
               
        } catch (java.nio.channels.ClosedChannelException | WebSocketException e) {   
            _clients.remove(client.getUid());
            _subscribers.remove(nodeid);
            _api.log().debug("NodeWs", "Unsubscribing closed client channel: "+nodeid+", "+e.getCause());
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
