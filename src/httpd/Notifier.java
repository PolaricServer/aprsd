package no.polaric.aprsd.http;
import no.polaric.aprsd.*;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.io.IOException;
import org.simpleframework.http.Cookie;
import org.simpleframework.http.Request;
import org.simpleframework.http.socket.*;
import org.simpleframework.http.socket.service.Service;
import org.simpleframework.transport.*;
import java.util.function.*;
import com.fasterxml.jackson.databind.*;



public abstract class Notifier extends ServerBase implements Service
{

   public abstract class Client implements FrameListener {
   
      protected final FrameChannel _chan; 
      protected final long _uid;
    
      protected boolean _admin=false, _sar=false, _login=false; 
      protected String _username;
   
   
      public Client(FrameChannel ch, long uid) {
         _chan = ch;
         _uid = uid;
      }
   
   
      public final void setUsername(String uname) {
            _username = uname; 
            _admin = authorizedForAdmin(_username);
            _sar = authorizedForUpdate(_username);
            _login = (_username != null);
       }
       
   
      public void send(Frame frame) throws IOException
         { _chan.send(frame); }
      
   
      public void sendText(String text) throws IOException
         { send(new DataFrame(FrameType.TEXT, text)); }
   
      public long getUid() 
         { return _uid; }
         
      public String getUsername()
         { return _username; }
         
      public void close() throws IOException
         { _chan.close(); }
      
   
      public void onFrame(Session socket, Frame frame) {
         FrameType type = frame.getType();
         String text = frame.getText();
         Request request = socket.getRequest();
         if(type == FrameType.TEXT) 
             onTextFrame(request, text);
      }

      
      /** 
       * Handler for text frame. To be defined in subclass.
       */
      public abstract void onTextFrame(Request request, String text);
      
   
      public void onError(Session socket, Exception cause) {
         System.out.println("onError (" + cause + ")");
         cause.printStackTrace(System.out);
      }

   
      public void onClose(Session session, Reason reason) {
         System.out.println("onClose (" + reason + ")");
      }
   }

   /* Is this instance on a trusted configuration. */
   private boolean _trusted = false;
         
   /* Jackson JSON mapper */ 
   protected final static ObjectMapper mapper = new ObjectMapper();
   
   /* Map of clients */ 
   protected final Map<Long, FrameListener> _clients;
      
   
   public Notifier(ServerAPI api, boolean trusted) throws IOException {
       super(api);
      _trusted = trusted;
      _clients = new ConcurrentHashMap<Long, FrameListener>();
   }  
     
     
   /* Factory method */
   public abstract Client newClient(FrameChannel ch, long uid);
     

     
   
   /** 
    * Connect. Join the room. The user id is a long int which is 
    * assigned automatically.
    */
   public void connect(Session connection) {
      try {
          Request req = connection.getRequest();      
          long uid = getSession(req); 
          FrameChannel chan = connection.getChannel();
          Client client = newClient(chan, uid); 
          chan.register(client );
          
          /* We need to be sure that we can trust that the user
           * is who he says he is. Can we trust that getAuthUser is authenticated
           * if not, try to identify and authenticate. 
           */
          if (_trusted || trustUser(uid, req))
                client.setUsername(getAuthUser(req));
                 
          if (subscribe(uid, client, req)) {
             _api.log().info("Notifier", "Subscription success. User="+uid);
             _clients.put(uid, client); 
          }
          else 
             _api.log().info("Notifier", "Subscription rejected. User="+uid);
          
      } catch(Exception e) {
          _api.log().warn("Notifier", "Subscription failed: " + e);
      }  
   }
   
   
   
   protected boolean trustUser(long uid, Request req) {
      return false; 
   }
   
   
   /**
    * Subscribe a client to the service. Should be overridden in subclass.
    * This may include authorization, preferences, etc.. 
    * @return true if subscription is accepted. False if rejected.
    */
   public boolean subscribe(long uid, Client client, Request req) 
      { return true; }
   
   
     
   /**
    * Distribute a object (as JSON) to the clients for which the 
    * predicate evaluates to true. 
    */
   public void postObject(Object myObj, Predicate<Client> pred) { 
      try { postText(mapper.writeValueAsString(myObj), pred); }
      catch (Exception e) {
          _api.log().warn("Notifier", "Cannot serialize object: " + e);
      }
   }
   
   
   /**
    * Distribute a text to the clients for which the 
    * predicate evaluates to true. 
    */
   public void postText(String txt, Predicate<Client> pred) {
      try {          
         /* Distribute to all clients */
         for(long user : _clients.keySet()) {
            Client client = (Client) _clients.get(user);
            try {               
               if(pred.test(client)) 
                  client.sendText(txt);
               
            } catch(Exception e){   
               if (e.getCause() instanceof TransportException) {
                  _clients.remove(user); 
                  _api.log().info("Notifier", "Unsubscribing closed client channel: "+user);       
               }
               else throw e;
            }
         }
      } catch(Exception e) {
         _api.log().error("Notifier", "Cannot distribute string: " + e);
         e.printStackTrace(System.out);
      }
   } 
   
   
}
