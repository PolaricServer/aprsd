/* 
 * Copyright (C) 2020 by Øyvind Hanssen (ohanssen@acm.org)
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
public class SarApi extends ServerBase {

    private ServerAPI _api; 
    private Map<String, Map> _ippMap = new HashMap();
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
    
    
    
    public SarApi(ServerAPI api) {
        super(api);
        _api = api;
    }
    
    
    protected String addIpp(String user, IppInfo ii) {
        var umap = _ippMap.get(user); 
        if (umap==null) {
            umap = new HashMap();
            _ippMap.put(user, umap);
        }
        umap.put(ii.ident, ii);
        return ii.ident;
    }
    
    
    protected void updateIpp(String user, String ident, IppInfo ii) {
        var umap = _ippMap.get(user); 
        if (umap==null) 
            return;
        umap.replace(ident, ii);
    }
    
    
    protected Collection<IppInfo> getIppList(String user) {
        var umap = _ippMap.get(user);
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
    public String ERROR(Response resp, int status, String msg)
      { resp.status(status); return msg; }
      

    
    /** 
     * Set up the webservices. 
     */
    public void start() {     
        
        /******************************************
         * Get a list of IPPs for a user 
         ******************************************/
        get("/sar/ipp", "application/json", (req, resp) -> {
            var auth = getAuthInfo(req); 
            if (auth == null)
                return ERROR(resp, 500, "No authorization info found");
            var list = getIppList(auth.userid); 
            if (list==null)
                return ERROR(resp, 404, "Not found");
            return list;
        }, ServerBase::toJson );
        
                
        
        /*******************************************
         * Add a IPP 
         *******************************************/
        post("/sar/ipp", (req, resp) -> {
            var auth = getAuthInfo(req); 
            if (auth == null)
                return ERROR(resp, 500, "No authorization info found");
            var ipp = (IppInfo) 
                ServerBase.fromJson(req.body(), IppInfo.class);
            var ident = addIpp(auth.userid, ipp);
            return ident;
        });
        
        
        
        /*******************************************
         * Get a specific IPP
         *******************************************/
        get("/sar/ipp/*", "application/json", (req, resp) -> {
            var ident = req.splat()[0];
            var auth = getAuthInfo(req); 
            if (auth == null)
                return ERROR(resp, 500, "No authorization info found");
            var ipp = getIpp(auth.userid, ident);
            if (ipp==null)
                return ERROR(resp, 404, "Not found");
            return ipp;
        }, ServerBase::toJson );
        
        
        
        /*******************************************
         * Update a IPP 
         *******************************************/
        put("/sar/ipp/*", (req, resp) -> { 
            var ident = req.splat()[0];
            var auth = getAuthInfo(req); 
            if (auth == null)
                return ERROR(resp, 500, "No authorization info found");
            var ipp = (IppInfo) 
                ServerBase.fromJson(req.body(), IppInfo.class);
            updateIpp(auth.userid, ident, ipp);
            return "Ok";
        });
        
        
        
        /*******************************************
         * Delete a IPP
         *******************************************/
        delete("/sar/ipp/*", (req, resp) -> {
            var ident = req.splat()[0];
            var auth = getAuthInfo(req); 
            if (auth == null)
                return ERROR(resp, 500, "No authorization info found");
            deleteIpp(auth.userid, ident);
            return "Ok";
        });
                
    }

}



