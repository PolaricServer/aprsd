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
import org.pac4j.sparkjava.SecurityFilter;
import org.pac4j.http.client.indirect.*;
import no.polaric.aprsd.*;


 
/**
 * Pac4j configuration. 
 */
public class AuthConfig {
     private ServerAPI _api;
     private String _host, _allowOrigin, _passwdFile, _userFile, _groupFile; 
     private Config _config;
     private final PasswordFileAuthenticator _passwds;
     private UserDb _users; 
     private GroupDb _groups;
     private boolean _udbChanged = false; 
        
     
     public AuthConfig(ServerAPI api) {
        _api = api;
        
         /* FIXME: How can we simplify config of this? */
         _host        = "";
         _allowOrigin = api.getProperty("httpserver.alloworigin", ".*");
         _passwdFile  = api.getProperty("httpserver.passwdfile",  "/etc/polaric-aprsd/passwd");
         _userFile    = api.getProperty("httpserver.userfile",    "/var/lib/polaric/users.dat");
         _groupFile   = api.getProperty("httpserver.groupfile",   "/etc/polaric-aprsd/groups");
              
         /* Default user and groups database is local file */
         _groups = new LocalGroups(api, _groupFile);
         _users = new LocalUsers(api, _userFile, _groups);
         _passwds =  new PasswordFileAuthenticator(api, _passwdFile, _users);
     
         /* Indirect basic auth client */     
         final IndirectBasicAuthClient basicClient = new IndirectBasicAuthClient(_passwds);

         /* Indirect Form client */
         final FormClient formClient = new FormClient(_host+"/loginForm", _passwds);

         /* Config */      
         _config = new Config (new Clients(_host+"/callback", basicClient, formClient));
         _config.addAuthorizer("isauth", new IsFullyAuthenticatedAuthorizer());
         _config.addAuthorizer("sar", new UserAuthorizer(false));
         _config.addAuthorizer("admin", new UserAuthorizer(true));
     }
 
     public void setUserDb(UserDb udb) { 
        if (_udbChanged) {
            _api.log().error("AuthConfig", "User DB cannot be changed more than once");
            return;
        }
        _udbChanged = true; 
        _users = udb; 
     }
 
     public GroupDb getGroupDb()
        { return _groups; }
        
     public UserDb getUserDb() 
        { return _users; }
 
     public void reloadPasswds() {
        _passwds.load();
     }
 
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
 
