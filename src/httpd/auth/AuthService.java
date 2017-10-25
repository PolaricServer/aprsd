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
import com.owlike.genson.*;
import spark.Request;
import spark.Response;
import static spark.Spark.get;
import static spark.Spark.*;
import org.pac4j.sparkjava.CallbackRoute;
import org.pac4j.sparkjava.LogoutRoute;
import org.pac4j.sparkjava.SparkWebContext; 
import org.pac4j.sparkjava.SecurityFilter;
import org.pac4j.core.config.Config;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.profile.CommonProfile;
import java.util.Optional;




/**
 * Web services for login, autentication and authorization. 
 * FIXME: How can we make this more extensible by plugins? 
 */
 
public class AuthService {
    
    private AuthConfig _authConf; 
    static ServerAPI _api; // FIXME: This is static. 
   
   
    public AuthService(ServerAPI api) {
       _authConf = new AuthConfig(api);
       _api = api; 
    }
   
    /** Return the configuration */
    public AuthConfig conf() 
       { return _authConf; }
       
       
    /** Set up the services. */
    public void start() {
      Config conf = _authConf.get();
      before("/formLogin",  new SecurityFilter(conf, "FormClient", "csrfToken, isauth")); 
     
      
      /* Authorization status. */
      before ("/authStatus", (req,resp) -> { 
               resp.header("Access-Control-Allow-Credentials", "true"); 
               resp.header("Access-Control-Allow-Origin", _authConf.getAllowOrigin()); 
              } 
            );
            
      before("/authStatus", new SecurityFilter(conf, null, "csrfToken, isauth"));      

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
      
    }
    
        
    
     /** Genson object mapper. */
    protected final static Genson mapper = new Genson();

    
    
    /**
     * Return authorization status 
     */
    public static String authStatus(Request req, Response res) {
        AuthInfo auth = new AuthInfo(_api, req, res);
        return mapper.serialize(auth);
    }
    
    
    
    /**
     * Generate a login form in HTML. 
     */
    public static String loginForm(Request req, Response res) {
       String err = "";
       if (req.queryParams("error") != null)
          err = "<span class=\"error\">Sorry. Unknown user and/or password!</span>";
    
       return    
         "<html><head><link rel=\"stylesheet\" href=\"style.css\"></head><body id=\"login\">" + 
         "<h2>Login</h2>" +
         err + 
         "<form method=\"post\" action=\"callback?client_name=FormClient\">" +
            "<fieldset>"+
              "<label class=\"leftlab sleftlab\"><b>Username</b></label>" +
              "<input type=\"text\" placeholder=\"Enter Username\" name=\"username\" required><br>" +
              "<label class=\"leftlab sleftlab\"><b>Password</b></label>" +
              "<input type=\"password\" placeholder=\"Enter Password\" name=\"password\" required><br>" +
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
       
       returnToOrigin(req, res, "origin");
       return 
         "<html><head><link rel=\"stylesheet\" href=\"style.css\"></head><body>" + 
         "<h2>You are now logged in</h2>" +
         "userid='"+profile.get().getId() +"'" +
         "</body></html>";
    }
    
    
    /**
     * Indicate the result of a successful logout and return to origin URL.
     */
    public static String logout(Request req, Response res) {
       returnToOrigin(req, res, "url"); 
       return 
         "<html><head><link rel=\"stylesheet\" href=\"style.css\"></head><body>" + 
         "<html><body>" + 
         "<h1>You are now logged out</h1>" +
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
