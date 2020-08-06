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
import spark.route.Routes;
import spark.staticfiles.StaticFilesConfiguration;
import spark.http.matching.MatcherFilter;
import spark.embeddedserver.EmbeddedServers;
import spark.embeddedserver.jetty.*;
import spark.ExceptionMapper; 
import static spark.Spark.get;
import static spark.Spark.*;
import org.pac4j.core.config.Config;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.sparkjava.*; 
import org.eclipse.jetty.server.Server;
// // import org.eclipse.jetty.server.SessionManager;
import org.eclipse.jetty.server.session.SessionHandler;
import javax.servlet.SessionCookieConfig;
import java.util.*;
import java.lang.reflect.*;
import no.polaric.aprsd.*;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jetty.util.thread.QueuedThreadPool;


/**
 * HTTP server. Web services are configured here. Se also AuthService class for login services. 
 * FIXME: How can we make this more extensible by plugins? 
 */
public class WebServer implements ServerAPI.Web 
{

    class JettyServer implements JettyServerFactory {

        public Server create(int maxThreads, int minThreads, int threadTimeoutMillis) {
            Server server;

            if (maxThreads > 0) {
                int max = maxThreads;
                int min = (minThreads > 0) ? minThreads : 8;
                int idleTimeout = (threadTimeoutMillis > 0) ? threadTimeoutMillis : 60000;
                server = new Server(new QueuedThreadPool(max, min, idleTimeout));
            } else {
                server = new Server();
            }
            return server;
        }

        @Override
        public Server create(ThreadPool threadPool) {
            return threadPool != null ? new Server(threadPool) : new Server();
        }
    }



    private long _nRequests = 0; 
    private final PubSub _pubsub;
    private final MapUpdater _jmapupdate;
    private ServerAPI _api; 
    private AuthService _auth;
 
      
    public WebServer(ServerAPI api, int port) {
       if (port > 0)
          port (port);
       _api = api;
       
      _pubsub     = new PubSub(_api, true); 
      _jmapupdate = new JsonMapUpdater(_api, true);
      _auth = new AuthService(api); 
    }
 
 
   
    /**
     * setup handler in the same manner spark does in {@code EmbeddedJettyFactory.create()}.
     */
    private static JettyHandler setupHandler(Routes routeMatcher, StaticFilesConfiguration staticFilesConfiguration, boolean hasMultipleHandler) {
        var ex = ExceptionMapper.getInstance(); 
        var matcherFilter = new MatcherFilter(routeMatcher, staticFilesConfiguration, ex, false, hasMultipleHandler);
        matcherFilter.init(null);
        return new JettyHandler(matcherFilter);
    }
    
    
    
    
    /** 
     * Session settings. 
     */
    protected void configSession(SessionHandler mgr) {
        mgr.setMaxInactiveInterval(14400);
        SessionCookieConfig sc = mgr.getSessionCookieConfig(); 
        sc.setHttpOnly(true);
        boolean secureses = _api.getBoolProperty("httpserver.securesession", false);
        sc.setSecure(secureses);
    }
    
    
    
    /** 
     * Start the web server and setup services. 
     */
     
    public void start() throws Exception {
        System.out.println("*** WebServer: Starting...");
      

        /*  https://blog.codecentric.de/en/2017/07/fine-tuning-embedded-jetty-inside-spark-framework/  */
        EmbeddedServers.add(EmbeddedServers.Identifiers.JETTY, 
         (Routes routeMatcher, StaticFilesConfiguration staticFilesConfiguration, ExceptionMapper ex, boolean hasMultipleHandler) -> 
         {
             JettyHandler handler = setupHandler(routeMatcher, staticFilesConfiguration, hasMultipleHandler);
             configSession(handler);
             
             return new EmbeddedJettyServer(
               new JettyServer(),           
               handler);
         });
      
      
        /* Serving static files */
        staticFiles.externalLocation(
            _api.getProperty("httpserver.filedir", "/usr/share/polaric") );  
       
        /* 
         * websocket services. 
         * Note that these are trusted in the sense that we assume that authorizations and
         * userid will only be available if properly authenticated. THIS SHOULD BE TESTED. 
         */
        webSocket("/notify", _pubsub);
        webSocket("/jmapdata", _jmapupdate);
                 
        _auth.start();
                 
        /* 
         * OPTIONS requests (CORS preflight) are not sent with cookies and should not go 
         * through the auth check. 
         * Maybe we do this only for REST APIs and return info more exactly what options
         * are available? Move it inside the corsEnable method? 
         */
        before("*", (req, res) -> {
            if (req.requestMethod() == "OPTIONS") {
                _auth.corsHeaders(req, res); 
                halt(200, "");
            }
        });
         
         
        before("*", (req, res) -> {res.status(200);});
        before("/config_menu", _auth.conf().filter("FormClient", "isauth, admin")); 

        
        /* 
         * Protect other webservices. 
         */ 
        before("/addobject",       _auth.conf().filter(null, "isauth, sar"));
        before("/deleteobject",    _auth.conf().filter(null, "isauth, sar"));
        before("/resetinfo",       _auth.conf().filter(null, "isauth, sar"));
        before("/sarmode",         _auth.conf().filter(null, "isauth, sar"));
        before("/sarurl",          _auth.conf().filter(null, "isauth"));
        before("/users",           _auth.conf().filter(null, "isauth, admin"));
        before("/users/*",         _auth.conf().filter(null, "isauth, admin"));
        before("/users/*/*",       _auth.conf().filter(null, "isauth"));
        before("/item/*/alias",    _auth.conf().filter(null, "isauth, sar"));
        before("/item/*/reset",    _auth.conf().filter(null, "isauth, sar"));
        before("/item/*/tags",     _auth.conf().filter(null, "isauth, sar"));
        before("/aprs/*",          _auth.conf().filter(null, "isauth, sar"));
        before("/system/sarmode",  _auth.conf().filter(null, "isauth, sar"));
        before("/sar/ipp/*",       _auth.conf().filter(null, "isauth"));
        before("/mailbox",         _auth.conf().filter(null, "isauth"));
        
        
        afterAfter((request, response) -> {
            _nRequests++;
        });
      

        
        /* Start REST API: System */
        SystemApi ss = new SystemApi(_api);
        ss.start(); 
        corsEnable("/system/*");
        corsEnable("/item/*"); 
        corsEnable("/items/*");     
        corsEnable("/items");
        
        AprsObjectApi oi = new AprsObjectApi(_api);
        oi.start();
        corsEnable("/aprs/*");
        
        /* Start REST API: Users */
        UserApi uu = new UserApi(_api, _auth.conf().getLocalUsers());
        uu.start();
        corsEnable("/users");
        corsEnable("/users/*"); 

        /* Start REST API: SAR */
        SarApi sar = new SarApi(_api); 
        sar.start();
        corsEnable("/sar/*");
        
        /* Start REST API: Bulletin board */
        BullBoardApi bb = new BullBoardApi(_api); 
        bb.start(); 
        corsEnable("/bullboard/*");
        
        MailBoxApi mb = new MailBoxApi(_api); 
        mb.start(); 
        corsEnable("/mailbox/*");
        
        /* Rooms for SYSTEM and ADMIN notifications */
        _pubsub.createRoom("notify:SYSTEM", false, false, false, false, ServerAPI.Notification.class);
        _pubsub.createRoom("notify:ADMIN", false, false, false, false, ServerAPI.Notification.class);
        
        /* Room for pushing updates to bulletin board */
        _pubsub.createRoom("bullboard", (Class) null); 
        
        AuthInfo.init(_api);
        init();
    }
    
    
    
    
    private Set<String> corsEnabled = new HashSet<String>();
    
    public void corsEnable(String uri) {
        if (!corsEnabled.contains(uri)) {
            after(uri, (req,resp) -> { _auth.corsHeaders(req, resp); } );
            corsEnabled.add(uri);
        }
    }
    

    
    
    public void stop() throws Exception {
       _api.log().info("WebServer", "Stopping...");
       _jmapupdate.postText("RESTART!", c->true);
       _api.saveConfig();
       _auth.conf().getLocalUsers().save();
    }
         
    
     
    /* Statistics */
    public long nVisits() 
        { return _jmapupdate.nVisits(); }
     
    public int  nClients() 
        { return _jmapupdate.nClients(); }
     
    public int  nLoggedin()
        { return _jmapupdate.nLoggedIn(); }
        
    public long nHttpReq() 
        { return _nRequests; }
     
    public long nMapUpdates() 
        { return _jmapupdate.nUpdates(); }
        
    public ServerAPI.PubSub getPubSub() 
        { return _pubsub; }
     
    public Notifier getNotifier() 
        { return _jmapupdate; } 
        
    public WsNotifier getJsonMapUpdater()
        { return _jmapupdate; }
    
    public void protectUrl(String prefix) {
        before(prefix, _auth.conf().filter(null, "isauth")); 
    }
    
    /* FIXME: What methods should be part of ServerAPI.Web ? */
    public AuthConfig getAuthConfig()
        { return _auth.conf(); }
        
    
        
    /**
     * Send notification to a room 
     */    
    public void notifyUser(String user, ServerAPI.Notification not) {
        _pubsub.put("notify:"+user, not);
    }

    
    /**
     * Get info about logged-in user and authorizations. 
     * This is an attribute on the request object.
     */
    public final static AuthInfo getAuthInfo(Request req) {
        return (AuthInfo) req.raw().getAttribute("authinfo");
    }
    
    
    /**
     * Register a handler for user open/close session (logout).
     * Suitable for using a lambda function.
     */
    public void onOpenSes(WsNotifier.CHandler h) {
        _jmapupdate.onOpenSes(h); 
    }
    public void onCloseSes(WsNotifier.CHandler h) {
        _jmapupdate.onCloseSes(h); 
    }
     
    
    
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
            
                /* FIXME: Configure allowed origin(s) */
                after (key, (req,resp) -> { 
                    _auth.corsHeaders(req, resp);
                });
            
                get(key,  (req, resp) -> {return invokeMethod(o, m, req, resp);} );
                post(key, (req, resp) -> {return invokeMethod(o, m, req, resp);} );
            }
    }
   
   
   
    public static String invokeMethod(Object o, Method m, Request req, Response resp) {
        try {
            return (String) m.invoke(o, req, resp);
        }
        catch (Exception e) {
            System.out.println("*** WebServer: Couldn't invoke method: "+e);
            e.printStackTrace(System.out);
            return "<H2>Internal error: Couldn't invoke method</H2>"+e;
        }
    }

}
