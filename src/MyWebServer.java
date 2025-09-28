/* 
 * Copyright (C) 2025 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import no.arctic.core.*;
import no.arctic.core.util.*;
import no.arctic.core.auth.*;
import no.arctic.core.httpd.*;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.pac4j.core.config.Config;
import org.pac4j.javalin.*;
import java.util.*;


public class MyWebServer extends WebServer {
    
    private JsonMapUpdater _jmapupd;
    private ZeroConf _zconf = new ZeroConf();
    private RemoteCtl _rctl; 
    
    
    /*
     * Attributes to user-session can be put here .
     */
    public static class UserSessionInfo extends WebServer.UserSessionInfo {
        public MailBox.User mailbox; 
        public UserSessionInfo(String uid, MailBox.User mbox) 
            {super(uid);mailbox = mbox;}
    }
     
    
    public MyWebServer(AprsServerConfig conf, int port, String filepath, String filedir) {
        super(conf, port, "notify", filepath, filedir );
        _rctl = conf.getRemoteCtl(); 
    }
    
    public JsonMapUpdater getJsonMapUpdater() {
        return _jmapupd;
    }
    
    
    public long nMapUpdates() {
        return (_jmapupd==null ? 0 : _jmapupd.nUpdates());
    }
    
    
    public void start() {
        super.start(); 
             
        /* Called when connected to RemoteCtl parent or child */
        if (_rctl != null)
            _rctl.onConnect( node-> {
                for (String u : loginUsers())
                    _rctl.sendRequest(node, "USER", u+"@"+_rctl.getMycall());
            });
        
        
        /* Called at login when user-session is created */
        onLogin( u-> {
            _conf.log().debug("MyWebServer", "Login: "+u);
            if (_rctl != null)
                _rctl.sendRequestAll("USER", u+"@"+_rctl.getMycall(), null);
        });
        
        
        /* Called when logout and no other active user-sessions */
        onLogout( u-> {
            _conf.log().debug("MyWebServer", "Logout: "+u);
            if (_rctl != null)
                _rctl.sendRequestAll("RMUSER", u+"@"+_rctl.getMycall(), null);
        });
        
        
        /* Called to create and close a session-object for user-login. Close is called a week after session-close */
        createUserSes( u-> {
            MailBox.User mb = (MailBox.User) MailBox.get(u);
            if (mb == null) {
                mb = new MailBox.User(_conf, u);
                mb.addAddress(u);
            }
            return new UserSessionInfo(u, mb);
        });
        
        closeUserSes( ses-> {
            MailBox mb = ((MyWebServer.UserSessionInfo) ses).mailbox;
            mb.removeAddresses(); 
        });
        
        
        MailBox.init((AprsServerConfig) _conf);
        
        _jmapupd = new JsonMapUpdater((AprsServerConfig) _conf);
        _jmapupd.start("jmapdata");
        
        /* Start REST APIs */
        UserApi ua = new UserApi((AprsServerConfig) _conf, userDb(), groupDb());
        ua.start();
        ItemApi ia = new ItemApi((AprsServerConfig) _conf);
        ia.start();
        AprsObjectApi aoa = new AprsObjectApi((AprsServerConfig) _conf);
        aoa.start();
        SystemApi sa = new SystemApi((AprsServerConfig) _conf);
        sa.start();
        SysAdminApi saa = new SysAdminApi((AprsServerConfig) _conf);
        saa.start();
        BullBoardApi bba = new BullBoardApi((AprsServerConfig) _conf);
        bba.start();
        MailBoxApi mba = new MailBoxApi((AprsServerConfig) _conf);
        mba.start();
        SarApi sar = new SarApi((AprsServerConfig) _conf);
        sar.start();
        ShellScriptApi ssa = new ShellScriptApi((AprsServerConfig) _conf);
        ssa.start();
        
         var mycall = _conf.getProperty("default.mycall", "NOCALL").toUpperCase();
         var secure = _conf.getBoolProperty("httpserver.secure", false);
         var proxy  = _conf.getBoolProperty("httpserver.proxy", true);
         
        _zconf.registerMdns("_polaric_aprsd._tcp.local.", mycall, _port,
             "secure="+(secure?"yes":"no")+", "+"proxy="+(proxy?"yes":"no"));
            
            
        /* At shutdown. Send a message to other nodes */
        _conf.addShutdownHandler( ()-> {
            System.out.println("**** SHUTDOWN ****");
            ((AprsServerConfig)_conf).saveConfig();
            
            var u = authService().userDb(); 
            if (u instanceof LocalUsers) 
                ((LocalUsers) u).save();
            
            _conf.log().info("WebServer", "Unregistering on mDNS...");
            _zconf.unregisterMdns();
            authService().hmacAuth().saveLogins();
        });
    }
    
}


