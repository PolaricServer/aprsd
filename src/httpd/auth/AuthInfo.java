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
import org.pac4j.sparkjava.SparkWebContext; 
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.profile.CommonProfile;
import java.util.Optional;
import no.polaric.aprsd.*;




/**
 * Authorizations for a given user.
 * This can be sent to the client in JSON format. 
 */
public class AuthInfo {
    public String userid;
    public boolean admin = false, sar = false; 
    

    
    public String toString() {
       return "AuthInfo [userid="+userid+", admin="+admin+", sar="+sar+"]";
    }
    
    
    
       
    /**
     * Constructor. Gets userid from a user profile on request and sets authorisations. 
     */
    
    public AuthInfo(ServerAPI api, Request req, Response res) 
    {
       final SparkWebContext context = new SparkWebContext(req, res);
       final ProfileManager manager = new ProfileManager(context);
       final Optional<CommonProfile> profile = manager.get(true);
        
       if (profile.isPresent()) {
          userid = profile.get().getId();
          String adminusers = api.getProperty("user.admin", "admin");
          String updateusers = api.getProperty("user.update", "");
          admin = (userid.matches(adminusers)); 
          sar = (userid.matches(updateusers));
          if (admin)
             sar=true;
       }
    }
}
