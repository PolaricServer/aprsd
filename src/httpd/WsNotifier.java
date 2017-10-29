/* 
 * Copyright (C) 2017 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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

import spark.*;
import java.util.Map;
import java.util.Set;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.io.IOException;
import java.util.function.*;
import java.net.*;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.UpgradeRequest;
import org.eclipse.jetty.websocket.api.WebSocketException;
import java.security.Principal;
import com.owlike.genson.*;
import no.polaric.aprsd.*;
import com.mindprod.base64.Base64;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;


/** Send events to clients through WebSockets. Let clients subscribe. */

@WebSocket(maxIdleTime=360000)
public abstract class WsNotifier extends ServerBase
{
   
   public abstract class Client {
   
      protected final Session _conn; 
      protected final String _uid;
      protected AuthInfo _auth;
      protected long _nIn, _nOut; 
      protected Date _ctime; 
      
   
      public Client(Session conn) {
         _conn = conn;
         _uid = _getUid(conn);
         _ctime = new Date();
      }
   
   
      public final void setAuthInfo(AuthInfo auth) {
         _auth = auth;
      }
      
      public final boolean login() 
         { return _auth != null && _auth.userid != null; }
   
      public void sendText(String text) throws IOException 
         { _nOut++; _conn.getRemote().sendString(text); }
   
      public  String getUid()
         { return _uid; }
         
      public Session getSession() 
         { return _conn; }
         
      public String getUsername()
         { return (_auth == null ? null : _auth.userid); }
         
      // FIXME: Is this used?    
      public void close() throws IOException { 
         _conn.close(); 
         _clients.remove(_uid);
      }
           
      public Date created() {return _ctime; }
      public long nIn() {return _nIn; }
      public long nOut() {return _nOut; }

      /** 
       * Handler for text frame. To be defined in subclass.
       */
      public abstract void onTextFrame(String text);
      
   } /* class Client */

   
   
   /* Count number of logged in users */
   private int _nLoggedIn;
      
   
   /* Count number of visits */
   private long _visits = 0;
   
   /* Is this instance on a trusted configuration. */
   private boolean _trusted = false;
   
   /* Origin site. 
    * Trusted origin sites (regular expression) 
    */
   private String _origin;
   private String _trustedOrigin; 
         

   /* Genson object mapper */
   protected final static Genson mapper = new Genson();
   
   
   /* Map of clients */ 
   protected final Map<String, Client> _clients;
      
   
   public WsNotifier(ServerAPI api, boolean trusted) {
       super(api); 
      _trustedOrigin = _api.getProperty("trusted.orgin", ".*");
      _trusted = trusted;
      _clients = new ConcurrentHashMap<String, Client>();
   }  
     
     
   /** Factory method */
   public abstract Client newClient(Session conn);
   
     
   /** Return number of visits */
   public long nVisits()
     { return _visits; }
     
   /** Return number of logged in clients */
   public int nLoggedIn()
     { return _nLoggedIn;}
     
   /** Return number of clients. */
   public int nClients() 
     { return _clients.size(); }
     

   /** Return collection of clients */
   public Collection<Client> clients()
     { return _clients.values(); }
     
     
   public boolean trusted()
     { return _trusted; }

    
   /** 
    * Webscoket Connect handler. Subscribe to the service (join the room). 
    * Use remote IP + port as user id. FIXME: Is this enough? More than one simultaneous user 
    * per client endpoint? 
    */
   
   @OnWebSocketConnect
   public void onConnect(Session conn) {
      try {
          UpgradeRequest req = conn.getUpgradeRequest();    
          String uid = _getUid(conn);
          
          /* Check origin */
          _origin = req.getOrigin();
          if (_origin != null && _origin.matches(_trustedOrigin)) 
          { 
              /* Create client and set authorization info */
              Client client = newClient(conn);
              client.setAuthInfo(getAuthInfo(req));
                 
              if (subscribe(uid, client)) {
                 _api.log().debug("WsNotifier", "Subscription success. User="+uid);
                 _clients.put(uid, client); 
                 _visits++;
                 if (client.login())
                    _nLoggedIn++;
              }
              else {
                 _api.log().info("WsNotifier", "Subscription rejected. User="+uid);
                 conn.close();
              }
           }
           else
              _api.log().info("WsNotifier", "Subscription rejected. Untrusted origin='"+_origin+"', user="+uid);
          
      } catch(Exception e) {
          _api.log().warn("WsNotifier", "Subscription failed: " + e);
          if (e instanceof NullPointerException)
            e.printStackTrace(System.out);
      }  
   }
   
   
      
    /**
     * Websocket close handler.
     */
     
    @OnWebSocketClose
    public void onClose(Session conn, int statusCode, String reason) {
       String user = _getUid(conn);
       _api.log().info("WsNotifier", "Connection closed"+(reason==null ? "" : ": "+reason)+". Unsubscribing user: "+user);
       Client c = _clients.get(user);
       if (c.login())
          _nLoggedIn--;
       _clients.remove(user);
       
    }

   
   
    /**
     * Websocket Message handler.
     */
     
    @OnWebSocketMessage
    public void onMessage(Session conn, String message) {
        Client c = _clients.get(_getUid(conn));
        if (c != null && message.length() > 0) {
           c._nIn++;
           c.onTextFrame(message);
        }
    }
    
    
    private String _getUid(Session conn) {
       UpgradeRequest req = conn.getUpgradeRequest();
       InetSocketAddress remote = conn.getRemoteAddress();
       String host = req.getHeader("X-Forwarded-For");
       if (host == null) 
          host = remote.getHostString();
       return host+":"+remote.getPort();
    }
    
    
    
   /**
    * Return true if user is to be trusted. To be overridden by subclass. 
    */
    
   protected boolean trustUser(String uid, Session conn) {
      return false; 
   }

   
   
   /**
    * Get username of the authenticated user. 
    * @return username, null if not authenticated. 
    */
   protected final AuthInfo getAuthInfo(UpgradeRequest req)
   {
       ServletUpgradeRequest rr = (ServletUpgradeRequest) req; 
       AuthInfo auth = (AuthInfo) rr.getHttpServletRequest​().getAttribute("authinfo");
       return auth;
   }
   
   
   
   /**
    * Subscribe a client to the service. Should be overridden in subclass.
    * This may include authorization, preferences, etc.. 
    * @return true if subscription is accepted. False if rejected.
    */
   public boolean subscribe(String uid, Client client) 
      { return true; }
   
   
          
   /**
    * Distribute a object (as JSON) to the clients for which the 
    * predicate evaluates to true. 
    */
   public void postObject(Object myObj, Predicate<Client> pred) {
        postText(mapper.serialize(myObj), pred); 
   }
   


   
   /**
    * Distribute a text to the clients for which the 
    * predicate evaluates to true. 
    */
   public void postText(Function<Client,String> txt, Predicate<Client> pred) {
      try {          
         /* Distribute to all clients */
         for(String user : _clients.keySet()) {
            Client client = (Client) _clients.get(user);
            try {               
               if(client != null && pred.test(client) && txt != null) 
                  client.sendText(txt.apply(client));
               
            } catch(WebSocketException e){   
                _clients.remove(user); 
                _api.log().info("WsNotifier", "Unsubscribing closed client channel: "+user);       
                Client c = _clients.get(user);
                if (c.login())
                   _nLoggedIn--;
            }
         }
      } catch(Exception e) {
         _api.log().error("WsNotifier", "Cannot distribute string: " + e);
         e.printStackTrace(System.out);
      }
   } 
   
   
   
   public void postText(String txt, Predicate<Client> pred) {
        postText(c->txt, pred);
   }
   
}
