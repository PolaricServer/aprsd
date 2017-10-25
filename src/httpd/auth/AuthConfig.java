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

import org.pac4j.core.client.Client;
import org.pac4j.core.client.Clients;
import org.pac4j.core.config.Config;
import org.pac4j.core.credentials.authenticator.Authenticator;
import org.pac4j.core.authorization.authorizer.*;
import org.pac4j.sparkjava.DefaultHttpActionAdapter;
import org.pac4j.sparkjava.SecurityFilter;
import org.pac4j.http.client.indirect.*;
import no.polaric.aprsd.*;


 
/**
 * Pac4j configuration. 
 */
public class AuthConfig {
     private String _host, _allowOrigin, _passwdFile; 
     private Config _config;
     
     
     public AuthConfig(ServerAPI api) {
     
         /* FIXME: How can we simplify config of this? */
         _host        = api.getProperty("auth.host",        "http://localhost:8081");
         _allowOrigin = api.getProperty("auth.alloworigin", "http://localhost");
         _passwdFile  = api.getProperty("auth.passwdfile",  "/etc/polaric-aprsd/passwd");
         
              
         final Authenticator passwds =  new PasswordFileAuthenticator(api, _passwdFile);
     
         /* Indirect basic auth client */     
         final IndirectBasicAuthClient basicClient = new IndirectBasicAuthClient(passwds);

         /* Indirect Form client */
         final FormClient formClient = new FormClient(_host+"/loginForm", passwds);
      
         /* Cors */
         CorsAuthorizer cors = new CorsAuthorizer(); 
         cors.setAllowOrigin(_allowOrigin);  // FIXME Other checks than origin. Testing.. Allow more than one origin? Regex? 

         /* Config */      
         _config = new Config (new Clients(_host+"/callback", basicClient, formClient));
         _config.addAuthorizer("isauth", new IsAuthenticatedAuthorizer());
         _config.addAuthorizer("cors", cors);
         _config.setHttpActionAdapter(new DefaultHttpActionAdapter());
     }
 
 
 
     // FIXME: Consider using origin request header and use a regex to check. 
     public String getAllowOrigin()
        { return _allowOrigin; }
        
 
     /** Return a Pac4j config object */
     public Config get() 
        { return _config; }
     
     
     /** Return a Pac4j security filter */
     public SecurityFilter filter(String clients, String authorizers) {
        return new SecurityFilter(get(), clients, authorizers); 
     }
}
 
