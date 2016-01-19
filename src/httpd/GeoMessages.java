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




public class GeoMessages extends Notifier 
{

   public class Client extends Notifier.Client 
   {   
       public Client(FrameChannel ch, long uid) 
          { super(ch, uid); }
             
       
       @Override synchronized public void onTextFrame(Request request, String text) {
          try {
            Message m = mapper.readValue(text, Message.class);
            m.senderId = _uid;
            m.msgId = _msgId++;
            m.from = _username;
            postMessage(m);
         } catch (IOException e) 
             {System.out.println("ERROR: Cannot parse message: "+e);}
       }
   }
   
   
   public static class Message { 
       public long senderId;
       public long msgId;
       public String from, to; 
       public String text;
   }


   private static long _msgId = 1;
   
   public GeoMessages(ServerAPI api, boolean trusted) throws IOException
      { super(api, trusted); }  
    
    
   /* Factory method */
   @Override public Notifier.Client newClient(FrameChannel ch, long uid) 
      { return new Client(ch, uid); }

      
   /**
    * Subscribe a client to the service. 
    * This may include authorization, preferences, etc.. 
    * @return true if subscription is accepted. False if rejected. 
    */
   @Override public boolean subscribe(long uid, Notifier.Client client, Request req)  { 
       /* TBD. Do we really need this? */
       return true; 
   }
   
   
   public void postMessage(Message msg) {
        if (msg.to != null)
          postObject(msg, x -> 
               msg.to.equals(x._username) ||
               msg.to.matches("ALL") || 
              (msg.to.matches("ALL-SAR") && x._sar) ||
              (msg.to.matches("ALL-LOGIN") && x._login)
            );

   }

}
