 
/* 
 * Copyright (C) 2022-23 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import java.io.*;
import java.net.http.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.time.Duration;
import no.polaric.aprsd.ServerAPI;


/**
 * Client side of websocket. 
 */

public class NodeWsClient implements WebSocket.Listener{
    
    private ServerAPI _api;
    private WebSocket _wsClient; 
    private URI _url;
    private HttpClient _ht;
    private String _nodeid;
    private String _subscribe;
    private boolean _connected = false;
    private HmacAuth _auth; 
    private NodeWsApi.Handler<String> _handler; 
    private boolean _retry = false;
    private long _retr_int = 0;
    private Timer hb = new Timer();
    
    
    public NodeWsClient(ServerAPI api, String nodeid, String url, boolean retry) {
        _api=api;
        _nodeid=nodeid;
        _retry = retry;
        _auth = new HmacAuth(_api, "httpserver.auth.key");
        try {
            _url=new URI(url);
            _ht = HttpClient.newHttpClient();
            open();
        }
        catch (URISyntaxException e) {
            _api.log().error("NodeWsClient", "Syntax error in URI: "+_url);
        }
    }
    
    
    
    
    public void setHandler(NodeWsApi.Handler<String> h) {
        _handler = h;
    }
    
    
    
    public void open() {
        try {
            _wsClient = _ht.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .buildAsync(_url, this)
                .get();
        }
        catch (Exception e) {
            _api.log().warn("NodeWsClient", "Websocket connect exception: "+e.getMessage());
           // e.printStackTrace(System.out);
            retry();
        }
    }
    
    
    
    public void close() {
        _wsClient.sendClose​(WebSocket.NORMAL_CLOSURE, "");
        _retry = false;
    }
    
    
    
    public boolean isConnected() {
        return _connected;
    }
    
    
    
    public boolean subscribe(String nodeid) {
        _subscribe = nodeid;
        return putCommand("SUBSCRIBE", nodeid);
    }
    
    
    
    public void unsubscribe() {
        _subscribe = null;
        putCommand("UNSUBSCRIBE", "");
    }
    
    
    
    public boolean putText(String msg) {
        return putCommand("POST", msg);
    }
    
    
    
    public boolean putCommand(String cmd, String msg) {
        _api.log().debug("NodeWsClient", "Post message: "+cmd);

        if (!_connected) {
            _api.log().warn("NodeWsClient", "Node not connected: "+_url);
            return false;
        }
        _wsClient.sendText(cmd+" "+_auth.genAuthPrefix(msg)+" "+msg, true);
        return true;
    }
    
                
    /** Post a object to the connected node (JSON encoded) */
    public boolean put(Object obj) 
        { return putText(ServerBase.toJson(obj)); }
        
        
    
    @Override
   public void onError​(WebSocket webSocket, Throwable error) {
        _api.log().warn("NodeWsClient", "Error: "+error);
        error.printStackTrace(System.out);
        _connected = false;
    }

    
    
    @Override
    public void onOpen(WebSocket webSocket) {       
        webSocket.request(1); 
        _retr_int = 0;
        _connected = true;
    }
    
    
    
    @Override
    public CompletionStage<?> onClose​(WebSocket webSocket, int statusCode, String reason) {
        _api.log().debug("NodeWsClient", "Connection closed. Statuscode: "+ statusCode + " "+reason);
        _connected = false;
        retry();
        return null;
    }
    
    
    private void retry() {
        if (_retry)
            hb.schedule( new TimerTask() 
                { public void run() {       
                    if (!_connected) open();    
                    if (_connected && _subscribe != null)
                        subscribe(_subscribe);
                } 
            }, retr_delay() ); 
    }
    
    
    
    /* Semi-exponential backoff for retries */ 
    private long retr_delay() {
        if (_retr_int == 0)
            _retr_int = 60000;     // Min 60 seconds
        else
            _retr_int = _retr_int * 2;
        if (_retr_int >= 3600000) 
            _retr_int = 3600000;   // Max 1 hour
        return _retr_int;
    }
    
    
    
    @Override
    public CompletionStage<?> onText​(WebSocket webSocket, CharSequence data, boolean last) {
        if (_handler != null) {
            String[] parms = data.toString().split(" ", 4);
            if (parms.length < 4) 
                _api.log().warn("NodeWsClient", "Format error in message");
            else if (parms[0].equals("POST")) {
                if (!_auth.checkAuth(parms[1], parms[2], parms[3])) 
                    _api.log().warn("NodeWsClient", "Authentication failed");
                else
                    _handler.recv(_nodeid, parms[3]);
            }
        }
        webSocket.request(1);
        return null;
    }
    
}

































































































