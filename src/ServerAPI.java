 
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
       
       public MessageProcessor getMsgProcessor(); 
       
       public void addHttpHandler(Object obj, String prefix);
       
       public AprsHandler getAprsHandler();
       
       public void setAprsHandler(AprsHandler log);
       
       public RemoteCtl getRemoteCtl(); 
       
       public OwnPosition getOwnPos(); 
       
       public Properties getConfig();
       
       public Map<String, Object> getObjectMap(); // Need this?
       
       public String getVersion();
       
       public String getToAddr();
       
       public SarMode getSar();
    
       public void setSar(String src, String filt, String descr);
       
       public void clearSar();
   }
   