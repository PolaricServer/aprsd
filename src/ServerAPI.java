 
package no.polaric.aprsd;
import java.util.*;

   /** 
    * This is the main API. The interface to the core server application - to be used by
    * plugins 
    */
   public interface ServerAPI 
   {
      /* Now, what methods do we need here? Other interfaces.
       * Do we need StationDB? */
       public StationDB getDB();
       
       public Set<String> getChannels(Channel.Type type);
       
       public Channel getChannel(String id);
       
       public void addChannel(Channel.Type type, String id, Channel ch);
       
       public Igate getIgate();
       
       public MessageProcessor getMsgProcessor(); /* Move from StationDB */
       
       public RemoteCtl getRemoteCtl(); 
       
       public Properties getConfig();
       
       public Map<String, Object> getObjectMap();
       
       public String getVersion();
   }
   