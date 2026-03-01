/* 
 * Copyright (C) 2017-2026 by Øyvind Hanssen (ohanssen@acm.org)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 */


package no.polaric.aprsd;
import no.polaric.aprsd.aprs.*;
import no.polaric.aprsd.point.*;
import no.polaric.aprsd.filter.*;
import no.polaric.core.*;
import no.polaric.core.httpd.*;
import no.polaric.core.auth.*;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.*; 
import java.io.*;
import com.fasterxml.jackson.annotation.*;


/**
 * Implement REST API for user-related info. 
 */
public class UserApi extends ServerBase {

    private AprsServerConfig _conf; 
    
    
    public static class GroupInfo {
        public String ident; 
        public String name; 
        public boolean avail;
        public GroupInfo() {}
        public GroupInfo(String id, String n, boolean av) 
            { ident=id; name=n; avail=av;}
    }
    
    
    /* 
     * User info as it is sent to clients. 
     */
    public static class UserInfo {
        public String ident; 
        public Date lastused; 
        public String name, callsign;
        public String group="DEFAULT";
        public String altgroup="DEFAULT";
        
        @JsonAlias({"sar"})
        public boolean operator; 
        
        public boolean admin, suspend; 
        
        public String passwd;
        
        public UserInfo() {}
        
        public UserInfo(String id, Date lu, String n, String c, boolean s, boolean a, boolean u)
           { ident = id; lastused = lu; name=n; callsign=c; operator=s; admin=a; suspend=u; }
        public UserInfo(String id, Date lu, String n, String c, boolean s, boolean a, boolean u, String tr)
           { ident = id; lastused = lu; name=n; callsign=c; operator=s; admin=a; suspend=u; group=tr;}   
        public UserInfo(String id, Date lu, String n, String c, boolean s, boolean a, boolean u, String tr, String tr2)
           { ident = id; lastused = lu; name=n; callsign=c; operator=s; admin=a; suspend=u; group=tr; altgroup=tr2;}   
    }

    
    public static class UserUpdate {
        public String name, callsign;
        public String group;
        public String altgroup;
        public String passwd;
        
        @JsonAlias({"sar"})
        public boolean operator; 
        public boolean admin, suspend;
        public UserUpdate() {}
        public UserUpdate(String n, String c, String g, String p, boolean s, boolean a, boolean u) 
            { name=n; callsign=c; group=g; passwd=p; operator=s; admin=a; suspend=u; }
    }
    
    
    public static class PasswdUpdate {
        public String passwd; 
    }
    public static class GroupUpdate {
        public String group; 
    }
    

    
    private UserDb _users; 
    private GroupDb _groups; 
    private SortedSet<String> _remoteUsers = new TreeSet<String>();
    private PubSub _psub;
    
    
    
    public UserApi(AprsServerConfig c,  UserDb u, GroupDb g) {
        super(c);
        _conf = c;
        _users = u;
        _groups = g; 
        _psub = (PubSub) _conf.getWebserver().pubSub();
    }
    
    
    
    /** 
     * Return an error status message to client. 
     * FIXME: Move to superclass. 
     */
    public void ERROR(Context ctx, int status, String msg)
      { ctx.status(status); ctx.result(msg); }
      
      
      
    /** 
     * Get user info for serialising as JSON 
     */
    protected UserInfo getUser(User u) {
        if (u==null)
            return null;
        String name = "";           
        return new UserInfo(u.getIdent(), u.getLastUsed(), u.getName(), 
            u.getCallsign(), u.isOperator(), u.isAdmin(), u.isSuspended(), u.getGroup().getIdent(), u.getAltGroup().getIdent() );    
    }
    
    
    
    
    
    /** 
     * Set up the webservices. 
     */
    public void start() {     
       
        protect("/myfilters");
        protect("/mypasswd");
        protect("/loginusers");
        protect("/usernames");
        protect("/users",  "admin");
        protect("/users/*","admin");
        
        /************************************************
         * Get list of filter profiles.
         * /myfilters requires login
         ************************************************/
         
        a.get ("/filters", (ctx) -> {
            ctx.json(ViewFilter.getFilterList(false, null));
        });

        
        a.get ("/myfilters", (ctx) -> {
            AuthInfo auth = getAuthInfo(ctx);
            ctx.json(ViewFilter.getFilterList(auth.userid !=null, auth.groupid));
        });
       
       
       
        /************************************************
         * Update user's password.
         ************************************************/
        a.put("/mypasswd", (ctx) -> {
            var uid = getAuthInfo(ctx).userid; 
            User u = _users.get(uid);
            if (u==null) {
                ERROR(ctx, 404, "Unknown user: "+uid);
                return;
            }
            var pwd = (PasswdUpdate) 
                ServerBase.fromJson(ctx.body(), PasswdUpdate.class);
                
            if (pwd.passwd != null) 
                    u.setPasswd(pwd.passwd);
            _users.getSyncer().update(uid, new UserUpdate
                (null, null, null, pwd.passwd, u.isAdmin(), u.isOperator(), u.isSuspended())); 
            ctx.result("Ok");
        });
        
        
        
        /************************************************
         * Get logged in users (list of usernames).
         ************************************************/
        a.get("/loginusers", (ctx) -> {
            List<String> us = new ArrayList<String>(); 
            for (String name : ((WebServer)_conf.getWebserver()).loginUsers())
                us.add(name);
            if (((AprsServerConfig)_conf).getRemoteCtl() != null) 
                for (String name: ((AprsServerConfig)_conf).getRemoteCtl().getUsers())
                    us.add(name);
            ctx.json(us);
        });
        
        
        
        /******************************************
         * Get a list of users. 
         ******************************************/
        a.get("/usernames", (ctx) -> {
            List<String> ul = new ArrayList<String>();
            for (User u: _users.getAll())  
               ul.add(u.getIdent());
            ctx.json(ul);
        });
        
        
        
        /******************************************
         * Get a list of users. 
         ******************************************/
        a.get("/users", (ctx) -> {
            List<UserInfo> ul = new ArrayList<UserInfo>();
            for (User u: _users.getAll())  
               ul.add(getUser(u));
            ctx.json(ul);
        });
        
        
        
        /*******************************************
         * Add user
         *******************************************/
        a.post("/users", (ctx) -> {
            var u = (UserInfo) 
                ServerBase.fromJson(ctx.body(), UserInfo.class);

            if (_users.add(u.ident, u.name, u.admin, u.suspend, u.passwd, u.group, u.altgroup)==null) {
                ERROR(ctx, 400, "Probable cause: User exists");
                return;
            }
            _users.getSyncer().add(u.ident, u);
            ctx.result("Ok");
        });
        
                
        

        /*******************************************
         * Get info about a given user.
         *******************************************/
        a.get("/users/{id}", (ctx) -> {
            var ident = ctx.pathParam("id");
            User u = _users.get(ident);
            if (u==null) 
                ERROR(ctx, 404, "Unknown user: "+ident);
            else
                ctx.json(getUser(u));
        });
        
        
    
        /*******************************************
         * Update user
         *******************************************/
        a.put("/users/{id}", (ctx) -> {
            var ident = ctx.pathParam("id");  
            User u = _users.get(ident);
            
            if (u==null) {
                ERROR(ctx, 404, "Unknown user: "+ident);
                return;
            }
            var uu = (UserUpdate) 
                ServerBase.fromJson(ctx.body(), UserUpdate.class);
            if (uu==null) {
                ERROR(ctx, 400, "Cannot parse input");
                return;
            }    
            if (uu.group != null) {
                Group g = _groups.get(uu.group);
                if (g==null) {
                    ERROR(ctx, 404, "Unknown group: "+uu.group);
                    return;
                }
            }    
            if (uu.altgroup != null) {
                Group g = _groups.get(uu.altgroup);
                if (g==null) {
                    ERROR(ctx, 404, "Unknown alt group: "+uu.altgroup);
                    return;
                }
            } 
            if (uu.callsign != null && !uu.callsign.equals("") && ((AprsServerConfig)_conf).getMsgProcessor().getMycall().equals(uu.callsign)) {
                ERROR(ctx, 400, "Cannot use the same callsign as this server: "+ uu.callsign);
                return;
            }
            if (uu.group != null)
                u.setGroup(_groups.get(uu.group)); 
            if (uu.altgroup != null)
                u.setAltGroup(_groups.get(uu.altgroup));   
            if (uu.name != null)
                u.setName(uu.name);           
            if (uu.callsign != null)
                u.setCallsign(uu.callsign);
            if (uu.passwd != null) 
                u.setPasswd(uu.passwd);    
 //           u.setOperator(uu.operator);
            u.setAdmin(uu.admin);
            u.setSuspended(uu.suspend);
            _users.getSyncer().update(ident, uu);
            ctx.result("Ok");
        });
        

        
        /*******************************************
         * Delete user
         *******************************************/
        a.delete("/users/{id}", (ctx) -> {
            var ident = ctx.pathParam("id"); 
            _users.remove(ident);
            _users.getSyncer().remove(ident);
            ctx.result("Ok");
        });
        
        
    }


}



