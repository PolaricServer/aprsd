/* 
 * Copyright (C) 2020-2026 by Øyvind Hanssen (ohanssen@acm.org)
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
import no.polaric.aprsd.point.*;
import no.polaric.aprsd.filter.*;
import no.polaric.core.*;
import no.polaric.core.httpd.*;
import no.polaric.core.auth.*;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.*; 
import java.io.*;



/**
 * Implement REST API for user-related info. 
 */
public class SarApi extends ServerBase {

    private ServerConfig _conf; 
    private Map<String, Map> _ippMap = new HashMap<String,Map>();
    private long nextId = 1; 
    
    /* 
     * User info as it is sent to clients. 
     */
    public static class IppInfo {
        public String ident; 
        public String descr;
        public double[] pos;
        public float p25, p50, p75, p95;
    }
    
    
    
    public SarApi(ServerConfig conf) {
        super(conf);
        _conf = conf;
    }
    
    
    @SuppressWarnings("unchecked")
    protected String addIpp(String user, IppInfo ii) {
        Map<String,IppInfo> umap = _ippMap.get(user); 
        if (umap==null) {
            umap = new HashMap<String,IppInfo>();
            _ippMap.put(user, umap);
        }
        umap.put(ii.ident, ii);
        return ii.ident;
    }
    
    
    @SuppressWarnings("unchecked")
    protected boolean updateIpp(String user, String ident, IppInfo ii) {
        Map<String,IppInfo>  umap = _ippMap.get(user); 
        if (umap==null) 
            return false;
        umap.replace(ident, ii);
        return true;
    }
    
    
    @SuppressWarnings("unchecked")
    protected Collection<IppInfo> getIppList(String user) {
        Map<String,IppInfo> umap = _ippMap.get(user);
        if (umap==null)
            return null;
        return umap.values();
    }
    
    
    protected IppInfo getIpp(String user, String ident) {
        var umap = _ippMap.get(user); 
        if (umap==null)
            return null;
        return (IppInfo) umap.get(ident);
    }
    
    
    protected void deleteIpp(String user, String ident) {
        var umap = _ippMap.get(user); 
        if (umap==null)
            return;
        umap.remove(ident);
    }
    
    
    /** 
     * Return an error status message to client. 
     * FIXME: Move to superclass. 
     */
    public void ERROR(Context ctx, int status, String msg)
      { ctx.status(status); ctx.result(msg); }
    
    
    
    
    /** 
     * Set up the webservices. 
     */
    public void start() {     
        
        protect("sar/ipp");
        
        /******************************************
         * Get a list of IPPs for a user 
         ******************************************/
        a.get("/sar/ipp", (ctx) -> {
            var auth = getAuthInfo(ctx); 
            if (auth == null) {
                ERROR(ctx, 500, "No authorization info found");
                return;
            }
            var list = getIppList(auth.userid); 
            if (list==null)
                ERROR(ctx, 404, "Not found");
            else
                ctx.json(list);
        });
        
                
        
        /*******************************************
         * Add a IPP 
         *******************************************/
        a.post("/sar/ipp", (ctx) -> {
            var auth = getAuthInfo(ctx); 
            if (auth == null) {
                ERROR(ctx, 500, "No authorization info found");
                return;
            }
            var ipp = (IppInfo) 
                ServerBase.fromJson(ctx.body(), IppInfo.class);       
            if (ipp==null) {
                ERROR(ctx, 400, "Cannot parse input");   
                return;
            }
            var ident = addIpp(auth.userid, ipp);
            ctx.result(ident);
        });
        
        
        
        /*******************************************
         * Get a specific IPP
         *******************************************/
        a.get("/sar/ipp/{id}", (ctx) -> {
            var ident = ctx.pathParam("id");
            var auth = getAuthInfo(ctx); 
            if (auth == null) {
                ERROR(ctx, 500, "No authorization info found");
                return;
            }
            var ipp = getIpp(auth.userid, ident);
            if (ipp==null)
                ERROR(ctx, 404, "Not found");
            else 
                ctx.json(ipp);
        });
        
        
        
        /*******************************************
         * Update a IPP 
         *******************************************/
        a.put("/sar/ipp/{id}", (ctx) -> { 
            var ident = ctx.pathParam("id");
            var auth = getAuthInfo(ctx); 
            if (auth == null) {
                ERROR(ctx, 500, "No authorization info found");
                return;
            }
            var ipp = (IppInfo) 
                ServerBase.fromJson(ctx.body(), IppInfo.class);
            if (ipp==null)
                ERROR(ctx, 400, "Cannot parse input");   
            else if (!updateIpp(auth.userid, ident, ipp))
                ERROR(ctx, 404, "Not found");
            else
                ctx.result("Ok");
        });
        
        
        
        /*******************************************
         * Delete a IPP
         *******************************************/
        a.delete("/sar/ipp/{id}", (ctx) -> {
            var ident = ctx.pathParam("id");
            var auth = getAuthInfo(ctx); 
            if (auth == null) {
                ERROR(ctx, 500, "No authorization info found");
                return;
            }
            deleteIpp(auth.userid, ident);
            ctx.result("Ok");
        });
                
    }

}



