 
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
       
       /** Get data interface. */
       public StationDB getDB();
       
       
       /** Get channel manager interface. */
       public Channel.Manager getChanManager(); 
       
       
       /** Get igate. */
       public Igate getIgate();
       
       
       /** Get message processor interface. */
       public MessageProcessor getMsgProcessor(); 
       
       
       /** Get server statistics. */
       public ServerStats getHttps();
       
       
       /** Add HTTP handlers (webservices).
        * A http handler is a class/object that offers a set of methods to handle
        * incoming HTTP requests. A method with prefix <code>handle_</code> that has a Request
        * and Response parameter are automatically assumed to be webservices. A method 
        * <code>handle_XXX</code> will then for instance be made available as a
        * webservice on <code>URL http://server:port/XXX</code>.
        *
        * @param obj    Object that offers a set of handler methods. 
        * @param prefix Optional URL prefix for services of this object. 
        */
       public void addHttpHandler(Object obj, String prefix);
       
       
       /** Add HTTP handlers (webservices).
        * @param class  Class name. 
        * @param prefix Optional URL prefix for services of this object. 
        */
       public void addHttpHandlerCls(String cn, String prefix);
       
       
       /** Get APRS incoming packet handler object. */
       public AprsHandler getAprsHandler();
       
       
       /** Set APRS incoming packet handler object. */
       public void setAprsHandler(AprsHandler log);
       
       
       /** Get handler for remote control. */
       public RemoteCtl getRemoteCtl(); 
       
       
       /** Get handler for own position */
       public OwnPosition getOwnPos(); 
       
       
       /** Get configuration properties. */
       public Properties getConfig();
       
       
       /** Get string configuration property. 
        * @param pn property name.
        * @param dval default value. 
        */
       public String getProperty (String pn, String dval); 
       
       
       /** Get boolean configuration property. 
        * @param pn property name.
        * @param dval default value. 
        */
       public boolean getBoolProperty (String pn, boolean dval);
       
       
       /** Get integer configuration property. 
        * @param pn property name.
        * @param dval default value. 
        */
       public int getIntProperty (String pn, int dval);
       
       
       /** Generic dictionary of objects. 
        * Plugins may use this to exchange references to interfaces to their services. 
        */
       public Map<String, Object> getObjectMap();
       
       
       /** Get software version. */
       public String getVersion();
       
       
       /** Get default to-address for APRS packet to be sent. */
       public String getToAddr();
       
       
       /** Get SAR URL handler. */
       public SarUrl getSarUrl();
       
       
       /** Get SAR mode info. */
       public SarMode getSar();
    
    
       /** Set SAR mode. 
        * @param src Callsign of the user requesting SAR mode. 
        * @param filt Regular expression for filtering source callsign of pos reports.
        * @param desc The reason why SAR mode is requested. 
        */
       public void setSar(String src, String filt, String descr);
    
    
       /**
        * Clear SAR mode. 
        */
       public void clearSar();
   }
   