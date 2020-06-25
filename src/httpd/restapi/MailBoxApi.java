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
 * Implement REST API for user-related info.  
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
                ERROR(resp, 401, "Unauthorized - no mailbox available");
            return box.getMessages();
        }, ServerBase::toJson );
        
        
        
        post("/mailbox", (req, resp) -> {
            var msg = ServerBase.fromJson(req.body(), MailBox.Message.class);
            MailBox.postMessage((MailBox.Message) msg);
            return "Ok";   
        });
        
        
    }


}



