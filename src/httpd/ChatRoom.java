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



/* Should this class extend ServerBase? */

public class ChatRoom implements Service 
{
   
   private final Map<Long, FrameListener> _clients;
      
   
   public ChatRoom() {
      _clients = new ConcurrentHashMap<Long, FrameListener>();
   }  
     
   
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
   
   
   /* 
    * Connect. Join the room. The user id is a long int which is 
    * assigned automatically.
    */
   public void connect(Session connection) {
      try {
          FrameChannel chan = connection.getChannel();
          Request req = connection.getRequest();      
          long uid = getSession(req); 
          System.out.println("User '"+uid+"' joined");
          FrameListener client = new ChatRoomClient(this, chan, uid);
          chan.register(client );
          _clients.put(uid, client); /* Subscribe */
      } catch(Exception e) {
          System.out.println("Problem joining: " + e);
      }  
   }
   

   /*
    * Distribute a message to the other clients in the room. 
    */
   public void distribute(long from, Frame frame) {
      try {          
         for(long user : _clients.keySet()) {
            ChatRoomClient client = (ChatRoomClient) _clients.get(user);
            
            try {               
               if(from != user) {
                  client.send(frame);
               }
            } catch(Exception e){   
               _clients.remove(user); /* Unsubscribe */
               client.close();
               System.out.println("Problem sending message: " + e);
            }
         }
      } catch(Exception e) {
         System.out.println("Problem distributing message: " + e);
      }
   }
}
