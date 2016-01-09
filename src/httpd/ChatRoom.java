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



/**
 * Chat room using Websockets. 
 * This class is mainly to test and demonstrate the use of Websockets. 
 */

public class ChatRoom extends Notifier 
{

   public static class Message {
     public long uid; 
     public String text; 
   }
   
   
   
   public class Client extends Notifier.Client 
   {
       public Client(FrameChannel ch, long uid) 
          { super(ch, uid); }
             
       /* On text frame from client. Just replay text to all clients. */
       @Override public void onTextFrame(Request request, String text) {
           Message m = new Message(); 
           m.uid = _uid; 
           m.text = text; 
           distribute(m);
       }
   }
     
    
    
   public ChatRoom(ServerAPI api) throws IOException
     { super(api); }  
    
    
   
   /** Factory method */
   @Override public Notifier.Client newClient(FrameChannel ch, long uid) 
     { return new Client(ch, uid); }
   
   
   
   /**
    * Distribute a message to the other clients in the room. 
    */
   public void distribute(Message m) {
        postObject(m, x -> x._uid != m.uid);
          /* Note the use of a lambda expression. It tells to distribute message 
           * to all except thee one who sent it. 
           */ 
   }
}
