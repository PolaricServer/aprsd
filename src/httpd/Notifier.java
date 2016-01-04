package no.polaric.aprsd.http;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.io.IOException;
import org.simpleframework.http.Cookie;
import org.simpleframework.http.Request;
import org.simpleframework.http.socket.*;
import org.simpleframework.http.socket.service.Service;
import java.util.function.*;
import com.fasterxml.jackson.databind.*;



public abstract class Notifier implements Service 
{

   public abstract class Client implements FrameListener {
   
      protected final FrameChannel _chan; 
      protected final long _uid;
   
   
      public Client(FrameChannel ch, long uid) {
         _chan = ch;
         _uid = uid;
      }
   
   
      public void send(Frame frame) throws IOException
         { _chan.send(frame); }
      
   
      public void sendText(String text) throws IOException
         { send(new DataFrame(FrameType.TEXT, text)); }
   
   
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

        
   /* Jackson JSON mapper */ 
   protected final static ObjectMapper mapper = new ObjectMapper();
   
   /* Map of clients */ 
   protected final Map<Long, FrameListener> _clients;
      
   
   public Notifier() {
      _clients = new ConcurrentHashMap<Long, FrameListener>();
   }  
     
     
   /* Factory method */
   public abstract Client newClient(FrameChannel ch, long uid);
     
   
   
   /* FIXME: This method is copied from ServerBase. Should be in a base-class */
   protected long _sessions = 0;
   protected synchronized long getSession(Request req)
      throws IOException
   {
      String s_str  = req.getParameter("clientses");
      if (s_str != null && s_str.matches("[0-9]+")) {
         long s_id = Long.parseLong(s_str);
         if (s_id > 0)
            return s_id;
      }
      _sessions = (_sessions +1) % 2000000000;
      return _sessions;       
   }
   
   
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
          
          if (subscribe(uid, client, req)) {
             System.out.println("Subscription success. User="+uid);
             _clients.put(uid, client); 
          }
          else 
             System.out.println("Subscription rejected. User="+uid);
          
      } catch(Exception e) {
          System.out.println("Subscription failed: " + e);
      }  
   }
   
   
   
   /**
    * Authorize and subscribe client. Should be overridden in subclass. 
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
          System.out.println("*** Error. Cannot serialize object: "+e);
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
               _clients.remove(user); /* Unsubscribe */
               client.close();
               System.out.println("*** Error. Cannot send string: " + e);
            }
         }
      } catch(Exception e) {
         System.out.println("*** Error. Cannot distribute string: " + e);
      }
   } 
   
   
}
