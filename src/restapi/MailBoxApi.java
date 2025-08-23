/* 
 * Copyright (C) 2020-2025 by Øyvind Hanssen (ohanssen@acm.org)
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
 
package no.polaric.aprsd;
import no.polaric.aprsd.point.*;
import no.arctic.core.*;
import no.arctic.core.httpd.*;
import no.arctic.core.auth.*;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.*; 


/**
 * Implement REST API for messaging.  
 */
 
public class MailBoxApi extends ServerBase {

    private AprsServerAPI _api; 
    
    
    
    public MailBoxApi(AprsServerAPI api) {
        super(api);
        _api = api;
    }
    
    
    
    /** 
     * Return an error status message to client. 
     * FIXME: Move to superclass. 
     */
    public void ERROR(Context ctx, int status, String msg)
      { ctx.status(status); ctx.result(msg); }
      
    
    
    public MailBox.User getMbox(Context ctx) {
        var ses = getAuthInfo(ctx); 
        if (ses.userses instanceof MyWebServer.UserSessionInfo ssi)
            return ssi.mailbox;
        else
            return null;
    }
    
    
    
    /** 
     * Set up the webservices. 
     */
    public void start() {     
       
        protect("/mailbox");
        
        /* 
         * GET /mailbox 
         * Get content of the mailbox - list of messages 
         */
        a.get("/mailbox", (ctx) -> {
            var box = getMbox(ctx);
            if (box==null)
                ERROR(ctx, 401, "Unauthorized - no mailbox available");
            else
                ctx.json(box.getMessages());
        });
        

        
        /* 
         * POST /mailbox 
         * Post a message. The message will be routed to the proper recipient mailbox. 
         */
        a.post("/mailbox", (ctx) -> {
            try {
                var userid = getAuthInfo(ctx).userid;
                var callsign = getAuthInfo(ctx).callsign; 
                
                var msg = (MailBox.Message) ServerBase.fromJson(ctx.body(), MailBox.Message.class);       
                
                if (msg==null) {
                    ERROR(ctx, 400, "Cannot parse input");
                    return;
                }
                if (msg.from==null)
                    msg.from = userid;
                else if (!userid.equals(msg.from)) {
                    ERROR(ctx, 404, "Unknown from-address: "+msg.from);
                    return;
                }
                
                /* For raw APRS messages we use the user's callsign as a sender-address */
                if (msg.to.matches(".*@(aprs|APRS|Aprs)")) {
                    if (callsign == null || "".equals(callsign)) {
                        ERROR(ctx, 404, "Callsign is needed for raw APRS messages");
                    }
                    msg.from = callsign;
                }
                if (! MailBox.postMessage(_api, (MailBox.Message) msg) )
                    ERROR(ctx, 404, "Couldn't deliver message to: "+msg.to);
                else
                    ctx.result("Ok");
            }
            catch (Exception e) {
                e.printStackTrace(System.out);
                ERROR(ctx, 500, "Exception: "+e.getMessage());
            }
        });
        
                
        /*
         * DELETE /mailbox/<msgid>
         * Delete a message. Returns "Ok" even if message was not found.
         */
        a.delete("/mailbox/{id}", (ctx) -> {
            try {
                var msgid = ctx.pathParam("id");
                var box = getMbox(ctx);
                if (!msgid.matches("[0-9]+"))            
                    ERROR(ctx, 400, "Message id must be number");
                else {
                    box.remove(Long.parseLong(msgid));
                    ctx.result("Ok");
                }
            }
            catch (Exception e) {
                e.printStackTrace(System.out);
                ERROR(ctx, 500, "Exception: "+e.getMessage());  
            }
        });
        
        
    }


}



