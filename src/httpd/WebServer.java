 
package no.polaric.aprsd.http;

import spark.Request;
import spark.Response;
import static spark.Spark.get;
import static spark.Spark.*;
import java.lang.reflect.*;
import no.polaric.aprsd.*;

public class WebServer implements ServerAPI.Web 
{
    private long _nRequests = 0; 
    private ServerAPI _api; 
    private final GeoMessages _messages;
    private final MapUpdater  _mapupdate, _jmapupdate, _smapupdate;
      
      
      
    public WebServer(ServerAPI api, int port) {
       if (port > 0)
          port (port);
       _api = api;
             
      _messages   = new GeoMessages(_api, true);
      _mapupdate  = new MapUpdater(_api, false);
      _smapupdate = new MapUpdater(_api, true);
      _jmapupdate = new JsonMapUpdater(_api, false); 
      _smapupdate.link(_mapupdate);
      _mapupdate.link(_jmapupdate);
    
    }
 
    
    public void start() throws Exception {
      System.out.println("WebServer: Starting...");

      /* FIXME: Add secure websocket services */
      webSocket("/messages_sec", _messages);
      webSocket("/mapdata", _mapupdate);
      webSocket("/mapdata_sec", _smapupdate);
      webSocket("/jmapdata", _jmapupdate);
      
      afterAfter((request, response) -> {
         _nRequests++;
      });
      
      get("/hello", WebServer::helloWorld);
      init();
    }
         
         
         
    public static String helloWorld(Request req, Response res) {
       return "Hello world!";
    }
    
    
    
    public void stop() throws Exception {
       System.out.println("WebServer: Stopping...");
    }
         
    
     
     /* Statistics */
     public long nVisits() 
        { return _mapupdate.nVisits() + _smapupdate.nVisits(); }
     
     public int  nClients() 
        { return _mapupdate.nClients() + _smapupdate.nClients(); }
     
     public int  nLoggedin()
        { return _smapupdate.nClients(); }
        
     public long nHttpReq() 
        { return _nRequests; } 
     
     public long nMapUpdates() 
        { return _mapupdate.nUpdates() + _smapupdate.nUpdates(); }
        
     public ServerAPI.Mbox getMbox() 
        { return _messages; }
     
     public Notifier getNotifier() 
        { return _smapupdate; } 
   
         
    
   /**
    * Adds a HTTP service handler. Go through methods. All public methods that starts 
    * with 'handle_' are considered handler-methods and are added to the handler-map. 
    * Key (URL target part) is derived from the method name after the 'handle_' prefix. 
    * Nothing else is assumed of the handler class.
    *
    * This is not REST. Register for GET and POST method. 
    * Future webservices should be RESTful.
    * 
    * @param o : Handler object
    * @param prefix : Prefix of the url target part. If null, no prefix will be assumed. 
    */
    
   public void addHandler(Object o, String prefix)
   { 
      for (Method m : o.getClass().getMethods())

         /* FIXME: Should consider using annotation to identify what methods are handlers. */
         if (m.getName().matches("handle_.+")) {
            /* FIXME: Should check if method is public, type of parameters and return value */
            String key = m.getName().replaceFirst("handle_", "");
            if (prefix != null && prefix.charAt(0) != '/')
                prefix = "/" + prefix;
            key = (prefix==null ? "" : prefix) + "/" + key;
            System.out.println("WebServer: Add HTTP handler method: "+key+" --> "+m.getName());
           
            get(key,  (req, resp) -> {return m.invoke(o, req, resp);} );
            post(key, (req, resp) -> {return m.invoke(o, req, resp);} );
         }
   }

}
