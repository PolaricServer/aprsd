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
import no.polaric.aprsd.*;
import spark.Request;
import spark.Response;
import static spark.Spark.get;
import static spark.Spark.*;
import org.pac4j.sparkjava.*;
import org.pac4j.core.config.Config;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.profile.CommonProfile;
import java.util.Optional;
import javax.servlet.*;
import org.xnap.commons.i18n.*;




/**
 * Web services for login, autentication and authorization. 
 * FIXME: How can we make this more extensible by plugins? 
 */
 
public class AuthService {
    
    private AuthConfig _authConf; 
    static ServerAPI _api; // FIXME: This is static. 
    private static Logfile  _log;
   
   
    public AuthService(ServerAPI api) {
       _authConf = new AuthConfig(api);
       _api = api; 
       _log = new Logfile(api, "auth", "auth.log");
    }
   
   
   
    /** Return the configuration */
    public AuthConfig conf() 
       { return _authConf; }
       
       
       
    /** Set up the services. */
    public void start() {
      Config conf = _authConf.get();
      
      before("/formLogin",  new SecurityFilter(conf, "FormClient", "isauth")); 
        
       /* 
        * Put an AuthInfo object on the request. Here we rely on sessions to remember 
        * user-profiles between requests. IF we use direct clients (stateless server) 
        * this will not work for paths without authenticators!! 
        */ 
      before("*", AuthService::setAuthInfo);
      
       /* Authorization status. */
      before ("/authStatus", (req,resp) -> { corsHeaders(req, resp); } );
                
      before("/logout", (req,resp) -> {
           _log.log("Logout by user: "+ ((AuthInfo) req.raw().getAttribute("authinfo")).userid);
        }); 
        
      /* Callback route */
      final CallbackRoute callback = new CallbackRoute(conf, null, true);   
      callback.setRenewSession(true);
      get("/callback", callback);
      post("/callback", callback);
             
      /* Logout route */
      final LogoutRoute localLogout = new LogoutRoute(conf, "/loggedout", ".*");   
      localLogout.setDestroySession(true);
      get("/logout", localLogout);
      get("/loggedout", AuthService::logout);
      
      /* Other routes */
      get("/basicLogin", AuthService::login);
      get("/formLogin",  AuthService::login);    
      get("/loginForm",  AuthService::loginForm);
      get("/authStatus", AuthService::authStatus);
      
              
        options("*", (req, resp) -> {
            System.out.println("**** OPTIONS ****");
            return null;
        });
      
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
        resp.header("Access-Control-Allow-Credentials", "true"); 
        resp.header("Access-Control-Allow-Origin", getAllowOrigin(req)); 
        resp.header("Access-Control-Allow-Methods", "GET, PUT, POST, DELETE, OPTIONS"); // FIXME
    }
    
    
    
    /**
     * Return authorization status (as JSON)
     */
    public static String authStatus(Request req, Response res) {
        AuthInfo auth = new AuthInfo(_api, req, res);
        return ServerBase.serializeJson(auth);
    }
    
    
    
    /**
     * Generate a login form in HTML. 
     */
    public static String loginForm(Request req, Response res) {
       I18n I = ServerBase._getI18n(req);
       String err = "";
       if (req.queryParams("error") != null) {
          err = "<span class=\"error\">" + I.tr("Sorry. Unknown user and/or password!") + "</span>";
          _log.log("Unsuccessful login attempt from. " +req.ip());
       }
       return    
         "<!doctype html>" +
         "<meta charset=\"UTF-8\">"+ 
         "<meta name=\"viewport\" content=\"user-scalable=no, width=device-width, height=device-height\">"+
       
         "<html><head><link rel=\"stylesheet\" href=\"style.css\"></head><body id=\"login\">" + 
         "<div><img src=\"images/PolaricServer.png\"/>" +
         "<div>|login|</div></div>" +
         err + 
         "<form method=\"post\" action=\"callback?client_name=FormClient\">" +
            "<fieldset>"+
              "<label class=\"leftlab sleftlab\"><b>" + I.tr("Username") + "</b></label>" +
              "<input type=\"text\" placeholder=\"" + I.tr("Enter Username") + "\" name=\"username\" required><br>" +
              "<label class=\"leftlab sleftlab\"><b>Password</b></label>" +
              "<input type=\"password\" placeholder=\""+ I.tr("Enter Password") + "\" name=\"password\" required><br>" +
            "</fieldset>" +     
            "<button type=\"submit\">Login</button>" +
         "</form></body></html>";
    }
    
    
    
    /**
     * Returns the client to the URL given as query param 'origin'. 
     */
    private static void returnToOrigin(Request req, Response res, String name) {
       String origin = req.queryParams(name);
       if (origin != null) 
          res.header("Refresh", "3;"+origin);
    }
    
    
    /**
     * Indicate the result of a successful login and return to origin URL.
     */
    public static String login(Request req, Response res) {
       final SparkWebContext context = new SparkWebContext(req, res);
       final ProfileManager manager = new ProfileManager(context);
       final Optional<CommonProfile> profile = manager.get(true);
       I18n I = ServerBase._getI18n(req);
       
       AuthInfo auth = (AuthInfo) req.raw().getAttribute("authinfo");
       _log.log("Successful login from: "+req.ip()+": userid="+ auth.userid);
       returnToOrigin(req, res, "origin");
       
       return 
         "<html><head><link rel=\"stylesheet\" href=\"style.css\"></head><body>" + 
         "<h2>" + I.tr("You are now logged in") + "</h2>" +
         "userid='"+profile.get().getId() +"'" +
         "</body></html>";
    }
    
    
    /**
     * Indicate the result of a successful logout and return to origin URL.
     */
    public static String logout(Request req, Response res) {
       I18n I = ServerBase._getI18n(req);
       returnToOrigin(req, res, "url"); 
       return 
         "<html><head><link rel=\"stylesheet\" href=\"style.css\"></head><body>" + 
         "<html><body>" + 
         "<h1>" + I.tr("You are now logged out") + "</h1>" +
         "</body></html>";
    }
    
    
    /** 
     * Create an AuthInfo object from the user profile and add it as an 
     * attribute on the request. 
     */    
    public static void setAuthInfo(Request req, Response res)
    {
        AuthInfo auth = new AuthInfo(_api, req, res); 
        req.raw().setAttribute("authinfo", auth);
    }
    
}
