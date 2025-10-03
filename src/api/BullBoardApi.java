/* 
 * Copyright (C) 2017-2025 by Øyvind Hanssen (ohanssen@acm.org)
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
 
package no.polaric.aprsd.api;
import no.polaric.aprsd.point.*;
import no.polaric.core.*;
import no.polaric.core.httpd.*;
import no.polaric.core.auth.*;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.*; 
import no.polaric.aprsd.*;
import no.polaric.aprsd.aprs.*;

/**
 * Implement REST API for user-related info. Users, areas. 
 */
public class BullBoardApi extends ServerBase {

    private AprsServerConfig _api; 
    private BullBoard _board;
    
    
    public static class BullSubmit {
        public String bullid;
        public String groupid;
        public String text; 
    }
    
    public BullBoardApi(AprsServerConfig api) {
        super(api);
        _api = api;
        _board = _api.getBullBoard();
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
       
       protect("/bullboard");
       
        /* Get names of groups */
        a.get("/bullboard/groups", (ctx) -> {
           Set<String> groups = _board.getGroupNames();
           ctx.json(groups);
        });

        
        /* 
         * GET /bullboard/<group>/senders 
         * Get callsigns of senders to a group 
         */
        a.get("/bullboard/{sbid}/senders", (ctx) -> {
            String sbid = ctx.pathParam("sbid");
            BullBoard.SubBoard sb = _board.getBulletinGroup(sbid); 
            if (sb==null)
                ERROR(ctx, 404, "Group '"+sbid+"' not found");
            else 
                ctx.json(sb.getSenders());
        });
        
                
        /* 
         * GET /bullboard/<group>/messages
         * Get all messages in a group. Note that this returns a list of lists.  
         */
        a.get("/bullboard/{sbid}/messages", (ctx) -> {
            String sbid = ctx.pathParam("sbid");
            BullBoard.SubBoard sb = _board.getBulletinGroup(sbid); 
            if (sb==null)
                ERROR(ctx, 404, "Group '"+sbid+"' not found");
            else
                ctx.json(sb.getAll());
        });
        
        
        /*
         * Submit bulletin.
         */
        a.post("/bullboard/{sbid}/messages", (ctx) -> {
            AuthInfo user = getAuthInfo(ctx); 
            String sbid = ctx.pathParam("sbid");
            if (user.callsign == null) {
                ERROR(ctx,  403, "No callsign registered for user");
                return;
            }
            var b = (BullSubmit) 
                ServerBase.fromJson(ctx.body(), BullSubmit.class);
            if (b==null) {
                ERROR(ctx, 400, "Cannot parse input");
                return; 
            }
            var grp = _board.getBulletinGroup(b.groupid);
            if (grp == null)
                grp = _board.createBulletinGroup(b.groupid);
                
            grp.post(user.callsign, b.bullid.charAt(0), b.text);
            ctx.result("Ok"); 
        });
        

        /* 
         * GET /bullboard/<group>/messages/<sender>
         * Get messages in a group from a given sender 
         */
        a.get("/bullboard/{sbid}/messages/{sender}", (ctx) -> {
            String sbid = ctx.pathParam("sbid");
            String sender = ctx.pathParam("sender");
            BullBoard.SubBoard sb = _board.getBulletinGroup(sbid); 
            if (sb==null)
                ERROR(ctx, 404, "Group '"+sbid+"' not found");
            else
                ctx.json(sb.get(sender));
        });
    }


}



