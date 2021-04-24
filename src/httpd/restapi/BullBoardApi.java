/* 
 * Copyright (C) 2017 by Øyvind Hanssen (ohanssen@acm.org)
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
 * Implement REST API for user-related info. Users, areas. 
 */
public class BullBoardApi extends ServerBase {

    private ServerAPI _api; 
    private BullBoard _board;
    
    
    public static class BullSubmit {
        public String bullid;
        public String groupid;
        public String text; 
    }
    
    public BullBoardApi(ServerAPI api) {
        super(api);
        _api = api;
        _board = _api.getBullBoard();
    }
    
    
    
    /** 
     * Return an error status message to client 
     */
    public String ERROR(Response resp, int status, String msg)
      { resp.status(status); return msg; }
      
      
    
    /** 
     * Set up the webservices. 
     */
    public void start() {     
       
        /* Get names of groups */
        get("/bullboard/groups", "application/json", (req, resp) -> {
           Set<String> groups = _board.getGroupNames();
           return groups;
        }, ServerBase::toJson );

        
        /* 
         * GET /bullboard/<group>/senders 
         * Get callsigns of senders to a group 
         */
        get("/bullboard/*/senders", "application/json", (req, resp) -> {
            String sbid = req.splat()[0];
            BullBoard.SubBoard sb = _board.getBulletinGroup(sbid); 
            if (sb==null)
                return ERROR(resp, 404, "Group '"+sbid+"' not found");
            return sb.getSenders();
        }, ServerBase::toJson );
        
        
        /*
         * Submit bulletin.
         */
        put("/bullboard/*/messages", (req, resp) -> {
            AuthInfo user = getAuthInfo(req); 
            String sbid = req.splat()[0];
            if (user.callsign == null)
                return ERROR(resp,  401, "No callsign registered for user");
            
            var b = (BullSubmit) 
                ServerBase.fromJson(req.body(), BullSubmit.class);
            if (b==null)
                return ERROR(resp, 400, "Input format error");
            
            var grp = _board.getBulletinGroup(b.groupid);
            if (grp == null)
                grp = _board.createBulletinGroup(b.groupid);
                
            grp.post(user.callsign, b.bullid.charAt(0), b.text);
            return "Ok"; 
        });
        
        
        /* 
         * GET /bullboard/<group>/messages
         * Get all messages in a group. Note that this returns a list of lists.  
         */
        get("/bullboard/*/messages", "application/json", (req, resp) -> {
            String sbid = req.splat()[0];
            BullBoard.SubBoard sb = _board.getBulletinGroup(sbid); 
            if (sb==null)
                return ERROR(resp, 404, "Group '"+sbid+"' not found");
            return sb.getAll();
        }, ServerBase::toJson );
        
        
        /* 
         * GET /bullboard/<group>/messages/<sender>
         * Get messages in a group from a given sender 
         */
        get("/bullboard/*/messages/*", "application/json", (req, resp) -> {
            String sbid = req.splat()[0];
            String sender = req.splat()[1]; 
            BullBoard.SubBoard sb = _board.getBulletinGroup(sbid); 
            if (sb==null)
                return ERROR(resp, 404, "Group '"+sbid+"' not found");
            return sb.get(sender);
        }, ServerBase::toJson );
    }


}



