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
import com.fasterxml.jackson.annotation.*;



/**
 * Authorizations and service config for a given user session.
 * This is instantiated on each request!!
 * This can be sent to the client in JSON format. 
 */
public class AuthInfo {
    
    /* Wrapper of counter value */
    protected static class Cnt {
        int cnt = 0;
        public int increment() {return ++cnt;}
        public int decrement() {return --cnt;}
    }
    
    
    public String userid;
    public String servercall;
    public boolean admin = false, sar = false; 
    public String[] services = null;
    
    @JsonIgnore public Cnt clients; 
    @JsonIgnore public MailBox.User mailbox = null;
    
    private static List<String> _services = new ArrayList<String>();
    
    
    public static void addService(String srv) {
       _services.add(srv);
    }
    
    
    public static void init(ServerAPI api) {
        WebServer ws = (WebServer) api.getWebserver(); 
        ws.onOpenSes( (c)-> {
                AuthInfo a = c.getAuthInfo();
                if (a.clients!=null) {
                    a.clients.increment();
                    
                    /* As long as one or more sessions are open, we want 
                     * address mappings for messages. 
                     */
                    a.mailbox.addAddress(a.userid);
                }
            }); 
            
        ws.onCloseSes( (c)-> {
                AuthInfo a = c.getAuthInfo();
                if (a.clients!=null) {
                    if (a.clients.decrement() == 0 && a.mailbox != null) {
                        /* If last session is closed, remove address mappings for messages. */
                        a.mailbox.removeAddresses();
                    }
                }
            }); 
    }
    
    
    
    public String toString() {
       return "AuthInfo [userid="+userid+", admin="+admin+", sar="+sar+"]";
    }
    
    
    public boolean login() 
        { return userid != null; }
       
       
    
       
       
    /**
     * Constructor. Gets userid from a user profile on request and sets authorisations. 
     * called from AuthService for each request.
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
        
        /* 
         * Copy user-information from the user-profile?
         * The user profile is created by the authenticator and kept as long as the session is active.
         */
        if (profile.isPresent()) {
            userid = profile.get().getId();
           
            /* check if there is a mailbox on the session. If not, create one. */
            mailbox = (MailBox.User) profile.get().getAttribute("mailbox");
            if (mailbox==null) {
                mailbox = new MailBox.User(api, userid); 
                profile.get().addAttribute("mailbox", mailbox); 
                profile.get().addAttribute("clients", new Cnt() );
            }

            User u = (User) profile.get().getAttribute("userInfo");
            admin = u.isAdmin();
            sar = u.isSar();          
            if (admin)
                sar=true;
            /* This is actually an object so updates are done on profile */
            clients = (Cnt) profile.get().getAttribute("clients"); 
        }
       
        servercall=api.getProperty("default.mycall", "NOCALL");
    }
}
