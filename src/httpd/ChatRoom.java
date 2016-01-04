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

public class ChatRoom extends Notifier 
{

   public class Client extends Notifier.Client 
   {
       public Client(FrameChannel ch, long uid) 
          { super(ch, uid); }
             
       /* On text frame from client. Just replay text to all clients. */
       @Override public void onTextFrame(Request request, String text) {
           String replay = "(" + _uid + ") " +text;
           distribute(_uid, replay);
       }
   }
     
    
    
   public ChatRoom()
     { super(); }  
    
   
   /** Factory method */
   @Override public Notifier.Client newClient(FrameChannel ch, long uid) 
     { return new Client(ch, uid); }
   
   
   
   /**
    * Distribute a message to the other clients in the room. 
    */
   public void distribute(long from, String txt) {
        postText(txt, x -> x._uid != from);
          /* Note the use of a lambda expression. It tells to distribute message 
           * to all except thee one who sent it. 
           */ 
   }
}
