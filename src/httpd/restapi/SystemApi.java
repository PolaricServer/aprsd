/* 
 * Copyright (C) 2018-2019 by Øyvind Hanssen (ohanssen@acm.org)
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
import no.polaric.aprsd.*;

/**
 * Implement REST API for system-related info.  
 */
public class SystemApi extends ServerBase {

    private ServerAPI _api; 
    
    public SystemApi(ServerAPI api) {
        super(api);
        _api = api;
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
       
        /******************************************
         * Get a list of icons (file paths). 
         ******************************************/
         get("/system/icons/*", "application/json", (req, resp) -> {
            var subdir = req.splat()[0];
            if (subdir.equals("default"))
               subdir = "";
            var webdir = System.getProperties().getProperty("webdir", ".");
            FilenameFilter flt = (dir, f) -> 
                  { return f.matches(".*\\.(png|gif|jpg)"); } ;
            Comparator<File> cmp = (f1, f2) -> 
                  f1.getName().compareTo(f2.getName());       
            
            var icondir = new File(webdir+"/icons/"+subdir);
            var files = icondir.listFiles(flt);
            Arrays.sort(files, cmp);
            if (!subdir.equals("")) subdir += "/";
            
            List<String> fl = new ArrayList<String>();
            for (File x: files)
               fl.add("/icons/"+subdir+x.getName());
            
            return fl;
        }, ServerBase::toJson );
        
        
        /*******************************************
         * Get alias/icon for a given item
         *******************************************/
        get("/tracker/alias/*", "application/json", (req, resp) -> {
            var ident = req.splat()[0];
            var st = _api.getDB().getItem(ident, null);
            if (st==null)
                return ERROR(resp, 404, "Unknown tracker item: "+ident); 
            return new ItemInfo.Alias(st.getAlias(), (st.iconIsNull() ? null : st.getIcon())); 
        }, ServerBase::toJson );
        
        
        /*******************************************
         * Update alias/icon for a given item
         *******************************************/
        put("/tracker/alias/*", (req, resp) -> {
            var ident = req.splat()[0];        
            var st = _api.getDB().getItem(ident, null);
            if (st==null)
                return ERROR(resp, 404, "Unknown tracker item: "+ident); 
            ItemInfo.Alias a = (ItemInfo.Alias) 
                ServerBase.fromJson(req.body(), ItemInfo.Alias.class);    
            if (a==null)
                return ERROR(resp, 400, "Invalid input format");
            if ( st.setAlias(a.alias) ) 
                notifyAlias(ident, a.alias, req); 
            if ( st.setIcon(a.icon) )
                notifyIcon(ident, a.icon, req);
            return "Ok";
        });

    }
    
    
    private void notifyAlias(String ident, String alias, Request req) {
        var uid = getAuthInfo(req).userid; 
        if (_api.getRemoteCtl() != null)
            _api.getRemoteCtl().sendRequestAll("ALIAS "+ident+" "+alias, null);
        _api.log().info("SystemApi", 
            "ALIAS: '"+alias+"' for '"+ident+"' by user '"+uid+"'");    
        _api.getWebserver().notifyUser(uid, new ServerAPI.Notification
            ("system", "system", "Alias: '"+alias+ "' for '"+ident+"' set by user '"+uid+"'", new Date(), 10) );     
    }
    
    
    private void notifyIcon(String ident, String icon, Request req) {
        var uid = getAuthInfo(req).userid; 
        if (_api.getRemoteCtl() != null)
            _api.getRemoteCtl().sendRequestAll("ICON "+ident+" "+icon, null);
        _api.log().info("SystemApi", 
            "ICON: '"+icon+"' for '"+ident+"' by user '"+uid+"'");    
        _api.getWebserver().notifyUser(uid, new ServerAPI.Notification
            ("system", "system", "Icon: '"+icon+ "' for '"+ident+"' set by user '"+uid+"'", new Date(), 10) );     
    }
}



