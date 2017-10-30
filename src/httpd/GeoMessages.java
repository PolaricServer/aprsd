package no.polaric.aprsd.http;
import no.polaric.aprsd.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.io.IOException;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import java.net.*;


/**
 * Instant messaging. 
 */
@WebSocket(maxIdleTime=360000)
public class GeoMessages extends WsNotifier implements ServerAPI.Mbox
{

   public class Client extends WsNotifier.Client 
   {   
       public Client(Session conn) 
          { super(conn); }
             
       
       @Override synchronized public void onTextFrame(String text) {
            Message m = (Message) deserializeJson(text, Message.class);
            m.senderId = _uid;
            m.msgId = _msgId++;
            if (m.time==null)
               m.time = new Date();
            m.from = _auth.userid;
            postMessage(m);
       }
   }
   
   
   /** Message content to be exchanged */
   public static class Message { 
       public String senderId;
       public long msgId;
       public Date time; 
       public String from, to; 
       public String text;
   }


   /* Note that if we want more than one instance of the set of messages/users,
    * these static variables may be factored out as a class
    */
   private static long _msgId = 1;
   private List<Message> _lastMsgs = new LinkedList<Message>();
   
   
   
   public Set<String> getUsers() { 
       Set<String> u = new TreeSet<String>();
       for (WsNotifier.Client x: _clients.values()) 
          if (x!=null) 
             u.add(x.getUsername());
       return u; 
   }
      
      
   public GeoMessages(ServerAPI api, boolean trusted)
      { super(api, trusted); }  
    
    
   /** Factory method. */
   @Override public WsNotifier.Client newClient(Session conn) 
      { return new Client(conn); }

   
   /** Create and post a message */  
   public void postMessage(String from, String to, String txt)
   {
       Message m = new Message(); 
       m.msgId = _msgId++;
       m.from = from; 
       m.to = to; 
       m.time = new Date();
       m.text = txt;
       postMessage(m);
   }
   
   
   /** Post a message */
   public void postMessage(Message msg) {
        if (msg.to != null) {
           synchronized(this) {
             _lastMsgs.add(msg);
             if (_lastMsgs.size() > 30)
               _lastMsgs.remove(0);
           } 
           
           postObject(msg, x -> 
               msg.to.equals(x._auth.userid) ||
              (msg.to.matches("ALL-SAR") && x._auth.sar) ||
              (msg.to.matches("ALL-LOGIN") && x.login())
            );
            
        }

   }

}
