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
        
        

    }


}



