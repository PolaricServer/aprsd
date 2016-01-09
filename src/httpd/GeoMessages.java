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




/* Should this class extend ServerBase? */

public class GeoMessages extends Notifier 
{

   public class Client extends Notifier.Client 
   {
       public boolean admin, sar, login; 
       
       public Client(FrameChannel ch, long uid) 
          { super(ch, uid); }
             
       @Override public void onTextFrame(Request request, String text) {
          System.out.println("onTextFrame: "+text);
          try {
            Message m = mapper.readValue(text, Message.class);
            postMessage(m);
         } catch (IOException e) 
             {System.out.println("ERROR: Cannot parse message: "+e);}
       }
   }
   
   
   public static class Message { 
       public String senderId;
       public String from, to; 
       public String text;
   }

   
   
   public GeoMessages(ServerAPI api) throws IOException
      { super(api); }  
    
    
   /* Factory method */
   @Override public Notifier.Client newClient(FrameChannel ch, long uid) 
      { return new Client(ch, uid); }

      
   
   @Override public boolean subscribe(long uid, Notifier.Client client, Request req) { 
       /* 
        * Should do authorization here. Can also do things 
        * like getting user name, preferences, geographical area, etc.. 
        */
       return true; 
   }
   
   public void postMessage(Message msg) {
        System.out.println("postMessage: to="+msg.to+", uid="+msg.senderId);
        if (msg.to != null)
          postObject(msg, x -> 
               msg.to.matches("ALL") || 
              (msg.to.matches("ALL-SAR") && ((Client)x).sar) ||
              (msg.to.matches("ALL-LOGIN") && ((Client)x).login)          
            );

   }

}
