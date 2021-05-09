/* 
 * Copyright (C) 2017-2020 by Øyvind Hanssen (ohanssen@acm.org)
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
 * Implement REST API for user-related info. 
 */
public class UserApi extends ServerBase {

    private ServerAPI _api; 
    
    
    /* 
     * User info as it is sent to clients. 
     */
    public static class UserInfo {
        public String ident; 
        public Date lastused; 
        public String name, callsign;
        public String allowTracker="";
        public boolean sar, admin; 
        public String passwd;
        public UserInfo() {}
        public UserInfo(String id, Date lu, String n, String c, boolean s, boolean a)
           { ident = id; lastused = lu; name=n; callsign=c; sar=s; admin=a; }
        public UserInfo(String id, Date lu, String n, String c, boolean s, boolean a, String tr)
           { ident = id; lastused = lu; name=n; callsign=c; sar=s; admin=a; allowTracker=tr;}   
    }

    
    public static class UserUpdate {
        public String name, callsign;
        public String allowTracker;
        public String passwd;
        public boolean sar, admin;
    }
    
    
    public static class PasswdUpdate {
        public String passwd; 
    }
    
    
    public static class Client {
        public String uid; 
        public String username;
        public Date created; 
        public Client(String id, String uname, Date cr) {
            uid=id; username=uname; created=cr;
        }
    }
    
    
    private LocalUsers _users; 
    private SortedSet<String> _remoteUsers = new TreeSet<String>();
    
    
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
     * Get user info for serialising as JSON 
     */
    protected UserInfo getUser(User u) {
        if (u==null)
            return null;
        String name = "";           
        if (u instanceof LocalUsers.User) {
            var lu = (LocalUsers.User) u; 
            return new UserInfo(u.getIdent(), u.getLastUsed(), lu.getName(), 
                lu.getCallsign(), lu.isSar(), lu.isAdmin(), lu.getAllowedTrackers() );    
        }
        else
            return null;
    }

    
    /** 
     * Set up the webservices. 
     */
    public void start() {     
        
        /************************************************
         * Update user's password.
         ************************************************/
        put("/mypasswd", (req, resp) -> {
            var uid = getAuthInfo(req).userid; 
            User u = _users.get(uid);
            if (u==null)
                return ERROR(resp, 404, "Unknown user: "+uid);
            var pwd = (PasswdUpdate) 
                ServerBase.fromJson(req.body(), PasswdUpdate.class);
            
            if (u instanceof LocalUsers.User) {
                if (pwd.passwd != null) 
                    ((LocalUsers.User) u).setPasswd(pwd.passwd);
            }
            return "Ok";
        });
        
        
        
        /************************************************
         * Get (web socket) clients.
         ************************************************/
        get("/wsclients", "application/json", (req, resp) -> {
            List<Client> cli = new ArrayList<Client>();
            for (WebClient x: ((WebServer)_api.getWebserver()).getClients())
                cli.add(new Client(x.getUid(), x.getUsername(), x.created()));
            return cli;
        }, ServerBase::toJson );
        
        
        
        /************************************************
         * Get logged in users (list of usernames).
         ************************************************/
        get("/loginusers", "application/json", (req, resp) -> {
            List<String> us = new ArrayList<String>(); 
            for (String name : ((WebServer)_api.getWebserver()).getLoginUsers())
                us.add(name);
            if (_api.getRemoteCtl() == null)
                return us; 
            for (String name: _api.getRemoteCtl().getUsers())
                us.add(name);
                
            return us;
        }, ServerBase::toJson );
        
        
        
        /******************************************
         * Get a list of users. 
         ******************************************/
        get("/users", "application/json", (req, resp) -> {
            List<UserInfo> ul = new ArrayList<UserInfo>();
            for (User u: _users.getAll())  
               ul.add(getUser(u));
    
            return ul;
        }, ServerBase::toJson );
        
        
        
        /******************************************
         * Get a list of users. 
         ******************************************/
        get("/usernames", "application/json", (req, resp) -> {
            List<String> ul = new ArrayList<String>();
            for (User u: _users.getAll())  
               ul.add(u.getIdent());
    
            return ul;
        }, ServerBase::toJson );
        
        
        /*******************************************
         * Get info about a given user.
         *******************************************/
        get("/users/*", "application/json", (req, resp) -> {
            var ident = req.splat()[0];
            User u = _users.get(ident);
            if (u==null)
                return ERROR(resp, 404, "Unknown user: "+ident);
            return getUser(u);
        }, ServerBase::toJson );
        
        
    
        /*******************************************
         * Update user
         *******************************************/
        put("/users/*", (req, resp) -> {
            var ident = req.splat()[0];        
            User u = _users.get(ident);
            if (u==null)
                return ERROR(resp, 404, "Unknown user: "+ident);
            var uu = (UserUpdate) 
                ServerBase.fromJson(req.body(), UserUpdate.class);
            
            if (u instanceof LocalUsers.User) {
                if (uu.name != null)
                    ((LocalUsers.User) u).setName(uu.name);           
                if (uu.callsign != null)
                    ((LocalUsers.User) u).setCallsign(uu.callsign);
                if (uu.passwd != null) 
                    ((LocalUsers.User) u).setPasswd(uu.passwd);
                if (uu.allowTracker != null)
                    ((LocalUsers.User) u).setTrackerAllowed(uu.allowTracker);
                ((LocalUsers.User) u).setSar(uu.sar);
                ((LocalUsers.User) u).setAdmin(uu.admin);
            }
            return "Ok";
        });
        
        
        
        /*******************************************
         * Add user
         *******************************************/
        post("/users", (req, resp) -> {
            var u = (UserInfo) 
                ServerBase.fromJson(req.body(), UserInfo.class);

            if (_users.add(u.ident, u.name, u.sar, u.admin, u.passwd, u.allowTracker)==null) 
                return ERROR(resp, 400, "Probable cause: User exists");
            return "Ok";
        });
        
        
        
        /*******************************************
         * Delete user
         *******************************************/
        delete("/users/*", (req, resp) -> {
            var ident = req.splat()[0];  
            _users.remove(ident);
            return "Ok";
        });
        
        
    }


}



