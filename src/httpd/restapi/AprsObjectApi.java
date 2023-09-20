/* 
 * Copyright (C) 2019-2023 by Øyvind Hanssen (ohanssen@acm.org)
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
import static spark.Spark.post;
import static spark.Spark.*;
import java.util.*; 
import no.polaric.aprsd.*;
import uk.me.jstott.jcoord.*; 




/**
 * Implement REST API for APRS objects. 
 */
 
public class AprsObjectApi extends ServerBase {

    private OwnObjects _ownObj; 

    /* 
     * Object info as it is sent from clients. 
     */
    public static class ObjInfo {
        public String ident; 
        public double[] pos;
        public char sym, symtab; 
        public String comment;
        public boolean perm;
    }
    
    
    public static class ObjUpdate {
        public double[] pos;
        public char sym, symtab; 
        public String comment;
    }
    
    
    public AprsObjectApi(ServerAPI api) {
        super(api);
        _ownObj = api.getOwnObjects();
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
         * Get a list of objects. 
         ******************************************/
        get("/aprs/objects", "application/json", (req, resp) -> {
            var ol = new ArrayList<String>();
            try {
                var it = _ownObj.getItems();
                it.forEach( x-> {ol.add(x);} );
            } catch (Exception e) {
                e.printStackTrace(System.out);
            }
            return ol;
        }, ServerBase::toJson );
        
        
        
        /******************************************
         * Post an object
         ******************************************/
        post("/aprs/objects", (req, resp) -> {
            ObjInfo obj = (ObjInfo) 
                ServerBase.fromJson(req.body(), ObjInfo.class);
                
            if (_ownObj.get(obj.ident) != null)
                return ERROR(resp, 400, "Object already exists");
                
            if ( !obj.ident.matches("[a-zA-Z0-9\\.\\/\\-\\_\\#]{1,9}") || 
                     (obj.pos[1]==0 && obj.pos[0]==0))
                return ERROR(resp, 400, "Invalid object. Couldn't post");
                
            var pos = new LatLng( obj.pos[1], obj.pos[0]);
            var pd  = new ReportHandler.PosData (pos, obj.sym, obj.symtab);
            
            if (_ownObj.add(obj.ident, pd, obj.comment, obj.perm)) {
                _api.log().info("RestApi", "POST OBJECT: '"+obj.ident+"' by user '"+getAuthInfo(req).userid+"'");
                systemNotification("ADMIN", "Object '"+obj.ident+"' posted by user '"+getAuthInfo(req).userid+"'", 120);
                return "OK";
            }
            else
                return ERROR(resp, 500, "Couldn't post object: "+obj.ident);
        } );
        

        /******************************************
         * Update an object
         ******************************************/
        put("/aprs/objects/*",  (req, resp) -> {
            String ident = req.splat()[0];
            ObjInfo oi = (ObjInfo) 
                ServerBase.fromJson(req.body(), ObjUpdate.class);
            AprsObject obj = _ownObj.get(ident);
            if (obj==null)
                return ERROR(resp, 404, "Object not found");
                
            var pos = new LatLng( oi.pos[1], oi.pos[0]);
            var pd  = new ReportHandler.PosData (pos, oi.sym, oi.symtab);
            obj.update(new Date(), pd, oi.comment, "(own)");
            return "OK";
        } );
        

        /*****************************************
         * Delete an object 
         *****************************************/
        delete("/aprs/objects/*", (req, resp) -> {
            String ident = req.splat()[0];
            if (_ownObj.delete(ident)) {
                _api.log().info("RestApi", "DELETE OBJECT: '"+ident+"' by user '"+getAuthInfo(req).userid+"'");
                systemNotification("ADMIN", "Object '"+ident+"' deleted by user '"+getAuthInfo(req).userid+"'", 120);
                return "OK";
            }
            else
                return ERROR(resp, 500, "Couldn't delete object: "+ident);
        });
        
        
    
    }


}



