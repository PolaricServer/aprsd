 
package no.polaric.aprsd;
import java.util.*;

   /** 
    * This is the main API. The interface to the core server application - to be used by
    * plugins 
    */
   public interface ServerAPI 
   {
      public interface ServerStats {
         public int getClients();
         public int getReq();
      }
   
      /* Now, what methods do we need here? Other interfaces.
       * Do we need StationDB? */
       public StationDB getDB();
       
       public Channel.Manager getChanManager(); 
       
       public Igate getIgate();
       
       public MessageProcessor getMsgProcessor(); 
       
       public ServerStats getHttps();
       
       public void addHttpHandler(Object obj, String prefix);
       
       public AprsHandler getAprsHandler();
       
       public void setAprsHandler(AprsHandler log);
       
       public RemoteCtl getRemoteCtl(); 
       
       public OwnPosition getOwnPos(); 
       
       public Properties getConfig();
       
       public Map<String, Object> getObjectMap(); // Need this?
       
       public String getVersion();
       
       public String getToAddr();
       
       public SarUrl getSarUrl();
       
       public SarMode getSar();
    
       public void setSar(String src, String filt, String descr);
       
       public void clearSar();
   }
   