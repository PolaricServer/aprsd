/* 
 * Copyright (C) 2018-2021 by Øyvind Hanssen (ohanssen@acm.org)
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
import java.io.*;
import java.util.stream.*;
import uk.me.jstott.jcoord.*;
import no.polaric.aprsd.*;



/**
 * Implement REST API for system-admin.  
 */
public class SysAdminApi extends ServerBase {

    private ServerAPI _api; 
    
    public SysAdminApi(ServerAPI api) {
        super(api);
        _api = api;
    }
    
    public static class SysInfo {
        public Date runsince; 
        public String version;
        public int items;
        public int ownobj;
        public int clients, loggedin;
        public long usedmem;
        public String[] plugins;
        public String remotectl;
    }
    
    
    
    /** 
     * Return an error status message to client. 
     * FIXME: Move to superclass. 
     */
    public String ERROR(Response resp, int status, String msg)
      { resp.status(status); return msg; }
      
      
    private Date _time = new Date();
    
    
    /** 
     * Set up the webservices. 
     */
    public void start() {     
    
    
        /******************************************
         * Get system status info
         ******************************************/
        get("/system/adm/status", "application/json", (req, resp) -> {
            SysInfo res = new SysInfo();
            res.runsince = _time;
            res.version = _api.getVersion();
            res.items = _api.getDB().nItems();
            res.ownobj = _api.getDB().getOwnObjects().nItems();
            res.clients = _api.getWebserver().nClients();
            res.loggedin = _api.getWebserver().nLoggedin();
            res.usedmem = StationDBBase.usedMemory();
            
            /* Plugins */
            PluginManager.Plugin[] plugins = PluginManager.getPlugins();
            String[] plist = new String[plugins.length];
            int i=0;
            for (PluginManager.Plugin x: plugins)
                plist[i++] = x.getDescr();
            res.plugins = plist; 
            
            /* Connected servers */
            RemoteCtl rctl = _api.getRemoteCtl(); 
            res.remotectl = (rctl == null ? "" : rctl.toString());
            
            return res;
        }, ServerBase::toJson );
        
        
    }

}


