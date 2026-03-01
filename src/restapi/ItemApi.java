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
 

package no.polaric.aprsd;
import no.polaric.aprsd.point.*;
import no.polaric.core.*;
import no.polaric.core.httpd.*;
import no.polaric.core.auth.*;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.*; 
import java.io.*;
import java.util.stream.*;
import no.polaric.aprsd.aprs.*;



/**
 * Implement REST API for items
 */
public class ItemApi extends ServerBase {

    private AprsServerConfig _conf; 
    
    public ItemApi(AprsServerConfig conf) {
        super(conf);
        _conf = conf;
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
    public Object ERROR(Context ctx, int status, String msg)
      { ctx.status(status); ctx.result(msg); return null;}
      
    
    
    protected boolean authForItem(Context ctx, PointObject x) {
        if (x==null)
            return false; 
        
        Source src = x.getSource();
        
        /* If source is null, check if item has a source identifier stored.
         * This handles the case where item is recovered from file but source
         * is not yet initialized from APRS input stream. In this case, deny
         * access to be safe, since we cannot verify if source is restricted.
         */
        if (src == null) {
            String srcId = null;
            
            /* For Station objects, getSourceId() returns the stored _source field */
            if (x instanceof Station) {
                srcId = ((Station)x).getSourceId();
            }
            /* For AprsObject, check the owner station's source ID */
            else if (x instanceof AprsObject) {
                Station owner = ((AprsObject)x).getOwner();
                if (owner != null)
                    srcId = owner.getSourceId();
            }
            /* For other types, use getSourceId() which may call getSource() */
            else {
                srcId = x.getSourceId();
            }
            
            /* If source ID is "(local)" or null, item has no source restriction */
            if (srcId == null || srcId.equals("(local)"))
                return true;
            /* Source ID exists but source not available - deny access to be safe */
            return false;
        }
    
        return (!src.isRestricted() 
           || x.tagIsOn("OPEN") 
           || (getAuthInfo(ctx) != null && getAuthInfo(ctx).login()));
    }
    
    
    
    protected boolean sarAuthForItem(Context ctx, PointObject x) {
        return (authForItem(ctx, x) && 
           ( x.hasTag(getAuthInfo(ctx).tagsAuth) || getAuthInfo(ctx).admin) );
    }
    
    
    
    private Object _itemInfo(Context ctx) {
        try {
            var ident = ctx.pathParam("ident");
            var xident = urlDecode(ident);
            var st = _conf.getDB().getItem(xident, null);
            if (st==null)
                return ERROR(ctx, 404, "Unknown tracker item: "+xident); 
            if (!authForItem(ctx, st))
                return ERROR(ctx, 403, "Not authorized for access to item");
            return st.getJsInfo();
        }
        catch(Exception e) {
            e.printStackTrace(System.out);
            return ERROR(ctx, 500, "Errror: "+e.getMessage());
        }
    }
    
    private Object _itemPos(Context ctx) {
        try {
            var ident = ctx.pathParam("ident");
            var xident = urlDecode(ident);
            var st = _conf.getDB().getItem(xident, null);
            if (st==null)
                return ERROR(ctx, 404, "Unknown tracker item: "+xident); 
            if (!authForItem(ctx, st))
                return ERROR(ctx, 403, "Not authorized for access to item");
            LatLng pos = st.getPosition();
            return new double[] {pos.getLng(), pos.getLat()};
        }
        catch(Exception e) {
            e.printStackTrace(System.out);
            return ERROR(ctx, 500, "Errror: "+e.getMessage());
        }
    }
    
    
    private Object _itemTrail(Context ctx) {
        try {
            // var ident = req.splat()[0];
            var ident = ctx.pathParam("ident");
            var st = _conf.getDB().getItem(ident, null);
            if (st==null)
                return ERROR(ctx, 404, "Unknown tracker item: "+ident); 
            if (!authForItem(ctx, st))
                return ERROR(ctx, 403, "Not aauthorized for access to item");
            
            var h = st.getTrail();                     
            var pp = st.getHItem();
            var fl = new ArrayList<JsTPoint>(); 
            for (var x:  h.points()) {
                var dist = x.getPosition().distance(pp.getPosition());
                fl.add(new JsTPoint(x.getTS(), x.speed, x.course, (int) Math.round(dist*1000), cleanPath(x.getPath())));
                pp = x; 
            }
            return fl;
        }
        catch (Exception e) {
            e.printStackTrace(System.out);
            return ERROR(ctx, 500, "Errror: "+e.getMessage());
        }
    }
    
    
    private List<JsPoint> _searchItems(Context ctx) {
        var srch = ctx.queryParam("srch");
        var tags = ctx.queryParam("tags");
        if (srch == null) 
            srch  = "__NOCALL__";
        var tagList = (tags==null || tags.equals("")) ? null : tags.split(",");
            
        try {
            List<JsPoint> result = 
                _conf.getDB().search(srch, tagList)
                    .stream()
                    .filter( x -> authForItem(ctx, x))
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
    }
    
    
    /*
     * Log, notify admin and other server about change of alias 
     * Should this be in ServerBase?
     */
    protected void notifyAlias(String ident, String alias, Context ctx) {
        var uid = getAuthInfo(ctx).userid; 
        if (alias==null)
            alias = "NULL";
        if (_conf.getRemoteCtl() != null)
            _conf.getRemoteCtl().sendRequestAll("ALIAS", ident+" "+alias, null);
        _conf.log().info("SystemApi", 
            "ALIAS: '"+alias+"' for '"+ident+"' by user '"+uid+"'");    
        _conf.getWebserver().notifyUser(uid, new ServerConfig.Notification
            ("system", "system", "Alias: '"+alias+ "' for '"+ident+"' set by user '"+uid+"'", new Date(), 10) );     
    }
    
    
    
    /*
     * Log, notify admin and other server about change of icon 
     * Should this be in ServerBase?
     */
    protected void notifyIcon(String ident, String icon, Context ctx) {
        var uid = getAuthInfo(ctx).userid;
        if (icon==null)
            icon="NULL";
        if (_conf.getRemoteCtl() != null)
            _conf.getRemoteCtl().sendRequestAll("ICON", ident+" "+icon, null);
        _conf.log().info("SystemApi", 
            "ICON: '"+icon+"' for '"+ident+"' by user '"+uid+"'");    
        _conf.getWebserver().notifyUser(uid, new ServerConfig.Notification
            ("system", "system", "Icon: '"+icon+ "' for '"+ident+"' set by user '"+uid+"'", new Date(), 10) );     
    } 
    
    
    /*
     * Log, notify admin and other server about change of tag 
     * Should this be in ServerBase?
     */
    protected void notifyTag(String ident, String tag, Context ctx) {
        var uid = getAuthInfo(ctx).userid; 
        if (tag==null)
            return;
        if (_conf.getRemoteCtl() != null)
            _conf.getRemoteCtl().sendRequestAll("TAG", ident+" "+tag, null);
        _conf.log().info("SystemApi", 
            "TAG: '"+tag+"' for '"+ident+"' by user '"+uid+"'");    
        if (!"RMANAGED".equals(tag))
            _conf.getWebserver().notifyUser(uid, new ServerConfig.Notification
                ("system", "system", "Tag: '"+tag+ "' for '"+ident+"' set by user '"+uid+"'", new Date(), 10) );     
    }
    
        
    
    protected void notifyRmTag(String ident, String tag, Context ctx) {
        var uid = getAuthInfo(ctx).userid; 
        if (tag==null)
            return;
        if (_conf.getRemoteCtl() != null)
            _conf.getRemoteCtl().sendRequestAll("RMTAG", ident+" "+tag, null);
        _conf.log().info("SystemApi", 
            "RMTAG: '"+tag+"' for '"+ident+"' by user '"+uid+"'");    
        if (!"RMANAGED".equals(tag))
            _conf.getWebserver().notifyUser(uid, new ServerConfig.Notification
                ("system", "system", "Tag: '"+tag+ "' for '"+ident+"' removed by user '"+uid+"'", new Date(), 10) );     
    }
    
    
    
    
    
    /** 
     * Set up the webservices. 
     */
    public void start() {     
    
        protect("/item", "admin");
        protect("/item/*/xinfo");  
        protect("/item/*/xpos");
        protect("/item/*/alias");
        protect("/item/*/reset");
        protect("/item/*/tags");
        protect("/item/*/tags/*","operator");
        protect("/item/*/chcolor");
        
        
        /*******************************************
         * Search items. Returns list of items. 
         * Parameters: 
         *    tags - comma separated list of tags
         *    srch - free text search
         *******************************************/         
        a.get("/items", (ctx) -> {
            ctx.json(_searchItems(ctx));
        });
        
        a.get("/xitems", (ctx) -> {
            ctx.json(_searchItems(ctx));
        });
        
        
        
        /*******************************************
         * Get Info about a given item
         * xinfo is for logged-in users
         *******************************************/
        a.get("/item/{ident}/info", (ctx) -> {
            var res = _itemInfo(ctx);
            if (res != null)
                ctx.json(res);
        });
        
        a.get("/item/{ident}/xinfo", (ctx) -> {
            var res = _itemInfo(ctx);
            if (res != null)
                ctx.json(res);
        });
        
        
        
        /*********************************************
         * Get Info about the position of a given item
         * xpos is for logged-in users
         *********************************************/
        a.get("/item/{ident}/pos", (ctx) -> {
            var res = _itemPos(ctx);
            if (res != null)
                ctx.json(res);
        });
        
        a.get("/item/{ident}/xpos", (ctx) -> {
            var res = _itemPos(ctx);
            if (res != null)
                ctx.json(res);
        });
        
        
        
        /*******************************************
         * Trail of items
         *******************************************/
        a.get("/item/{ident}/trail", (ctx) -> {
            var res = _itemTrail(ctx);
            if (res != null)
                ctx.json(res);
        });
        
        a.get("/item/{ident}/xtrail", (ctx) -> {
            var res = _itemTrail(ctx);
            if (res != null)
                ctx.json(res);
        });
        
                
        
        /*******************************************
         * Get alias/icon for a given item
         *******************************************/
        a.get("/item/{ident}/alias", (ctx) -> {
            var ident = ctx.pathParam("ident");
            var st = _conf.getDB().getItem(ident, null);
            if (st==null) {
                ERROR(ctx, 404, "Unknown tracker item: "+ident);
                return;
            }
            if (!authForItem(ctx, st)) {
                ERROR(ctx, 403, "Not authorized for access to item");
                return;
            }
            ctx.json(new ItemInfo.Alias(st.getAlias(), (st.iconIsNull() ? null : st.getIcon()))); 
        });
        
        
        
        /*******************************************
         * Update alias/icon for a given item
         *******************************************/
        a.put("/item/{ident}/alias", (ctx) -> { 
            var ident = ctx.pathParam("ident");
            var st = _conf.getDB().getItem(ident, null);
            if (st==null) {
                ERROR(ctx, 404, "Unknown tracker item: "+ident);
                return;
            }
            if (!sarAuthForItem(ctx, st)) {
                ERROR(ctx, 403, "Not authorized for access to item");
                return;
            }    
            if (st.hasTag("MANAGED") || st.hasTag("RMAN")) {
                ERROR(ctx, 401, "Alias can only be set by owner");
                return;
            }    
            ItemInfo.Alias aa = (ItemInfo.Alias) 
                ServerBase.fromJson(ctx.body(), ItemInfo.Alias.class);    
            if (a==null) {
                ERROR(ctx, 400, "Cannot parse input");
                return;
            }    
            if ( st.setAlias(aa.alias) ) 
                notifyAlias(ident, aa.alias, ctx);
            
            if ( st.setIcon(aa.icon) )
                notifyIcon(ident, aa.icon, ctx);
            ctx.result("Ok");
        });
        

        
        /*******************************************
         * Change color of trail
         *******************************************/
        a.put("/item/{ident}/chcolor", (ctx) -> {
            var ident = ctx.pathParam("ident");
            TrackerPoint st = _conf.getDB().getItem(ident, null);
            if (st==null) {
                ERROR(ctx, 404, "Unknown tracker item: "+ident); 
                return;
            }
            if (!sarAuthForItem(ctx, st)) {
                ERROR(ctx, 403, "Not authorized for access to item");    
                return;
            }
            st.nextTrailColor();
            ctx.result("Ok");
        });
        
        
        /*******************************************
         * Reset trail, etc for a given item
         *******************************************/
        a.put("/item/{ident}/reset", (ctx) -> {  
            var ident = ctx.pathParam("ident");
            var st = _conf.getDB().getItem(ident, null);
            if (st==null) {
                ERROR(ctx, 404, "Unknown tracker item: "+ident); 
                return;
            }
            if (!sarAuthForItem(ctx, st)) {
                ERROR(ctx, 403, "Not authorized for access to item");   
                return;
            }
            st.reset();
            ctx.result("Ok");
        });
        
        
        
        /******************************************
         * Get tags for a given item
         ******************************************/
        a.get("/item/{ident}/tags", (ctx) -> {
            var ident = ctx.pathParam("ident");
            var st = _conf.getDB().getItem(ident, null);
            if (st==null) {
                ERROR(ctx, 404, "Unknown tracker item: "+ident); 
                return;
            }
            if (!authForItem(ctx, st)) {
                ERROR(ctx, 403, "Not authorized for access to item");
                return;
            }
            var tags = st.getTags();
            ctx.json(tags);
        });
        
        
        
        /******************************************
         * Add tags to a given item
         ******************************************/
        a.post("/item/{ident}/tags", (ctx) -> {
            var ident = ctx.pathParam("ident");
            var st = _conf.getDB().getItem(ident, null);
            if (st==null) {
                ERROR(ctx, 404, "Unknown tracker item: "+ident); 
                return;
            }
            if (!sarAuthForItem(ctx, st)) {
                ERROR(ctx, 403, "Not authorized for access to item");
                return;
            }
            String[] a = (String[]) ServerBase.fromJson(ctx.body(), String[].class);
            for (String tag: a) {
                if (tag.charAt(0) != '+' && tag.charAt(0) != '-')
                    tag = "+" + tag;
                st.setTag(tag);
                notifyTag(st.getIdent(), tag, ctx);
            }
            ctx.result("Ok");   
        });
        
        
        
        /******************************************
         * Remove a tag from a given item
         ******************************************/
        a.delete("/item/{ident}/tags/{tag}", (ctx) -> {
            var ident = ctx.pathParam("ident");
            var tag = ctx.pathParam("tag"); 
            var st = _conf.getDB().getItem(ident, null);
            if (st==null) {
                ERROR(ctx, 404, "Unknown tracker item: "+ident); 
                return;
            }
            if (!sarAuthForItem(ctx, st)) {
                ERROR(ctx, 403, "Not authorized for access to item");
                return;
            }
            if (tag.charAt(0) != '+' && tag.charAt(0) != '-' && st.hasTag(tag)) {
                st.setTag("-" + tag); 
                notifyTag(st.getIdent(), "-"+tag, ctx);
            }
            else {
                if (tag.charAt(0) != '+' && tag.charAt(0) != '-')
                    tag = "+" + tag;
                st.removeTag(tag); 
                notifyRmTag(st.getIdent(), tag, ctx);
            }
            ctx.result("Ok"); 
        });
        
        
        
        /******************************************
         * Remove all items
         ******************************************/
        a.delete("/item", (ctx) -> {
            _conf.getDB().clearItems();
            ctx.result("Ok"); 
        });
        
        
        
        /*********************************************
         * Get telemetry description for a given item
         *********************************************/
        a.get("/telemetry/{ident}/descr", (ctx) -> {
            var ident = ctx.pathParam("ident");
            var st = _conf.getDB().getItem(ident, null);
            if (st==null) {
                ERROR(ctx, 404, "Unknown tracker item: "+ident); 
                return;
            }
            if (!(st instanceof Station) || ((Station) st).getTelemetry() == null) {
                ERROR(ctx, 404, "No telemetry found: "+ident); 
                return;
            }
            var tm = ((Station) st).getTelemetry(); 
            if (!tm.valid()) 
                ERROR(ctx, 404, "Telemetry is invalid: "+ident);
            else
                ctx.json(tm.getDescr());
        });
        
        
        /******************************************
         * Get telemetry metadata for a given item
         ******************************************/
        a.get("/telemetry/{ident}/meta", (ctx) -> {
            var ident = ctx.pathParam("ident");
            var st = _conf.getDB().getItem(ident, null);
            if (st==null) {
                ERROR(ctx, 404, "Unknown tracker item: "+ident); 
                return;
            }
            if (!(st instanceof Station) || ((Station) st).getTelemetry() == null) {
                ERROR(ctx, 404, "No telemetry found: "+ident); 
                return;
            }
            var tm= ((Station) st).getTelemetry(); 
            if (!tm.valid()) 
                ERROR(ctx, 404, "Telemetry is invalid: "+ident);
            else
                ctx.json(tm.getMeta());
        });
        
        
        /************************************************
         * Get telemetry current report for a given item
         *************************************************/
        a.get("/telemetry/{ident}/current", (ctx) -> {
            var ident = ctx.pathParam("ident");
            var st = _conf.getDB().getItem(ident, null);
            if (st==null) {
                ERROR(ctx, 404, "Unknown tracker item: "+ident); 
                return;
            }
            if (!(st instanceof Station) || ((Station) st).getTelemetry() == null) {
                ERROR(ctx, 404, "No telemetry found: "+ident); 
                return;
            }
            var tm= ((Station) st).getTelemetry(); 
            if (!tm.valid()) 
                ERROR(ctx, 404, "Telemetry is invalid: "+ident);
            else 
                ctx.json(tm.getCurrent());
        });
        
        
        /*************************************************
         * Get telemetry history report for a given item
         *************************************************/
        a.get("/telemetry/{ident}/history", (ctx) -> {
            var ident = ctx.pathParam("ident");
            var hours = ctx.queryParam("hours");
            
            var st = _conf.getDB().getItem(ident, null);
            if (st==null) {
                ERROR(ctx, 404, "Unknown tracker item: "+ident); 
                return;
            }
            if (!(st instanceof Station) || ((Station) st).getTelemetry() == null) {
                ERROR(ctx, 404, "No telemetry found: "+ident); 
                return;
            }
            var tm = ((Station) st).getTelemetry(); 
            if (!tm.valid())
                ERROR(ctx, 404, "Telemetry is invalid: "+ident);
            if (hours==null || hours.equals(""))
                ctx.json(tm.getHistory());
            else try {
                int h = Integer.parseInt(hours);
                ctx.json(tm.getHistory(h));
            }
            catch (NumberFormatException e) {
                ERROR(ctx, 400, "Invalid query param: "+hours);
            }
        });
    }

}


