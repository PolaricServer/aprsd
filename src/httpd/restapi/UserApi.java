/* 
 * Copyright (C) 2017-2021 by Øyvind Hanssen (ohanssen@acm.org)
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
import no.polaric.aprsd.filter.*;



/**
 * Implement REST API for user-related info. 
 */
public class UserApi extends ServerBase {

    private ServerAPI _api; 
    
    
    public static class GroupInfo {
        public String ident; 
        public String name; 
        public GroupInfo() {}
        public GroupInfo(String id, String n) 
            { ident=id; name=n; }
    }
    
    
    /* 
     * User info as it is sent to clients. 
     */
    public static class UserInfo {
        public String ident; 
        public Date lastused; 
        public String name, callsign;
        public String group="DEFAULT";
        public boolean sar, admin, suspend; 
        public String passwd;
        public UserInfo() {}
        public UserInfo(String id, Date lu, String n, String c, boolean s, boolean a, boolean u)
           { ident = id; lastused = lu; name=n; callsign=c; sar=s; admin=a; suspend=u; }
        public UserInfo(String id, Date lu, String n, String c, boolean s, boolean a, boolean u, String tr)
           { ident = id; lastused = lu; name=n; callsign=c; sar=s; admin=a; suspend=u; group=tr;}   
    }

    
    public static class UserUpdate {
        public String name, callsign;
        public String group;
        public String passwd;
        public boolean sar, admin, suspend;
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
    
    
    private UserDb _users; 
    private GroupDb _groups; 
    private SortedSet<String> _remoteUsers = new TreeSet<String>();
    
    
    public UserApi(ServerAPI api,  UserDb u, GroupDb g) {
        super(api);
        _api = api;
        _users = u;
        _groups = g; 
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
        return new UserInfo(u.getIdent(), u.getLastUsed(), u.getName(), 
            u.getCallsign(), u.isSar(), u.isAdmin(), u.isSuspended(), u.getGroup().getIdent() );    
    }
    

    
    /** 
     * Set up the webservices. 
     */
    public void start() {     
       
        /************************************************
         * Get list of filter profiles.
         ************************************************/
        get ("/filters", "application/json", (req, resp) -> {
            var uid = getAuthInfo(req).userid;
            String group = null;
            if (uid != null) {
                User u = _users.get(uid);
                if (u != null)
                    group = u.getGroup().getIdent(); 
            }   
            return ViewFilter.getFilterList(uid!=null, group);
        }, ServerBase::toJson);
       
       
       
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

            if (pwd.passwd != null) 
                    u.setPasswd(pwd.passwd);
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
        get("/groups", "application/json", (req, resp) -> {
            List<GroupInfo> gl = new ArrayList<GroupInfo>();
            for (Group g: _groups.getAll())  
                gl.add(new GroupInfo(g.getIdent(), g.getName()));
    
            return gl;
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
            
            if (uu.group != null) {
                Group g = _groups.get(uu.group);
                if (g==null)
                    return ERROR(resp, 404, "Unknown group: "+uu.group);
                u.setGroup(_groups.get(uu.group));
            }    
            if (uu.name != null)
                u.setName(uu.name);           
            if (uu.callsign != null)
                u.setCallsign(uu.callsign);
            if (uu.passwd != null) 
                u.setPasswd(uu.passwd);    
            u.setSar(uu.sar);
            u.setAdmin(uu.admin);
            u.setSuspended(uu.suspend);
            return "Ok";
        });
        
        
        
        /*******************************************
         * Add user
         *******************************************/
        post("/users", (req, resp) -> {
            var u = (UserInfo) 
                ServerBase.fromJson(req.body(), UserInfo.class);

            if (_users.add(u.ident, u.name, u.sar, u.admin, u.suspend, u.passwd, u.group)==null) 
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



