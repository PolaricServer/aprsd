/* 
 * Copyright (C) 2017-2025 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import no.polaric.aprsd.aprs.*;
import no.polaric.core.httpd.*;
import no.polaric.core.auth.*;
import no.polaric.aprsd.point.*;
import no.polaric.aprsd.util.*;
import io.javalin.websocket.*; 
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.io.IOException;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import no.polaric.aprsd.filter.*;


/**
 * Map overlay updater using Websockets.  
 */
public class JsonMapUpdater extends MapUpdater implements Notifier, JsonPoints
{
    int _max_ovr_size = 20000;
    StationDB _db; 
    
    public class Client extends MapUpdater.Client 
    {
        private Set<String> items = new HashSet<String>(1000);
       
        public Client(WsContext ctx) 
            {  super(ctx); }
   
          
        public void subscribe() {
            /* It is possible to remove only items that move out of the viewport? 
             * Or could items be expired. Use a LRU cache semantics? 
             */
            if (!_keep) 
                items.clear();
        }
          
   
        /** Returns the overlay. JSON format. */
        @Override public String getOverlayData(boolean metaonly) {
            try {
                _updates++;
                JsOverlay mu = new JsOverlay(_filter);
                if (mu==null) {
                    _conf.log().error("JsonMapUpdater", "Cannot allocate JsOverlay object.");
                    return null;
                }
                mu.authorization = _auth;
                mu.nclients = _conf.getWebserver().nClients();
                if (!metaonly)
                    addPoints(mu);
                return serializeJson(mu);
            }
            catch (Exception e) {
                _conf.log().error("JsonMapUpdater", "Exception in generating overlay.");
                e.printStackTrace(System.out);
                return null;
            }
        }
       
       
       
       
        /**
         * Check if an item should be restricted based on its source.
         * Handles the case where item is recovered from file but source is not yet 
         * initialized from APRS input stream.
         */
        private boolean shouldRestrictItem(TrackerPoint s) {
            Source src = s.getSource();
            
            /* If source is available, check if it's restricted */
            if (src != null)
                return src.isRestricted();
            
            /* If source is null, check if item has a source identifier stored.
             * This handles items recovered from file where source is not yet initialized.
             */
            String srcId = null;
            if (s instanceof Station) {
                srcId = ((Station)s).getSourceId();
            }
            else if (s instanceof AprsObject) {
                Station owner = ((AprsObject)s).getOwner();
                if (owner != null)
                    srcId = owner.getSourceId();
            }
            else {
                srcId = s.getSourceId();
            }
            
            /* If source ID exists but source not available, treat as restricted to be safe */
            if (srcId != null && !srcId.equals("(local)"))
                return true;
            
            /* No source restriction */
            return false;
        }
       
       
        /** Add trackerpoints to overlay */
        private void addPoints(JsOverlay mu) 
        {
            /* Output APRS objects */
            if (_db == null)
                _conf.log().error("JsonMapUpdater", "_conf.getDB() returns null");
            else 
            {
                mu.points = new LinkedList<JsPoint>();
                mu.delete = new LinkedList<String>();
                mu.lines = new LinkedList<JsLine>();
                boolean allowed = login(); 
                AuthInfo ai = authInfo();
                RuleSet vfilt = ViewFilter.getFilter(_filter, allowed);      
                List<TrackerPoint> itemlist =  _db.search(_uleft, _lright, vfilt);
                if (itemlist.size() > _max_ovr_size) {
                    mu.overload = true;
                    return;
                }
                
                for (TrackerPoint s:itemlist) 
                {          
                    if (!s.isChanging() && items.contains(s.getIdent()))
                        continue;
                                  
                    items.add(s.getIdent());
                    
                    /* Apply filter. */ 
                    Action action = vfilt.apply(s, _scale); 
                    if ( s.getPosition() == null ||
                         ( shouldRestrictItem(s) && !action.isPublic() && !allowed) ||
                           action.hideAll() ||
                           (_tag != null && !s.hasTag(_tag))
                        )
                      continue; 

                    /* Add point to delete-list */
                    if (!s.visible()) {
                        if (!_keep) 
                            continue;
                        mu.delete.add(s.getIdent());
                    }
                    else {  
                        /* Add item to overlay */
                        JsPoint p = createPoint(s, action, allowed, ai);
                        if (p!=null) 
                            mu.points.add(p);
                                
                        /* Add lines (that connect points) to list */
                        if (action.showPath() && s instanceof Station && ((AprsPoint)s).isInfra())
                           addLines(mu, (Station) s, _uleft, _lright); 
                    }
                }
                
                /* Add signs to list */
                for (Signs.Item s: Signs.search(userName(), group(), _scale, _uleft, _lright)) {
                    JsPoint p = createSign(s); 
                    if (p!=null)
                       mu.points.add(p);
                } 
                
            }
        }
        

        
        /**
         * Convert sign to JSON Point.
         */
        private JsPoint createSign(Signs.Item s) {
            LatLng ref = s.getPosition(); 
            if (ref == null)
                return null;
            JsPoint x = new JsPoint(); 
            
            x.ident = s.getIdent();
            x.type  = s.getType();
            x.pos   = new double[] {ref.getLng(), ref.getLat()};
            x.title = s.getDescr() == null ? "" : fixText(s.getDescr());
            x.href  = s.getUrl() == null ? "" : s.getUrl();
            x.icon  = "/icons/"+ s.getIcon();
            x.own   = (userName() != null && userName().equals(s.getUser()));
            return x;
        }
        
        

        /* Authorize to do changes on point (item) */
        public static boolean itemSarAuth(AuthInfo a, PointObject x) {
            return x.hasTag(a.tagsAuth) || a.admin; 
        }
        
        
        
        /** Convert Tracker point to JSON point. 
         * Return null if point has no position.  
         */
        private JsPoint createPoint(TrackerPoint s, Action action, boolean allowed, AuthInfo ai) {
            LatLng ref = s.getPosition(); 
            if (ref == null) 
                return null;
             
            JsPoint x  = new JsPoint();
            
            /* Indicate if user has authorization to change point. */
            x.sarAuth = (ai != null && itemSarAuth(ai, s)); 
            
            x.ident   = s.getIdent();
            
            /* Cache trail to avoid calling getTrail() multiple times */
            Seq<TPoint> trail = s.getTrail();
            x.label   = createLabel(s, action, allowed, trail);
            x.pos     = new double[] {roundDeg(ref.getLng()), roundDeg(ref.getLat())};
            x.title   = s.getDescr() == null ? "" : fixText(s.getDescr()); 
            x.redraw  = s.isChanging();
            x.own     = (s instanceof AprsObject) 
                        && _db.getOwnObjects().hasObject(s.getIdent().replaceFirst("@.*",""));
            x.aprs    = (s instanceof AprsPoint); 
            x.telemetry = s.hasTag("APRS.telemetry");
           
            String icon = action.getIcon(s.getIcon()); 
            if (s.iconOverride())  
               icon = s.getIcon(); 
            x.icon = "/icons/"+ (icon != null ? icon : icon()); 
            x.trail = createTrail(trail, s, action);
            return x;
        }
       
       
       
        /** Create label or return null if label is to be hidden. */
        private JsLabel createLabel(TrackerPoint s, Action action, boolean allowed, Seq<TPoint> trail) {
            boolean showSarInfo = allowed || !action.hideAlias();
            
            JsLabel lbl = new JsLabel();
           
            lbl.style = (!(trail.isEmpty()) ? "lmoving" : "lstill");
            if (s instanceof AprsObject)
                lbl.style = "lobject"; 
            lbl.style = lbl.style + " " + action.getStyle();
            lbl.id = s.getDisplayId(showSarInfo);
            lbl.hidden = (action.hideIdent() || s.isLabelHidden() );
            return lbl;
        }
       
       
       
        private JsTrail createTrail(Seq<TPoint> trail, TrackerPoint s, Action action) {
            Seq<TPoint> h = trail
               .subTrail(action.getTrailTime(), action.getTrailLen(), 
                  tp -> tp.isInside(_uleft, _lright, 0.7, 0.7) );     
          
            if (!action.hideTrail() && !h.isEmpty()) {
                JsTrail res = new JsTrail(s.getTrailColor()); 
                h.forEach( it -> {
                    LatLng pos = (LatLng) it.getPosition();
                    res.linestring.add(new JsTPoint(it));
                 });
                return res;
            }
            else return null;
        }
       
       
       
        /**
         * Display a message path between nodes. 
         */
        protected void addLines(JsOverlay ov, Station s, LatLng uleft, LatLng lright)
        {
            LatLng ity = s.getPosition();
            Set<String> from = s.getTrafficTo();
            if (from == null || from.isEmpty()) 
                return;
           
            for (String f: from)
            {
                Station p = (Station) _db.getItem(f, null);
                if (p==null || !p.isInside(uleft, lright) || p.expired())
                    continue;
                
                LatLng itx = p.getPosition();
                RouteInfo.Edge e = _db.getRoutes().getEdge(s.getIdent(), p.getIdent());
                
                if (itx != null) { 
                    JsLine line = new JsLine(
                       f+"."+s.getIdent(),
                       new double[] {roundDeg(itx.getLng()), roundDeg(itx.getLat())}, 
                       new double[] {roundDeg(ity.getLng()), roundDeg(ity.getLat())}, 
                       (e.primary ? "prim" : "sec") 
                    );
                    ov.lines.add(line);
                }
            }
        }
    
    } /* Class client */
    
    
    
   /* Factory method. */
   @Override public WsNotifier.Client newClient(WsContext ctx) 
      { return new Client(ctx); }
    
    
   public JsonMapUpdater(AprsServerConfig conf) { 
      super(conf); 
      _db = conf.getDB();
   }
}
