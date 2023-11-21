/* 
 * Copyright (C) 2018-2023 by Øyvind Hanssen (ohanssen@acm.org)
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
import no.polaric.aprsd.*;



/**
 * Implement REST API for items
 */
public class ItemApi extends ServerBase {

    private ServerAPI _api; 
    
    public ItemApi(ServerAPI api) {
        super(api);
        _api = api;
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
      
    
    
    protected boolean authForItem(Request req, PointObject x) {
        if (x==null)
            return false; 
        if (x.getSource() == null) 
            return true; /* FIXME */ 

        return (!x.getSource().isRestricted() 
           || x.tagIsOn("OPEN") 
           || (getAuthInfo(req) != null && getAuthInfo(req).login()));
    }
    
    
    protected boolean sarAuthForItem(Request req, PointObject x) {
        return (authForItem(req, x) && getAuthInfo(req).itemSarAuth(x));
    }
    
    
    
    private Object _itemInfo(Request req, Response resp) {
        try {
            var ident = req.splat()[0];
            var st = _api.getDB().getItem(ident, null);
            if (st==null)
                return ERROR(resp, 404, "Unknown tracker item: "+ident); 
            if (!authForItem(req, st))
                return ERROR(resp, 403, "Not authorized for access to item");
            return st.getJsInfo();
        }
        catch(Exception e) {
            e.printStackTrace(System.out);
            return ERROR(resp, 500, "Errror: "+e.getMessage());
        }
    }
    
    private Object _itemPos(Request req, Response resp) {
        try {
            var ident = req.splat()[0];
            var st = _api.getDB().getItem(ident, null);
            if (st==null)
                return ERROR(resp, 404, "Unknown tracker item: "+ident); 
            if (!authForItem(req, st))
                return ERROR(resp, 403, "Not aauthorized for access to item");
            LatLng pos = st.getPosition();
            return new double[] {pos.getLng(), pos.getLat()};
        }
        catch(Exception e) {
            e.printStackTrace(System.out);
            return ERROR(resp, 500, "Errror: "+e.getMessage());
        }
    }
    
    
    private Object _itemTrail(Request req, Response resp) {
        try {
            var ident = req.splat()[0];
            var st = _api.getDB().getItem(ident, null);
            if (st==null)
                return ERROR(resp, 404, "Unknown tracker item: "+ident); 
            if (!authForItem(req, st))
                return ERROR(resp, 403, "Not aauthorized for access to item");
            
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
            return ERROR(resp, 500, "Errror: "+e.getMessage());
        }
    }
    
    
    private List<JsPoint> _searchItems(Request req, Response resp) {
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
    }
    
    
    
    
    /** 
     * Set up the webservices. 
     */
    public void start() {     
    

        /*******************************************
         * Search items. Returns list of items. 
         * Parameters: 
         *    tags - comma separated list of tags
         *    srch - free text search
         *******************************************/         
        get("/items", "application/json", (req, resp) -> {
            return _searchItems(req, resp);
        }, ServerBase::toJson );
        
        get("/xitems", "application/json", (req, resp) -> {
            return _searchItems(req, resp);
        }, ServerBase::toJson );
        
        
        
        /*******************************************
         * Get Info about a given item
         * xinfo is for logged-in users
         *******************************************/
        get("/item/*/info", "application/json", (req, resp) -> {
            return _itemInfo(req, resp);
        }, ServerBase::toJson );
        
        get("/item/*/xinfo", "application/json", (req, resp) -> {
            return _itemInfo(req, resp);
        }, ServerBase::toJson );
        
        
        
        /*********************************************
         * Get Info about the position of a given item
         * xpos is for logged-in users
         *********************************************/
        get("/item/*/pos", "application/json", (req, resp) -> {
            return _itemPos(req, resp);
        }, ServerBase::toJson );
        
        get("/item/*/xpos", "application/json", (req, resp) -> {
            return _itemPos(req, resp);
        }, ServerBase::toJson );
        
        
        
        /*******************************************
         * Trail of items
         *******************************************/
        get("/item/*/trail", "application/json", (req, resp) -> {
            return _itemTrail(req, resp);
        }, ServerBase::toJson );
        
        get("/item/*/xtrail", "application/json", (req, resp) -> {
            return _itemTrail(req, resp);
        }, ServerBase::toJson );
        
                
        
        /*******************************************
         * Get alias/icon for a given item
         *******************************************/
        get("/item/*/alias", "application/json", (req, resp) -> {
            var ident = req.splat()[0];
            var st = _api.getDB().getItem(ident, null);
            if (st==null)
                return ERROR(resp, 404, "Unknown tracker item: "+ident); 
            if (!authForItem(req, st))
                return ERROR(resp, 403, "Not authorized for access to item");
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
            if (!sarAuthForItem(req, st))
                return ERROR(resp, 403, "Not authorized for access to item");
                
            if (st.hasTag("MANAGED") || st.hasTag("RMAN"))
                return ERROR(resp, 401, "Alias can only be set by owner");
                
            ItemInfo.Alias a = (ItemInfo.Alias) 
                ServerBase.fromJson(req.body(), ItemInfo.Alias.class);    
            if (a==null)
                return ERROR(resp, 400, "Cannot parse input");
                
            if ( st.setAlias(a.alias) ) 
                notifyAlias(ident, a.alias, req);
            
            if ( st.setIcon(a.icon) )
                notifyIcon(ident, a.icon, req);
            return "Ok";
        });
        

        
        /*******************************************
         * Change color of trail
         *******************************************/
        put("/item/*/chcolor", "application/json", (req, resp) -> {
            var ident = req.splat()[0];
            TrackerPoint st = _api.getDB().getItem(ident, null);
            if (st==null)
                return ERROR(resp, 404, "Unknown tracker item: "+ident);  
            if (!sarAuthForItem(req, st))
                return ERROR(resp, 403, "Not authorized for access to item");    
            st.nextTrailColor();
            return "Ok";
        });
        
        
        /*******************************************
         * Reset trail, etc for a given item
         *******************************************/
        put("/item/*/reset", (req, resp) -> {
            var ident = req.splat()[0];        
            var st = _api.getDB().getItem(ident, null);
            if (st==null)
                return ERROR(resp, 404, "Unknown tracker item: "+ident); 
            if (!sarAuthForItem(req, st))
                return ERROR(resp, 403, "Not authorized for access to item");   
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
            if (!authForItem(req, st))
                return ERROR(resp, 403, "Not authorized for access to item");
                
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
            if (!sarAuthForItem(req, st))
                return ERROR(resp, 403, "Not authorized for access to item");
                
            String[] a = (String[]) ServerBase.fromJson(req.body(), String[].class);
            for (String tag: a) {
                if (tag.charAt(0) != '+' && tag.charAt(0) != '-')
                    tag = "+" + tag;
                st.setTag(tag);
                notifyTag(st.getIdent(), tag, req);
            }
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
            if (!sarAuthForItem(req, st))
                return ERROR(resp, 403, "Not authorized for access to item");
                
            if (tag.charAt(0) != '+' && tag.charAt(0) != '-' && st.hasTag(tag)) {
                st.setTag("-" + tag); 
                notifyTag(st.getIdent(), "-"+tag, req);
            }
            else {
                if (tag.charAt(0) != '+' && tag.charAt(0) != '-')
                    tag = "+" + tag;
                st.removeTag(tag); 
                notifyRmTag(st.getIdent(), tag, req);
            }
            return "Ok"; 
        });
        
        
        
        /*********************************************
         * Get telemetry description for a given item
         *********************************************/
        get("/telemetry/*/descr", "application/json", (req, resp) -> {
            var ident = req.splat()[0];        
            var st = _api.getDB().getItem(ident, null);
            if (st==null)
                return ERROR(resp, 404, "Unknown tracker item: "+ident); 
            if (!(st instanceof Station) || ((Station) st).getTelemetry() == null)
                return ERROR(resp, 404, "No telemetry found: "+ident); 
            var tm = ((Station) st).getTelemetry(); 
            if (!tm.valid())
                return ERROR(resp, 404, "Telemetry is invalid: "+ident);

            return tm.getDescr();
        }, ServerBase::toJson );
        
        
        /******************************************
         * Get telemetry metadata for a given item
         ******************************************/
        get("/telemetry/*/meta", "application/json", (req, resp) -> {
            var ident = req.splat()[0];        
            var st = _api.getDB().getItem(ident, null);
            if (st==null)
                return ERROR(resp, 404, "Unknown tracker item: "+ident); 
            if (!(st instanceof Station) || ((Station) st).getTelemetry() == null)
                return ERROR(resp, 404, "No telemetry found: "+ident); 
            var tm= ((Station) st).getTelemetry(); 
            if (!tm.valid())
                return ERROR(resp, 404, "Telemetry is invalid: "+ident);

            return tm.getMeta();
        }, ServerBase::toJson );
        
        
        /************************************************
         * Get telemetry current report for a given item
         *************************************************/
        get("/telemetry/*/current", "application/json", (req, resp) -> {
            var ident = req.splat()[0];        
            var st = _api.getDB().getItem(ident, null);
            if (st==null)
                return ERROR(resp, 404, "Unknown tracker item: "+ident); 
            if (!(st instanceof Station) || ((Station) st).getTelemetry() == null)
                return ERROR(resp, 404, "No telemetry found: "+ident); 
            var tm= ((Station) st).getTelemetry(); 
            if (!tm.valid())
                return ERROR(resp, 404, "Telemetry is invalid: "+ident);

            return tm.getCurrent();
        }, ServerBase::toJson );
        
        
        /*************************************************
         * Get telemetry history report for a given item
         *************************************************/
        get("/telemetry/*/history", "application/json", (req, resp) -> {
            var ident = req.splat()[0];    
            var hours = req.queryParams("hours");
            
            var st = _api.getDB().getItem(ident, null);
            if (st==null)
                return ERROR(resp, 404, "Unknown tracker item: "+ident); 
            if (!(st instanceof Station) || ((Station) st).getTelemetry() == null)
                return ERROR(resp, 404, "No telemetry found: "+ident); 
            var tm = ((Station) st).getTelemetry(); 
            if (!tm.valid())
                return ERROR(resp, 404, "Telemetry is invalid: "+ident);
            if (hours==null || hours.equals(""))
                return tm.getHistory();
            try {
                int h = Integer.parseInt(hours);
                return tm.getHistory(h);
            }
            catch (NumberFormatException e) {
                return ERROR(resp, 400, "Invalid query param: "+hours);
            }
                
        }, ServerBase::toJson );
    }

}


