/* 
 * Copyright (C) 2017-2023 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import no.polaric.aprsd.*;

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

@WebSocket(maxIdleTime=600000)
public class JsonMapUpdater extends MapUpdater implements Notifier, JsonPoints
{
    int _max_ovr_size = 20000;
    
    
    public class Client extends MapUpdater.Client 
    {
        private Set<String> items = new HashSet<String>(1000);
       
        public Client(Session conn) 
            {  super(conn); }
   
          
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
                    _api.log().error("JsonMapUpdater", "Cannot allocate JsOverlay object.");
                    return null;
                }
                mu.authorization = _auth;
                mu.sarmode = (_api.getSar() != null);
                if (!metaonly)
                    addPoints(mu);
                return serializeJson(mu);
            }
            catch (Exception e) {
                _api.log().error("JsonMapUpdater", "Exception in generating overlay.");
                e.printStackTrace(System.out);
                return null;
            }
        }
       
       
       
        /** Add trackerpoints to overlay */
        private void addPoints(JsOverlay mu) 
        {
            /* Output APRS objects */
            if (_api.getDB() == null)
                _api.log().error("JsonMapUpdater", "_api.getDB() returns null");
            else 
            {
                mu.points = new LinkedList<JsPoint>();
                mu.delete = new LinkedList<String>();
                mu.lines = new LinkedList<JsLine>();
                boolean allowed = (login() && trusted()); 
                RuleSet vfilt = ViewFilter.getFilter(_filter, allowed);      
                List<TrackerPoint> itemlist =  _api.getDB().search(_uleft, _lright, vfilt);
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
                         ( s.getSource() != null && s.getSource().isRestricted() && !action.isPublic() && !allowed) ||
                           action.hideAll() ||
                           (_tag != null && !s.hasTag(_tag))
                        )
                      continue; 

                    /* Add point to delete-list */
                    if (!s.visible() || (_api.getSar() != null && !allowed && _api.getSar().filter(s))) {
                        if (!_keep) 
                            continue;
                        mu.delete.add(s.getIdent());
                    }
                    else {  
                        /* Add item to overlay */
                        JsPoint p = createPoint(s, action);
                        if (p!=null) 
                            mu.points.add(p);
                                
                        /* Add lines (that connect points) to list */
                        if (action.showPath() && s instanceof Station && ((AprsPoint)s).isInfra())
                           addLines(mu, (Station) s, _uleft, _lright); 
                    }
                }
                
                /* Add signs to list */
                for (Signs.Item s: Signs.search(getUsername(), _scale, _uleft, _lright)) {
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
            return x;
        }
        
        

       
        /** Convert Tracker point to JSON point. 
         * Return null if point has no position.  
         */
        private JsPoint createPoint(TrackerPoint s, Action action) {
            LatLng ref = s.getPosition(); 
            if (ref == null) 
                return null;
             
            JsPoint x  = new JsPoint();
            
            /* Indicate if user has authorization to change point. */
            var ai = getAuthInfo();
            x.sarAuth = (ai != null && getAuthInfo().itemSarAuth(s)); 
            
            x.ident   = s.getIdent();
            x.label   = createLabel(s, action);
            x.pos     = new double[] {roundDeg(ref.getLng()), roundDeg(ref.getLat())};
            x.title   = s.getDescr() == null ? "" : fixText(s.getDescr()); 
            x.redraw  = s.isChanging();
            x.own     = (s instanceof AprsObject) 
                        && _api.getDB().getOwnObjects().hasObject(s.getIdent().replaceFirst("@.*",""));
            x.aprs    = (s instanceof AprsPoint); 
            x.telemetry = s.hasTag("APRS.telemetry");
           
            String icon = action.getIcon(s.getIcon()); 
            if (s.iconOverride() && _api.getSar()!=null)  
               icon = s.getIcon(); 
            x.icon = "/icons/"+ (icon != null ? icon : _icon); 
            x.trail = createTrail(s, action);
            return x;
        }
       
       
       
        /** Create label or return null if label is to be hidden. */
        private JsLabel createLabel(TrackerPoint s, Action action) {
            boolean showSarInfo = login() || _api.getSar() == null || 
                !_api.getSar().isAliasHidden() || !action.hideAlias();
            
            JsLabel lbl = new JsLabel();
           
            lbl.style = (!(s.getTrail().isEmpty()) ? "lmoving" : "lstill");
            if (s instanceof AprsObject)
                lbl.style = "lobject"; 
            lbl.style += " "+ action.getStyle();
            lbl.id = s.getDisplayId(showSarInfo);
            lbl.hidden = (action.hideIdent() || s.isLabelHidden() );
            return lbl;
        }
       
       
       
        private JsTrail createTrail(TrackerPoint s, Action action) {
            Seq<TPoint> h = s.getTrail()
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
                Station p = (Station)_api.getDB().getItem(f, null);
                if (p==null || !p.isInside(uleft, lright) || p.expired())
                    continue;
                
                LatLng itx = p.getPosition();
                RouteInfo.Edge e = _api.getDB().getRoutes().getEdge(s.getIdent(), p.getIdent());
                
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
   @Override public WsNotifier.Client newClient(Session ses) 
      { return new Client(ses); }
    
    
   public JsonMapUpdater(ServerAPI api, boolean trust) { 
      super(api, trust); 
   }
}
