package no.polaric.aprsd.http;
import no.polaric.aprsd.*;

import java.util.*;
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
            if (m.time==null)
               m.time = new Date();
            m.from = _username;
            postMessage(m);
         } catch (IOException e) 
             { _api.log().error("GeoMessages", "Cannot parse message: "+e); }
       }
   }
   
   
   public static class Message { 
       public long senderId;
       public long msgId;
       public Date time; 
       public String from, to; 
       public String text;
   }


   private static long _msgId = 1;
   private List<Message> _lastMsgs = new LinkedList<Message>();
    // Lag metode for å liste meldinger. 
    // HTML web interface for å sende melding og se liste. Obs. Oppdatere lista når nye meldinger legges til? 
    //   callback? Callback til klient via denne websocket? 
   
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
        if (msg.to != null) {
           _lastMsgs.add(msg);
           if (_lastMsgs.size() > 30)
              _lastMsgs.remove(0);
              
           postObject(msg, x -> 
               msg.to.equals(x._username) ||
               msg.to.matches("ALL") || 
              (msg.to.matches("ALL-SAR") && x._sar) ||
              (msg.to.matches("ALL-LOGIN") && x._login)
            );
            
        }

   }

}
