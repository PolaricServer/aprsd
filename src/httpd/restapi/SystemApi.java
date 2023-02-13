/* 
 * Copyright (C) 2018-2021 by Øyvind Hanssen (ohanssen@acm.org)
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
import java.util.stream.*;
import uk.me.jstott.jcoord.*;
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
    public String ERROR(Response resp, int status, String msg)
      { resp.status(status); return msg; }
      
    
    
    /** 
     * Set up the webservices. 
     */
    public void start() {     
    
    
        /******************************************
         * Get all tags
         ******************************************/
        get("/system/tags", "application/json", (req, resp) -> {
            var tags = PointObject.getUsedTags();
            return tags;
        }, ServerBase::toJson );
        
        
        /******************************************
         * Get own position. 
         ******************************************/
        get("/system/ownpos", "application/json", (req, resp) -> {
            var p = _api.getOwnPos();
            LatLng pos = (LatLng) p.getPosition();
            double[] cpos = null;
            if (pos!=null) 
                cpos = new double[] {pos.getLng(), pos.getLat()};
            return new OwnPos(cpos, ""+p.getSymtab(), ""+p.getSymbol());
        }, ServerBase::toJson );
    
    
        /******************************************
         * Set own position
         ******************************************/
        put("/system/ownpos", (req, resp) -> {
            var uid = getAuthInfo(req).userid;
            var op = (OwnPos) 
                ServerBase.fromJson(req.body(), OwnPos.class);
            if (op==null || op.pos==null)
                return ERROR(resp, 400, "Invalid input format");   
            var p = _api.getOwnPos();
            p.updatePosition(new Date(), 
                      new LatLng( op.pos[1], op.pos[0]), 
                      (op.symtab==null ? '/' : op.symtab.charAt(0)),
                      (op.sym==null ? 'c' : op.sym.charAt(0)));
            _api.log().info("RestApi", "Own position changed by '"+uid+"'");
            systemNotification("ADMIN", "Own position changed by '"+uid+"'", 120);
            return "Ok";
        });
    
    
        /******************************************
         * Get SAR mode settings
         ******************************************/
         get("/system/sarmode", "application/json", (req, resp) -> {
            SarMode sm = _api.getSar(); 
            if (sm==null)
                return null;
            else
                return new SarInfo(sm.getFilter(), sm.getReason(), sm.isAliasHidden()); 
         }, ServerBase::toJson );

         
         
         /*****************************************
          * Put SAR mode settings
          *****************************************/
         put("/system/sarmode", (req, resp) -> {
            var uid = getAuthInfo(req).userid;
            var sm = _api.getSar(); 
            var si = (SarInfo) 
                ServerBase.fromJson(req.body(), SarInfo.class);
                
            /* Return if no change */
            if ((si==null && sm==null) || (si != null && sm != null))
                return "Ok (no change)";
            if (si==null)
                _api.clearSar(); 
            else
                _api.setSar(si.descr, uid, si.filt, si.hide);
            
            /* Consider moving this to the setSar method */
            _api.log().info("RestApi", "SAR mode changed by '"+uid+"'");
            systemNotification("ADMIN", "SAR mode changed by '"+uid+"'", 120);
            if (_api.getRemoteCtl() != null) {
                if (si==null)
                    _api.getRemoteCtl().sendRequestAll("SAR", "OFF", null);
                else
                    _api.getRemoteCtl().sendRequestAll("SAR", uid+" "+si.filt+" "+si.descr, null);
            }
            return "Ok";
         });
         
         
    
        /******************************************
         * Get a list of icons (file paths). 
         ******************************************/
         get("/system/icons/*", "application/json", (req, resp) -> {
            try {
                var subdir = req.splat()[0];
                if (subdir==null || subdir.equals("default"))
                    subdir = "";
                var webdir = System.getProperties().getProperty("webdir", ".");
                FilenameFilter flt = (dir, f) -> 
                    { return f.matches(".*\\.(png|gif|jpg)"); } ;
                Comparator<File> cmp = (f1, f2) -> 
                    f1.getName().compareTo(f2.getName());       
            
                var icondir = new File(webdir+"/icons/"+subdir);
                var files = icondir.listFiles(flt);
                if (files==null) 
                    return ERROR(resp, 500, "Invalid file subdirectory for icons");
                
                Arrays.sort(files, cmp);
                if (!subdir.equals("")) subdir += "/";
            
                List<String> fl = new ArrayList<String>();
                for (File x: files)
                fl.add("/icons/"+subdir+x.getName());
            
                return fl;
            }
            catch (Exception e) {
                e.printStackTrace(System.out);
                return null;
            }
            
        }, ServerBase::toJson );
        
    }
}


