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
import java.util.*;
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
import org.eclipse.jetty.websocket.api.CloseException;
import org.eclipse.jetty.websocket.api.WriteCallback;
import java.security.Principal;
import no.polaric.aprsd.*;
import com.mindprod.base64.Base64;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;


/** Send events to clients through WebSockets. Let clients subscribe. */

@WebSocket(maxIdleTime=1200000)
public abstract class WsNotifier extends ServerBase
{
   
   /* Handler for changes to clients */
   public interface CHandler {
        public void handle(Client c); 
   }
   
   
   /********* Client *********/
   public abstract class Client implements WebClient {
   
      protected final Session _conn; 
      protected final String _uid;
      protected AuthInfo _auth;
      protected long _nIn, _nOut; 
      protected Date _ctime; 
      private   boolean _mobile;
      
      
      protected class _CB implements WriteCallback {
        public void writeFailed(Throwable x) {
            _api.log().warn("WsNotifier", "sendText write failed: "+x.getMessage());
        }
        public void writeSuccess() {
        }
        
      }
      
   
      public Client(Session conn) {
         _conn = conn;
         _uid = _getUid(conn);
         _ctime = new Date();
      }
   
   
      public final void setAuthInfo(AuthInfo auth) {
         _api.log().debug("WsNotifier", "setAuthInfo: "+auth);
         _auth = auth;
      }
      
      public final AuthInfo getAuthInfo() {
         return _auth;
      }
      
      public final boolean login() 
         { return _auth != null && _auth.userid != null; }
   
      public void sendText(String text) throws IOException {
          if (_conn == null || _conn.getRemote() == null) {
              _api.log().warn("WsNotifier", "sendText. Connection is null");
              return;
          }
          if (text == null) text="";
          _nOut++; 
          _conn.getRemote().sendString(text, new _CB()); 
       }
   
      public  String getUid()
         { return _uid; }
         
      public Session getSession() 
         { return _conn; }
         
      public String getUsername()
         { return (_auth == null ? null : _auth.userid); }
           
      public boolean isMobile() {return _mobile;}
      public Date created() {return _ctime; }
      public long nIn() {return _nIn; }
      public long nOut() {return _nOut; }

      /** 
       * Handler for text frame. To be defined in subclass.
       */
      public abstract void onTextFrame(String text);
      
   } /********* class Client *********/

   
   
   /* Count number of logged in users */
   private int _nLoggedIn;
      
   
   /* Count number of visits and logins */
   private long _visits = 0;
   private long _logins = 0;
   
   /* Is this instance on a trusted configuration. */
   private boolean _trusted = false;
   
   /* Origin site. 
    * Trusted origin sites (regular expression) 
    */
   private String _origin;
   private String _trustedOrigin; 
   
   
   /* Map of clients */ 
   protected final Map<String, Client> _clients;
      
   /* Callbacks for open and close of sessions */
   private List<CHandler> _cOpen = new ArrayList<CHandler>();
   private List<CHandler> _cClose = new ArrayList<CHandler>();   
   
   
   
   
   
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
     
   public long nLogins()
     { return _logins; }
     
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

    
    
    
   public AuthInfo authenticate(String qstring) {
      String[] params = null; 
      if (qstring != null) {
         params = qstring.split(";");
         if (params.length < 3 || params.length > 4) {
            _api.log().info("WsNotifier", "Authentication failed, wrong format of query string");
            return null;
         }
      }
      try { 
         HmacAuthenticator auth = AuthService.conf().getHmacAuth();
         String rname = (params.length == 4 ? params[3] : null);
         User ui = auth.checkAuth(params[0], params[1], params[2], "");
         Group grp = auth.getRole(ui, rname);
         return new AuthInfo(_api, ui, grp); 
      }
      catch (Exception e) {}
      return null;
   }
   
   
    
    
   /** 
    * Websocket Connect handler. Subscribe to the service (join the room). 
    * Use remote IP + port as user id. 
    */
   
   @OnWebSocketConnect
   public void onConnect(Session conn) {
      try {
          String uid = _getUid(conn);
          UpgradeRequest req = conn.getUpgradeRequest(); 
          String qstring = req.getQueryString();
          _api.log().debug("WsNotifier", "onConnect - query string: "+qstring);
          
          /* Check origin */
          _origin = req.getOrigin();
          if (_origin == null || _origin.matches(_trustedOrigin))
            // FIXME: Is this secure enough for web-browser clients?
          { 
              /* Create client, autenticate and set authorization info */
              Client client = newClient(conn);
              String[] qs = null;
              if (qstring != null) {
                  qs = qstring.split("&");
                  if ("_MOBILE_".equals(qs[0]))
                     client._mobile=true;
              }       
              client.setAuthInfo(authenticate(
                 (qstring == null ? null :  (qs.length == 1 ? qstring : qs[1]))
              ));
                 
              if (subscribe(uid, client)) {
                 _api.log().debug("WsNotifier", "Subscription success. User="+uid);
                 _clients.put(uid, client); 
                 _visits++;
                         
                 
                 /* Call any functions that are registered for handling this */
                 for (CHandler c: _cOpen)
                    c.handle(client);
                    
                 if (client.login()) {
                    _nLoggedIn++;
                    _logins++;
                }
              }
              else {
                 _api.log().info("WsNotifier", "Subscription rejected by subclass::subscribe. User="+uid);
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
     * Close the client session.
     */
    protected void closeSes(Session conn) {
        String user = _getUid(conn);
        if (conn==null || user==null)
            return;
        Client c = _clients.get(user);
        if (c==null) {
            _api.log().warn("WsNotifier", "closeSes: user "+user+" not found");
            return;
        }
        if (c.login())
          _nLoggedIn--;
        _clients.remove(user);
        
        /* Call any functions that are registered for handling this */
        for (CHandler h : _cClose)
            h.handle(c);
    }
    
    
    
    
    /**
     * Register a callback for open-session events
     */
    public void onOpenSes(CHandler h) {
        _cOpen.add(h);  
    }
    
    
    /**
     * Register a callback for close-session events
     */
    public void onCloseSes(CHandler h) {
        _cClose.add(h);  
    }
    
    
    
   
    
    /**
     * Websocket error handler.
     */
    @OnWebSocketError
    public void onError(Session conn, Throwable cause) {   
        String user = (conn==null ? "(null)" : _getUid(conn));
        if (cause instanceof CloseException) {
            _api.log().debug("WsNotifier", "Websocket close ["+user+"]: "+cause.getCause());
            return; 
        }
        else if (cause instanceof NullPointerException) {
            _api.log().warn("WsNotifier", "Websocket NullPointerException");
            cause.printStackTrace(System.out);
        }
        else {
            _api.log().warn("WsNotifier", "Websocket ["+user+"]: "+cause);
            closeSes(conn);
        }
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
          
       
       
    
    protected String _getUid(Session conn) {
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
    * Get username,etc of the authenticated user. 
    * @return username, null if not authenticated. 
    * FIXME: This uses auth info on session - not used in the new auth scheme - remove 
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
        postText(serializeJson(myObj), pred); 
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
                if (client != null && pred.test(client) && txt != null) 
                    client.sendText(txt.apply(client));
               
            } catch (java.nio.channels.ClosedChannelException e) {   
                _clients.remove(user); 
                _api.log().info("WsNotifier", "Unsubscribing closed client channel: "+user);       
                Client c = _clients.get(user);
                if (c!= null && c.login())
                   _nLoggedIn--;
            }
         }
      } 
      catch (Exception e) {
         _api.log().error("WsNotifier", "Cannot distribute string: " + e);
         e.printStackTrace(System.out);
      }
   } 
   
   
   
   public void postText(String txt, Predicate<Client> pred) {
        postText(c->txt, pred);
   }
   
}
