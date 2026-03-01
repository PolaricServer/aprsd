/* 
 * Copyright (C) 2018-2026 by Øyvind Hanssen (ohanssen@acm.org)
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
import java.io.*;
import java.util.stream.*;



/**
 * Implement REST API for system-related info.  
 */
public class SystemApi extends ServerBase {

    private AprsServerConfig _conf; 
    
    public SystemApi(AprsServerConfig conf) {
        super(conf);
        _conf = conf;
    }
    
    /* SAR mode info */
    public static class SarInfo {
        public String  filt;
        public String  descr; 
        public boolean hide; 
        public SarInfo() {}
        public SarInfo(String f, String d, boolean h)
            { filt=f; descr=d; hide=h; }
    }
    
    public static class OwnPos {
        public String sym;
        public String symtab;
        public double pos[];
        public OwnPos() {}
        public OwnPos(double[] p, String st, String s)
            { pos = p; symtab=st; sym=s; }
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
    
        protect("/system/sarmode", "operator");
        protect("/system/ownpos",  "admin");
    
    
        /******************************************
         * A simple ping service. Just return OK. 
         ******************************************/
        a.get("/system/ping", (ctx) -> {
            ctx.result("Ok");
        });
    
        /******************************************
         * Get all tags
         ******************************************/
        a.get("/system/tags", (ctx) -> {
            var tags = PointObject.getUsedTags();
            ctx.json(tags);
        });
        
        
        /******************************************
         * Get own position. 
         ******************************************/
        a.get("/system/ownpos", (ctx) -> {
            var p = _conf.getOwnPos();
            LatLng pos = (LatLng) p.getPosition();
            double[] cpos = null;
            if (pos!=null) 
                cpos = new double[] {pos.getLng(), pos.getLat()};
            ctx.json(new OwnPos(cpos, ""+p.getSymtab(), ""+p.getSymbol()));
        });
    
    
        /******************************************
         * Set own position
         ******************************************/
        a.put("/system/ownpos", (ctx) -> {
            var uid = getAuthInfo(ctx).userid;
            var op = (OwnPos) 
                ServerBase.fromJson(ctx.body(), OwnPos.class);
            if (op==null || op.pos==null) {
                ERROR(ctx, 400, "Couldn't parse input");   
                return; 
            }
            var p = _conf.getOwnPos();
            p.updatePosition(new Date(), 
                      new LatLng( op.pos[1], op.pos[0]), 
                      (op.symtab==null ? '/' : op.symtab.charAt(0)),
                      (op.sym==null ? 'c' : op.sym.charAt(0)));
            _conf.log().info("RestApi", "Own position changed by '"+uid+"'");
            systemNotification("ADMIN", "Own position changed by '"+uid+"'", 120);
            ctx.result("Ok");
        });
    
    
    
        /******************************************
         * Get a list of icons (file paths). 
         ******************************************/
         a.get("/system/icons/{subdir}", (ctx) -> {
            try {
                var subdir = ctx.pathParam("subdir");
                if (subdir==null || subdir.equals("default"))
                    subdir = "";
                var webdir = System.getProperties().getProperty("webdir", ".");
                FilenameFilter flt = (dir, f) -> 
                    { return f.matches(".*\\.(png|gif|jpg)"); };
    
                
                Comparator<File> cmp = (f1, f2) -> 
                    f1.getName().compareTo(f2.getName());       
            
                var icondir = new File(webdir+"/icons/"+subdir);
                var files = icondir.listFiles(flt);
                if (files==null) {
                    ERROR(ctx, 500, "Invalid file subdirectory for icons");
                    return;
                }
                    
                Arrays.sort(files, cmp);
                if (!subdir.equals("")) subdir += "/";
            
                List<String> fl = new ArrayList<String>();
                for (File x: files)
                    fl.add("/icons/"+subdir+x.getName());
            
                ctx.json(fl);
            }
            catch (Exception e) {
                e.printStackTrace(System.out);
            }
        });
        
    }
}


