 /* 
 * Copyright (C) 2017-2023 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import org.pac4j.core.context.WebContext;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.jee.context.session.*;
import java.util.*;
import java.util.concurrent.*;
import no.polaric.aprsd.*;
import com.fasterxml.jackson.annotation.*;



/**
 * Authorizations and service config for a given user session.
 * This is instantiated on each request!!
 * This can be sent to the client in JSON format. 
 */
public class AuthInfo {

    /* Expire time in minutes */
    public static final int MAILBOX_EXPIRE = 60 * 24 * 7;
    
    
    /* Wrapper of Mailbox with counter */
    public static class SesMailBox extends MailBox.User {
        long expire = 0;
        int cnt = 0;
        public int increment() {return ++cnt;}
        public int decrement() {return --cnt;}
        
        public SesMailBox(ServerAPI api, String userid) {
            super(api, userid); 
        }
    }
    

    
    public String userid;
    public String groupid;
    public String callsign;
    public String servercall;
    public boolean admin = false, sar = false;
    public String tagsAuth;
    public String[] services = null;
    private ServerAPI _api; 
    
    @JsonIgnore public SesMailBox mailbox = null;
    @JsonIgnore public Group group;
    
    private static List<String> _services = new ArrayList<String>();    
    private static Queue<SesMailBox> gcbox = new LinkedList<SesMailBox>();
    private static Map<String, SesMailBox> mboxlist = new HashMap<String,SesMailBox>();
    private static ScheduledExecutorService gc = Executors.newScheduledThreadPool(5);
    private static Map<String, ScheduledFuture> closingSessions = new HashMap<String,ScheduledFuture>();
    
    public static void addService(String srv) {
       _services.add(srv);
    }
    
    
    /* Authorize to do changes on point (item) */
    public boolean itemSarAuth(PointObject x) {
        return x.hasTag(tagsAuth) || admin; 
    }
    
    
    public static void init(ServerAPI api) {

        WebServer ws = (WebServer) api.getWebserver(); 
        
        /* Called when websocket connection for map-updates is opened. 
         * Kind of represents a client session 
         */
        ws.onOpenSes( (c)-> {
                AuthInfo a = c.getAuthInfo();
                if (a==null) 
                    return;
                
                a.getMailbox();
                
                /* If user has recently closed session and is scheduled for removal, 
                 * cancel this removal. 
                 */
                var closing = closingSessions.get(a.userid); 
                if (closing != null) {
                    a.mailbox.increment();
                    closing.cancel(false); 
                    closingSessions.remove(a.userid);
                }
                
                else { 
                    /* 
                     * Inform other PS instances of user login. 
                     */
                    var rctl = api.getRemoteCtl(); 
                    if (rctl != null)
                        rctl.sendRequestAll("USER", a.userid+"@"+api.getRemoteCtl().getMycall(), null);
                    
                    /* As long as one or more sessions are open, we want 
                     * address mappings for messages. 
                     */
                    a.mailbox.increment();
                    a.mailbox.addAddress(a.userid);
                    if (!"".equals(a.callsign))
                        a.mailbox.addAddress(a.callsign+"@aprs");
                }
            });
            
        /* Called when websocket connection for map-updates is closed. 
         * Kind of represents a client session 
         */
        ws.onCloseSes( (c)-> {
                AuthInfo a = c.getAuthInfo();
                if (a==null)
                    return;
                    
                a.getMailbox();
                if (a.mailbox!=null) {
                    if (a.mailbox.decrement() == 0) {
                        /* 
                         * Schedule for removing the user and expiring the mailbox - in 30 seconds 
                         * This is cancelled if session is re-opened within 30 seconds
                         */
                        var closing = gc.schedule( () -> {
                            /* If last session is closed, remove address mappings for messages. */
                            a.mailbox.removeAddresses();
                            var rctl = api.getRemoteCtl(); 
                            rctl.sendRequestAll("RMUSER", a.userid+"@"+api.getRemoteCtl().getMycall(), null);
                        
                            /* Put mailbox on expire. Expire after 1 week */
                            a.mailbox.expire = (new Date()).getTime() + 1000 * 60 * MAILBOX_EXPIRE; 
                            gcbox.add(a.mailbox);
                            
                            /* Remove the future */
                            closingSessions.remove(a.userid); 
                            
                        }, 30, TimeUnit.SECONDS);
                        closingSessions.put(a.userid, closing); 
                    }
                }
            }); 
            

        /* Start a periodic task that expires mailboxes. */
        gc.scheduleAtFixedRate( ()-> {
            while (true) 
                if (!gcbox.isEmpty() && gcbox.peek().expire < (new Date()).getTime()) {
                    SesMailBox mb = gcbox.remove();
                    if (mb!=null && mb.cnt == 0 && mboxlist.get(mb.getUid()) != null)
                        api.log().info("AuthInfo", "MailBox expired. userid="+mb.getUid());
                    mboxlist.remove(mb.getUid());   
                }
                else
                    break;
        }, 60, 60, TimeUnit.SECONDS);
        
        
        /* At shutdown. Send a message to other nodes */
        api.addShutdownHandler( ()-> {
            if (api.getRemoteCtl() != null)
                api.getRemoteCtl().sendRequestAll("RMNODE", api.getRemoteCtl().getMycall(), null); 
        });

    }
    
    
    
    public String toString() {
       return "AuthInfo [userid="+userid+", admin="+admin+", sar="+sar+"]";
    }
    
    
    public boolean login() 
        { return userid != null; }
       
    
        
    public boolean isTrackerAllowed(String tr, String chan) {
        return sar || admin; 
    }

    
    public static Optional<CommonProfile> getSessionProfile(Request req, Response res) {
        return getSessionProfile(new SparkWebContext(req, res)); 
    }
    
    
    public static Optional<CommonProfile> getSessionProfile(WebContext context) {
        final ProfileManager manager = new ProfileManager(context, JEESessionStore.INSTANCE); 
        final Optional<CommonProfile> profile = manager.getProfile(CommonProfile.class);
        return profile;
    }
    
    
    /*
     * Authorizations. We use a kind a role-based authorization here. 
     * where some authorizations depends on role/group membership. 
     */
    public void authorize(User u, Group grp) {
        userid = u.getIdent();
        callsign = u.getCallsign();
        if (grp == null) 
            grp = u.getGroup();
        groupid = grp.getIdent();
        tagsAuth = grp.getTags();
            
        admin = u.isAdmin();
        sar = grp.isSar(); 
    }
    
    
    public SesMailBox getMailbox() {
        if (mailbox != null)
            return mailbox; 
        MailBox mb = MailBox.get(userid); 
        if (mb==null)
            mb = mboxlist.get(userid);
            
        if (mb != null && mb instanceof SesMailBox) 
            mailbox = (SesMailBox) mb;
        else {
            mailbox = new SesMailBox(_api, userid); 
            mboxlist.put(userid, mailbox);
        }    
        return mailbox;
    }
    
    
       
    /**
     * Constructor. Gets userid from a user profile on request and sets authorisations. 
     * called from AuthService for each request.
     */
     
    public AuthInfo(ServerAPI api, User u, Group g) {
        _api = api;
        authorize(u, g);
        var i = 0;
        services = new String[_services.size()];
        for (var x : _services)
            services[i++] = x;
    }
    
    
    
    /**
     * Constructor. Gets info from web context.
     */
     
    public AuthInfo(ServerAPI api, Request req, Response res) {
        this(api, new SparkWebContext(req, res));
    }
    
    
    public AuthInfo(ServerAPI api, WebContext context) 
    {
        Optional<CommonProfile> profile = getSessionProfile(context);
        _api = api;
        var i = 0;
        services = new String[_services.size()];
        for (var x : _services)
            services[i++] = x;
        
        /* 
         * Copy user-information from the user-profile?
         * The user profile is created by the authenticator.
         */
        if (profile.isPresent()) {
            userid = profile.get().getId();
            User u = (User) profile.get().getAttribute("userInfo");
            Group grp = (Group) profile.get().getAttribute("role");
            authorize(u, grp);
        }
        else
            api.log().debug("AuthInfo", "User profile not present");
       
        servercall=api.getProperty("default.mycall", "NOCALL");
    }
}
