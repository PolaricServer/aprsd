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
import java.util.*;
import no.polaric.aprsd.*;




/**
 * Authorizations and service config for a given user session.
 * This can be sent to the client in JSON format. 
 */
public class AuthInfo {
    public String userid;
    public String servercall;
    public boolean admin = false, sar = false; 
    public String[] services = null;
    private static List<String> _services = new ArrayList<String>();
    
    
    public static void addService(String srv) {
       _services.add(srv);
    }
    
    
    public String toString() {
       return "AuthInfo [userid="+userid+", admin="+admin+", sar="+sar+"]";
    }
    
    
    public boolean login() 
        { return userid != null; }
       
       
    /**
     * Constructor. Gets userid from a user profile on request and sets authorisations. 
     */
    
    public AuthInfo(ServerAPI api, Request req, Response res) 
    {
       final SparkWebContext context = new SparkWebContext(req, res);
       final ProfileManager manager = new ProfileManager(context);
       final Optional<CommonProfile> profile = manager.get(true);
      
       var i = 0;
       services = new String[_services.size()];
       for (var x : _services)
	      services[i++] = x;
	      
       if (profile.isPresent()) {
          userid = profile.get().getId();
          User u = (User) profile.get().getAttribute("userInfo");
          admin = u.isAdmin();
          sar = u.isSar();          
          if (admin)
             sar=true;
       }
       
       servercall=api.getProperty("default.mycall", "NOCALL");
    }
}
