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
             
       @Override public void onTextFrame(Request request, String text) {
           Frame replay = new DataFrame(FrameType.TEXT, "(" + _uid + ") " +text);
           distribute(_uid, replay);
       }
   }
     
    
    
   public ChatRoom()
     { super(); }  
    
   
   @Override public Notifier.Client newClient(FrameChannel ch, long uid) 
     { return new Client(ch, uid); }
   
   
   
   /**
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
