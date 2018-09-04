/* 
 * Copyright (C) 2017 by Øyvind Hanssen (ohanssen@acm.org)
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
import static spark.Spark.get;
import static spark.Spark.put;
import static spark.Spark.*;
import java.util.*; 
import no.polaric.aprsd.*;

/**
 * Implement REST API for user-related info. Users, areas. 
 */
public class UserApi extends ServerBase {

    private ServerAPI _api; 
    
    /* 
     * User info as it is sent to clients. 
     */
    public static class UserInfo {
        public String ident; 
        public Date lastused; 
        public UserInfo(String id, Date lu)
           { ident = id; lastused = lu; }
    }
    
    
    private LocalUsers _users; 
    
    
    public UserApi(ServerAPI api,  LocalUsers u) {
        super(api);
        _api = api;
        _users = u;
    }
    
    
    
    /** 
     * Return an error status message to client. 
     * FIXME: Move to superclass. 
     */
    public String ERROR(Response resp, int status, String msg)
      { resp.status(status); return msg; }
      
      
    
    /** 
     * Set up the webservices. 
     */
    public void start() {     
       
        /* Get a list of areas for a given user. */
        get("/users/*/areas", "application/json", (req, resp) -> {
            String uid = req.splat()[0];
            List<User.Area> ul = new ArrayList<User.Area>();
            _users.get(uid).getAreas().forEach( x-> {ul.add(x);} );
            return ul;
        }, ServerBase::toJson );
        
        
        
        /* Add an area to the list */
        post("/users/*/areas", (req, resp) -> {
            String uid = req.splat()[0];
            User.Area a = (User.Area) 
                ServerBase.fromJson(req.body(), User.Area.class);
                
            _api.getWebserver().notifyUser(uid, 
                new ServerAPI.Notification
                  ("system", "system", "Added area '"+a.name+ "' for user '"+uid+"'", new Date(), 10) );    
                
            if (a != null) 
                return ""+ _users.get(uid).addArea(a);
            else 
                return ERROR(resp, 400, "Invalid input format");
        });
        

        /* Delete an area from the list */
        delete("/users/*/areas/*", (req, resp) -> {
            String uid = req.splat()[0];
            String ix = req.splat()[1];
            try {
               int i = Integer.parseInt(ix);
               _users.get(uid).removeArea(i);
            }
            catch(Exception e) {
                return ERROR(resp, 400, ""+e); 
            }
            return "OK";
        });
        
        
        /* Get a list of users. */
        get("/users", "application/json", (req, resp) -> {
            List<UserInfo> ul = new ArrayList<UserInfo>();
            for (User u: _users.getAll())
               ul.add(new UserInfo(u.getIdent(), u.getLastUsed()));
            return ul;
        }, ServerBase::toJson );
    
    }


}



