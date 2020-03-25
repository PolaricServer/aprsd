/* 
 * Copyright (C) 2018-2019 by Øyvind Hanssen (ohanssen@acm.org)
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
    
    
    public static class JsPoint {
        public String ident;
        public String name;
        public String alias;
        public double pos[];
        public Date   updated;
        public String descr;
        public int    speed; 
        public int    course; 
        public JsPoint(String id, String nm, String a, double p[], Date u, String d, int sp, int crs) 
           { ident=id; name = nm; alias=a; pos=p; updated=u; descr=d; speed=sp; course=crs; }
    }
    
    
    public static class JsTPoint {
        public Date time;
        public int speed;
        public int course;
        public int dist;
        public String path;
        public JsTPoint(Date t, int sp, int c, int d, String pt)
            { time=t; speed=sp; course=c; dist = d; path=pt; }
    }
    
    
    
    /** 
     * Return an error status message to client. 
     * FIXME: Move to superclass. 
     */
    public String ERROR(Response resp, int status, String msg)
      { resp.status(status); return msg; }
      
    
        
    protected String cleanPath(String txt) { 
        return txt.replaceAll("((WIDE|TRACE|SAR|NOR)[0-9]*(\\-[0-9]+)?\\*?,?)|(qA.),?", "")
           .replaceAll("\\*", "").replaceAll(",+|(, )+", ", ");
    }
    
    
    protected boolean authForItem(Request req, PointObject x) {
        return (!x.getSource().isRestricted() || getAuthInfo(req).login() || x.hasTag("OPEN"));
    }
    
    
    
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
                    _api.getRemoteCtl().sendRequestAll("SAR OFF", null);
                else
                    _api.getRemoteCtl().sendRequestAll("SAR "+uid+" "+si.filt+" "+si.descr, null);
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
        
        
        
        
        /*******************************************
         * Search items. Returns list of items. 
         * Parameters: 
         *    tags - comma separated list of tags
         *    srch - free text search
         *******************************************/
        get("/items", "application/json", (req, resp) -> {
            var srch = req.queryParams("srch");
            var tags = req.queryParams("tags");
            if (srch == null) 
                srch  = "__NOCALL__";
            var tagList = (tags==null || tags.equals("")) ? null : tags.split(",");
            
            try {
            List<JsPoint> result = 
                _api.getDB().search(srch, tagList)
                    .stream()
                    .filter( x -> authForItem(req, x))
                    .map( x -> new JsPoint( 
                        x.getIdent(), 
                        x.getDisplayId(), x.getAlias(), 
                        x.getPosition()==null ? null :
                            new double[] { ((LatLng)x.getPosition()).getLng(), ((LatLng)x.getPosition()).getLat() }, 
                        x.getUpdated(), x.getDescr(), x.getSpeed(), x.getCourse() ) )
                    .collect(Collectors.toList());
            return result;
            } catch (Exception e)
              {e.printStackTrace(System.out); return null;}
        }, ServerBase::toJson );
        
        
        
        
        // FIXME: Should be 'items' instead of 'item'? 
        
        /*******************************************
         * Get alias/icon for a given item
         *******************************************/
        get("/item/*/alias", "application/json", (req, resp) -> {
            var ident = req.splat()[0];
            var st = _api.getDB().getItem(ident, null);
            if (!authForItem(req, st))
                return ERROR(resp, 401, "Uauthorized for access to item");
            if (st==null)
                return ERROR(resp, 404, "Unknown tracker item: "+ident); 
            return new ItemInfo.Alias(st.getAlias(), (st.iconIsNull() ? null : st.getIcon())); 
        }, ServerBase::toJson );
        
        
        
        /*******************************************
         * Update alias/icon for a given item
         *******************************************/
        put("/item/*/alias", (req, resp) -> {
            var ident = req.splat()[0];        
            var st = _api.getDB().getItem(ident, null);
            if (st==null)
                return ERROR(resp, 404, "Unknown tracker item: "+ident); 
            ItemInfo.Alias a = (ItemInfo.Alias) 
                ServerBase.fromJson(req.body(), ItemInfo.Alias.class);    
            if (a==null)
                return ERROR(resp, 400, "Invalid input format");
            if ( st.setAlias(a.alias) ) 
                notifyAlias(ident, a.alias, req); 
            if ( st.setIcon(a.icon) )
                notifyIcon(ident, a.icon, req);
            return "Ok";
        });
        
        
        
        /*******************************************
         * Trail items
         *******************************************/
        get("/item/*/trail", "application/json", (req, resp) -> {
            var ident = req.splat()[0];
            var st = _api.getDB().getItem(ident, null);
            if (st==null)
                return ERROR(resp, 404, "Unknown tracker item: "+ident); 
                
            var h = st.getTrail();                     
            var pp = st.getHItem();
            var fl = new ArrayList<JsTPoint>(); 
            for (var x:  h.points()) {
                var dist = x.getPosition().toLatLng().distance(pp.getPosition().toLatLng());
                fl.add(new JsTPoint(x.getTS(), x.speed, x.course, (int) Math.round(dist*1000), cleanPath(x.pathinfo)));
                pp = x; 
            }
                
            return fl;
        }, ServerBase::toJson );
        
        
        
        /*******************************************
         * Reset trail, etc for a given item
         *******************************************/
        put("/item/*/reset", (req, resp) -> {
            var ident = req.splat()[0];        
            var st = _api.getDB().getItem(ident, null);
            if (st==null)
                return ERROR(resp, 404, "Unknown tracker item: "+ident); 
            st.reset();
            return "Ok";
        });
        
        
        
        /******************************************
         * Get tags for a given item
         ******************************************/
        get("/item/*/tags", "application/json", (req, resp) -> {
            var ident = req.splat()[0];        
            var st = _api.getDB().getItem(ident, null);
            if (st==null)
                return ERROR(resp, 404, "Unknown tracker item: "+ident); 
            
            var tags = st.getTags();
            return tags;
        }, ServerBase::toJson );
        
        
        
        /******************************************
         * Add tags to a given item
         ******************************************/
        post("/item/*/tags", (req, resp) -> {
            var ident = req.splat()[0];        
            var st = _api.getDB().getItem(ident, null);
            if (st==null)
                return ERROR(resp, 404, "Unknown tracker item: "+ident); 
               
            String[] a = (String[]) ServerBase.fromJson(req.body(), String[].class);
            for (String tag: a)
                st.setTag(tag);
            return "Ok";   
        });
        
        
        
        /******************************************
         * Remove a tag from a given item
         ******************************************/
        delete("/item/*/tags/*", (req, resp) -> {
            var ident = req.splat()[0];   
            var tag = req.splat()[1];
            var st = _api.getDB().getItem(ident, null);
            if (st==null)
                return ERROR(resp, 404, "Unknown tracker item: "+ident); 
            st.removeTag(tag);
            return "Ok"; 
        });
        
    }

}


