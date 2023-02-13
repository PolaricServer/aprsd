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
 * Implement REST API for messaging.  
 */
 
public class MailBoxApi extends ServerBase {

    private ServerAPI _api; 
    
    
    
    public MailBoxApi(ServerAPI api) {
        super(api);
        _api = api;
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
       
        
        /* 
         * GET /mailbox 
         * Get content of the mailbox - list of messages 
         */
        get("/mailbox", "application/json", (req, resp) -> {
            var box = getAuthInfo(req).mailbox;
            if (box==null)
                return ERROR(resp, 401, "Unauthorized - no mailbox available");
            return box.getMessages();
        }, ServerBase::toJson );
        

        
        /* 
         * POST /mailbox 
         * Post a message. The message will be routed to the proper recipient mailbox. 
         */
        post("/mailbox", (req, resp) -> {
            try {
                var userid = getAuthInfo(req).userid;
                var callsign = getAuthInfo(req).callsign; 
                
                var msg = (MailBox.Message) ServerBase.fromJson(req.body(), MailBox.Message.class);       
                
                if (msg==null)
                    return ERROR(resp, 400, "Invalid input format");
                if (msg.from==null)
                    msg.from = userid;
                else if (!userid.equals(msg.from))
                    return ERROR(resp, 404, "Unknown from-address: "+msg.from);
                    
                /* For raw APRS messages we use the user's callsign as a sender-address */
                if (msg.to.matches(".*@(aprs|APRS|Aprs)")) {
                    if (callsign == null || "".equals(callsign))
                        return ERROR(resp, 404, "Callsign is needed for raw APRS messages");
                    msg.from = callsign;
                }
                if (! MailBox.postMessage(_api, (MailBox.Message) msg) )
                    return ERROR(resp, 404, "Couldn't deliver message to: "+msg.to);
                return "Ok";
            }
            catch (Exception e) {
                e.printStackTrace(System.out);
                return ERROR(resp, 500, "Exception: "+e.getMessage());
            }
        });
        
                
        /*
         * DELETE /mailbox/<msgid>
         * Delete a message. Returns "Ok" even if message was not found.
         */
        delete("/mailbox/*", (req, resp) -> {
            var msgid = req.splat()[0];
            var box = getAuthInfo(req).mailbox;
            if (!msgid.matches("[0-9]+"))            
                return ERROR(resp, 400, "Message id must be number");
            box.remove(Long.parseLong(msgid));
            return "Ok";
        });
        
        
    }


}



