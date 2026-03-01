/* 
 * Copyright (C) 2019-2026 by Øyvind Hanssen (ohanssen@acm.org)
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
 
package no.polaric.aprsd.api;
import no.polaric.aprsd.*;
import no.polaric.aprsd.point.*;
import no.polaric.core.*;
import no.polaric.core.httpd.*;
import no.polaric.core.auth.*;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.*; 
import no.polaric.aprsd.aprs.*;




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
    
    
    public AprsObjectApi(AprsServerConfig conf) {
        super(conf);
        _ownObj = conf.getOwnObjects();
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
       
       protect("/aprs/objects", "operator");
       
        /******************************************
         * Get a list of objects. 
         ******************************************/
        a.get("/aprs/objects", (ctx) -> {
            var ol = new ArrayList<String>();
            try {
                var it = _ownObj.getItems();
                it.forEach( x-> {ol.add(x);} );
            } catch (Exception e) {
                e.printStackTrace(System.out);
            }
            ctx.json(ol);
        });
        
        
        
        /******************************************
         * Post an object
         ******************************************/
        a.post("/aprs/objects", (ctx) -> {
            ObjInfo obj = (ObjInfo) 
                ServerBase.fromJson(ctx.body(), ObjInfo.class);
                
            if (_ownObj.get(obj.ident) != null) {
                ERROR(ctx, 400, "Object already exists");
                return;
            }
            if ( !obj.ident.matches("[a-zA-Z0-9\\.\\/\\-\\_\\#]{1,9}") || 
                     (obj.pos[1]==0 && obj.pos[0]==0)) {
                ERROR(ctx, 400, "Invalid object. Couldn't post");
                return;
            }
            var pos = new LatLng( obj.pos[1], obj.pos[0]);
            var pd  = new ReportHandler.PosData (pos, obj.sym, obj.symtab);
            
            if (_ownObj.add(obj.ident, pd, obj.comment, obj.perm)) {
                _conf.log().info("RestApi", "POST OBJECT: '"+obj.ident+"' by user '"+getAuthInfo(ctx).userid+"'");
                systemNotification("ADMIN", "Object '"+obj.ident+"' posted by user '"+getAuthInfo(ctx).userid+"'", 120);
                ctx.result("OK");
            }
            else
                ERROR(ctx, 500, "Couldn't post object: "+obj.ident);
        } );
        

        /******************************************
         * Update an object
         ******************************************/
        a.put("/aprs/objects/{id}",  (ctx) -> {
            String ident = ctx.pathParam("id");
            ObjInfo oi = (ObjInfo) 
                ServerBase.fromJson(ctx.body(), ObjUpdate.class);
            AprsObject obj = _ownObj.get(ident);
            if (obj==null)
                ERROR(ctx, 404, "Object not found");
            else {     
                var pos = new LatLng( oi.pos[1], oi.pos[0]);
                var pd  = new ReportHandler.PosData (pos, oi.sym, oi.symtab);
                obj.update(new Date(), pd, oi.comment, "(own)");
                ctx.result("OK");
            }
        } );
        

        /*****************************************
         * Delete an object 
         *****************************************/
        a.delete("/aprs/objects/{id}", (ctx) -> {
            String ident = ctx.pathParam("id");
            if (_ownObj.delete(ident)) {
                _conf.log().info("RestApi", "DELETE OBJECT: '"+ident+"' by user '"+getAuthInfo(ctx).userid+"'");
                systemNotification("ADMIN", "Object '"+ident+"' deleted by user '"+getAuthInfo(ctx).userid+"'", 120);
                ctx.result("OK");
            }
            else
                ERROR(ctx, 500, "Couldn't delete object: "+ident);
        });
        
        
    
    }


}



