package no.polaric.aprsd.http;

import spark.*;
import java.util.Map;
import java.util.Set;
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





/** Send events to clients through WebSockets. Let clients subscribe. */

@WebSocket
public abstract class WsNotifier extends ServerBase
{
   
   public abstract class Client {
   
      protected final Session _conn; 
      protected final InetSocketAddress _uid;
    
      protected boolean _admin=false, _sar=false, _login=false; 
      protected String _username;
   
   
      public Client(Session conn) {
         _conn = conn;
         _uid = conn.getRemoteAddress();
      }
   
   
      public final void setUsername(String uname) {
            _username = uname; 
            _admin = authorizedForAdmin(_username);
            _sar = authorizedForUpdate(_username);
              // FIXME: Should we have an Authorization class? 
                            
            _login = (_username != null);
       }
       
   
      public void sendText(String text) throws IOException 
         { _conn.getRemote().sendString(text); }
   
      public  InetSocketAddress getUid()
         { return _uid; }
         
      public Session getSession() 
         { return _conn; }
         
      public String getUsername()
         { return _username; }
         
      public void close() throws IOException { 
         _conn.close(); 
         _clients.remove(_uid);
      }
      

      /** 
       * Handler for text frame. To be defined in subclass.
       */
      public abstract void onTextFrame(String text);
      
   } /* class Client */

   
   
   
   
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
   protected final Map<InetSocketAddress, Client> _clients;
      
   
   public WsNotifier(ServerAPI api, boolean trusted) {
       super(api); 
      _trustedOrigin = _api.getProperty("trusted.orgin", ".*");
      _trusted = trusted;
      _clients = new ConcurrentHashMap<InetSocketAddress, Client>();
   }  
     
     
   /** Factory method */
   public abstract Client newClient(Session conn);
     
     
   /** Return number of visits */
   public long nVisits()
     { return _visits; }
     
     
   /** Return number of clients. */
   public int nClients() 
     { return _clients.size(); }
     
   
   

    
   /** 
    * Webscoket Connect handler. Subscribe to the service (join the room). 
    * Use remote IP + port as user id. FIXME: Is this enough? More than one simultaneous user 
    * per client endpoint? 
    */
   
   @OnWebSocketConnect
   public void onConnect(Session conn) {
      try {
          UpgradeRequest req = conn.getUpgradeRequest();    
          InetSocketAddress uid = conn.getRemoteAddress();
          
          /* Check origin */
          _origin = req.getOrigin();
          if (_origin != null && _origin.matches(_trustedOrigin)) 
          { 
              Client client = newClient(conn); 

              /* We need to be sure that we can trust that the user
               * is who he says he is. Can we trust that getAuthUser is authenticated
               * if not, try to identify and authenticate. 
               */
              if (_trusted || trustUser(uid, conn))
                  client.setUsername(getAuthUser(req));
                 
              if (subscribe(uid, client)) {
                 _api.log().info("WsNotifier", "Subscription success. User="+uid+(_trusted ? " (trusted chan)" : ""));
                 _clients.put(uid, client); 
                 _visits++;
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
       _api.log().info("WsNotifier", "Connection closed: "+reason);
       _clients.remove(conn.getRemoteAddress());
    }

   
   
    /**
     * Websocket Message handler.
     */
     
    @OnWebSocketMessage
    public void onMessage(Session conn, String message) {
        Client c = _clients.get(conn.getRemoteAddress());
        if (c != null)
           c.onTextFrame(message);
    }
    
    
    
   /**
    * Return true if user is to be trusted. To be overridden by subclass. 
    */
    
   protected boolean trustUser(InetSocketAddress uid, Session conn) {
      return false; 
   }

   
   
   /**
    * Get username of the authenticated user. 
    * @return username, null if not authenticated. 
    */
   protected final String getAuthUser(UpgradeRequest req)
   {
         String auth = req.getHeader("authorization");
         if (auth==null)
            auth = req.getHeader("Authorization");
         return getAuthUser(auth);
   }
   
   
   /**
    * Subscribe a client to the service. Should be overridden in subclass.
    * This may include authorization, preferences, etc.. 
    * @return true if subscription is accepted. False if rejected.
    */
   public boolean subscribe(InetSocketAddress uid, Client client) 
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
         for(InetSocketAddress user : _clients.keySet()) {
            Client client = (Client) _clients.get(user);
            try {               
               if(client != null && pred.test(client) && txt != null) 
                  client.sendText(txt.apply(client));
               
            } catch(Exception e){   
               if (e.getCause() instanceof WebSocketException) {
                  _clients.remove(user); 
                  _api.log().info("WsNotifier", "Unsubscribing closed client channel: "+user);       
               }
               else throw e;
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
