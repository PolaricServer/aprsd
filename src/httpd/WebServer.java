/* 
 * Copyright (C) 2017 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package no.polaric.aprsd.http;

import spark.Request;
import spark.Response;
import static spark.Spark.get;
import static spark.Spark.*;
import org.pac4j.core.config.Config;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.sparkjava.CallbackRoute;
import org.pac4j.sparkjava.LogoutRoute;
import org.pac4j.sparkjava.SecurityFilter;
import org.pac4j.sparkjava.SparkWebContext; 
import java.util.Optional;
import java.lang.reflect.*;
import no.polaric.aprsd.*;



/**
 * HTTP server. Web services are configured here. Se also AuthService class for login services. 
 * FIXME: How can we make this more extensible by plugins? 
 */
public class WebServer implements ServerAPI.Web 
{
    private long _nRequests = 0; 
    private final GeoMessages _messages;
    private final MapUpdater  _mapupdate, _jmapupdate;
    private ServerAPI _api; 
    private AuthService _auth;
 
      
    public WebServer(ServerAPI api, int port) {
       if (port > 0)
          port (port);
       _api = api;
             
      _messages   = new GeoMessages(_api, true);
      _mapupdate  = new MapUpdater(_api, true);
      _jmapupdate = new JsonMapUpdater(_api, true);
      _mapupdate.link(_jmapupdate);
      _auth = new AuthService(api); 
    }
 
 
 
    /** 
     * Start the web server and setup routes to services. 
     */
     
    public void start() throws Exception {
       System.out.println("WebServer: Starting...");
      
       /* Serving static files */
       staticFiles.externalLocation(
          _api.getProperty("webserver.filedir", "/usr/share/polaric") );  
       
       /* 
        * websocket services. 
        * Note that these are trusted in the sense that we assume that authorizations and
        * userid will only be available if properly authenticated. THIS SHOULD BE TESTED. 
        */
       webSocket("/messages", _messages);
       webSocket("/mapdata", _mapupdate);
       webSocket("/jmapdata", _jmapupdate);
         
       /* 
        * Protect other webservices. We should eventually prefix these and 
        * just one filter should be sufficient 
        */
       before("/station_sec", _auth.conf().filter(null, "csrfToken, isauth"));   
       before("/addobject", _auth.conf().filter(null, "csrfToken, isauth"));
       before("/deleteobject", _auth.conf().filter(null, "csrfToken, isauth"));
       before("/resetinfo", _auth.conf().filter(null, "csrfToken, isauth"));
       before("/sarmode", _auth.conf().filter(null, "csrfToken, isauth"));
       before("/sarurl", _auth.conf().filter(null, "csrfToken, isauth"));
       before("/search_sec", _auth.conf().filter(null, "csrfToken, isauth"));
         
         
       afterAfter((request, response) -> {
          _nRequests++;
       });
      
       _auth.start();
    
       init();
    }
    
    

    
    
    public void stop() throws Exception {
       _api.log().info("WebServer", "Stopping...");
       _mapupdate.postText("RESTART!", c->true);
    }
         
    
     
     /* Statistics */
     public long nVisits() 
        { return _mapupdate.nVisits() + _jmapupdate.nVisits(); }
     
     public int  nClients() 
        { return _mapupdate.nClients() + _jmapupdate.nClients(); }
     
     public int  nLoggedin()
        { return _mapupdate.nLoggedIn() + _jmapupdate.nLoggedIn(); }
        
     public long nHttpReq() 
        { return _nRequests; } 
     
     public long nMapUpdates() 
        { return _mapupdate.nUpdates() + _jmapupdate.nUpdates(); }
        
     public ServerAPI.Mbox getMbox() 
        { return _messages; }
     
     public Notifier getNotifier() 
        { return _mapupdate; } 
   
     public WsNotifier getMapUpdater()
        { return _mapupdate; }
        
     public WsNotifier getJsonMapUpdater()
        { return _jmapupdate; }
        
     public WsNotifier getMessages()
        { return _messages; }
        
    
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
            
            /* FIXME: Configure allowed origin(s) */
            after (key, (req,resp) -> { 
               resp.header("Access-Control-Allow-Credentials", "true"); 
               resp.header("Access-Control-Allow-Origin", _auth.getAllowOrigin(req)); 
              } 
            );
            
            get(key,  (req, resp) -> {return m.invoke(o, req, resp);} );
            post(key, (req, resp) -> {return m.invoke(o, req, resp);} );
         }     
   }

}
