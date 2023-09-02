    /* 
 * Copyright (C) 2017-2023 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import no.polaric.aprsd.*;
import spark.Request;
import spark.Response;
import static spark.Spark.get;
import static spark.Spark.*;
import org.pac4j.sparkjava.*;
import org.pac4j.core.config.Config;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.jee.context.session.*;
import org.pac4j.sparkjava.SparkWebContext; 
import org.pac4j.core.context.WebContext;
import java.util.Optional;
import javax.servlet.*;




/**
 * Web services for login, autentication and authorization. 
 * FIXME: How can we make this more extensible by plugins? 
 */
 
public class AuthService {
    
    private static AuthConfig _authConf; 
    static ServerAPI _api; // FIXME: This is static. 
    private static Logfile  _log;
   
   
    public AuthService(ServerAPI api) {
       if (_authConf == null)
         _authConf = new AuthConfig(api);
       _api = api; 
       _log = new Logfile(api, "auth", "auth.log");
    }
   
   
   
    /** Return the configuration */
    public static AuthConfig conf() 
       { return _authConf; }
       
       
       
    /** Set up the services. */
    public void start() {
      Config conf = _authConf.get();
      
      /* 
       * OPTIONS requests (CORS preflight) are not sent with cookies and should not go 
       * through the auth check. 
       * Maybe we do this only for REST APIs and return info more exactly what options
       * are available? Move it inside the corsEnable method? 
       */
      before("*", (req, res) -> {
            if (req.requestMethod() == "OPTIONS") {
                corsHeaders(req, res); 
                halt(200, "");
            }
        });
        
      /* Set CORS headers. */
      before ("/authStatus", (req,resp) -> { corsHeaders(req, resp); } );
      before ("/directLogin", (req,resp) -> { corsHeaders(req, resp); } );
      before ("/hmacTest", (req,resp) -> { corsHeaders(req, resp); } );     
      before ("/postTest", (req,resp) -> { corsHeaders(req, resp); } );   
              
      /* Login with username and password */
      before("/directLogin", new SecurityFilter(conf, "DirectFormClient")); 
              
      /* MD5 Hash of body */
      before("*", AuthService::genBodyDigest);
      
      before("/hmacTest",    new SecurityFilter(conf, "HeaderClient")); 
      before("/authStatus",  new SecurityFilter(conf, "HeaderClient")); 


       /* 
        * For all routes, put an AuthInfo object on the request. Here we rely on sessions to remember 
        * user-profiles between requests. IF we use direct clients (stateless server) 
        * this will not work for paths without authenticators!! 
        */
      before("/hmacTest",    AuthService::getAuthInfo);
      before("/authStatus",  AuthService::getAuthInfo);

      post("/directLogin", AuthService::directLogin);   // Indicate login success
      get("/hmacTest",     AuthService::directLogin2);  
      get("/authStatus",   AuthService::authStatus);    // Return authorisation status

    }
    

    /**
     * Allowed origin (for CORS). If Origin header from request matches allowed origins regex. 
     * return it. Otherwise, null.
     */
    public String getAllowOrigin(Request req) {
        
        String allow = _authConf.getAllowOrigin(); 
        String origin = req.headers("Origin");
        if (origin != null && origin.matches(allow))
           return origin;
        else
           return null;
    }
    
    
    
    /**
     * Produce CORS headers. If Origin header from request matches allowed origins regex. 
     * add it to the Allow-Origin response header. 
     */
    public void corsHeaders(Request req, Response resp) {
        resp.header("Access-Control-Allow-Headers", "Authorization"); 
        resp.header("Access-Control-Allow-Credentials", "true"); 
        resp.header("Access-Control-Allow-Origin", getAllowOrigin(req)); 
        resp.header("Access-Control-Allow-Methods", "GET, PUT, POST, DELETE, OPTIONS");
    }
    
    
    
    /**
     * Return authorization status (as JSON)
     */
    public static String authStatus(Request req, Response res) {
        AuthInfo auth = new AuthInfo(_api, req, res);
        return ServerBase.serializeJson(auth);
    }

    
    /* 
     * This returns a key, be sure that it is only sent on encrypted channels in production 
     * enviroments. 
     */
    public static String directLogin(Request req, Response res) {
      Optional<CommonProfile> profile = AuthInfo.getSessionProfile(req, res); 
         
      String userid = profile.get().getId();
      String key = SecUtils.b64encode(SecUtils.getRandom(48)); // Gives 64 bytes when encoded 
      _authConf.getHmacAuth().setUserKey(userid, key);
             
      _log.log("Successful DIRECT login from: "+req.ip()+", userid="+ userid);
      return key;
    }
    
    
    public static String directLogin2(Request req, Response res) {
      Optional<CommonProfile> profile = AuthInfo.getSessionProfile(req, res); 
      String userid = profile.get().getId();
      
      _log.log("Successful DIRECT login (using HMAC) from: "+req.ip()+", userid="+ userid);
      return "Ok";
    }

    
    
   public static void genBodyDigest(Request req, Response res) {
      String body = req.body();
      String digest = (body==null || body.length() == 0 ? "" : SecUtils.xDigestB64(body, 44));
      req.raw().setAttribute("bodyHash", digest);
   }
    
    
    
    /** 
     * Create an AuthInfo object from the user profile and add it as an 
     * attribute on the request. 
     */
   public static AuthInfo getAuthInfo(Request req, Response res) {
      return getAuthInfo(new SparkWebContext(req, res));
   }
    
   public static AuthInfo getAuthInfo(WebContext context)
   {
      Optional<AuthInfo> ainfo = context.getRequestAttribute("authinfo");
      if (ainfo.isPresent()) 
         return ainfo.get();
      
      AuthInfo auth = new AuthInfo(_api, context); 
      context.setRequestAttribute("authinfo", auth);
      return auth;
   }
    
}
