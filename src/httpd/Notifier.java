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
   
   
      public void close() throws IOException
         { _chan.close(); }
      
   
      public void onFrame(Session socket, Frame frame) {
         FrameType type = frame.getType();
         String text = frame.getText();
         Request request = socket.getRequest();
         if(type == FrameType.TEXT) 
             onTextFrame(request, text);
         
         System.out.println("onFrame (" + type + ")");
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

   
   
   /* Map of clients */ 
   protected final Map<Long, FrameListener> _clients;
      
   
   public Notifier() {
      _clients = new ConcurrentHashMap<Long, FrameListener>();
   }  
     
 
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
          FrameChannel chan = connection.getChannel();
          Request req = connection.getRequest();      
          long uid = getSession(req); 
          System.out.println("User '"+uid+"' joined");
          FrameListener client = newClient(chan, uid); 
          chan.register(client );
          _clients.put(uid, client); /* Subscribe */
          
      } catch(Exception e) {
          System.out.println("Problem joining: " + e);
      }  
   }
   
}
