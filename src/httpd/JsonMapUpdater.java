/* 
 * Copyright (C) 2017 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import uk.me.jstott.jcoord.*; 
import no.polaric.aprsd.filter.*;


/**
 * Map overlay updater using Websockets.  
 */

@WebSocket(maxIdleTime=360000)
public class JsonMapUpdater extends MapUpdater implements Notifier, JsonPoints
{
 
    public class Client extends MapUpdater.Client 
    {
        private Set<String> items = new HashSet<String>();
       
        public Client(Session conn) 
            {  super(conn); }
   
          
        public void subscribe() {
            // It is possible to remove only items that move out of the viewport? */
            items.clear();
        }
          
   
        /** Returns the overlay. JSON format. */
        @Override public String getOverlayData(boolean metaonly) {
            try {
                _updates++;
                JsOverlay mu = new JsOverlay(_filter);
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
                for (TrackerPoint s: _api.getDB().search(_uleft, _lright)) 
                {          
                    /* Apply filter. */
                    Action action = vfilt.apply(s, _scale); 
                    if ( s.getPosition() == null ||
                         ( s.getSource().isRestricted() && !action.isPublic() && !allowed) ||
                         action.hideAll())
                        continue;
                
                    /* Add point to delete-list */
                    if (!s.visible() || (_api.getSar() != null && !allowed && _api.getSar().filter(s))) {
                        if (items.contains(s.getIdent())) {
                            items.remove(s.getIdent());
                            mu.delete.add(s.getIdent());
                        }
                    }
                    else {  
                        /* Add point to list */
                        if (s.isChanging() || !items.contains(s.getIdent())) {
                            items.add(s.getIdent());
                            JsPoint p = createPoint(s, action);
                            if (p!=null) 
                                mu.points.add(p);
                        }
                        /* Add lines (that connect points) to list */
                        if (action.showPath() && s instanceof Station && ((AprsPoint)s).isInfra())
                           addLines(mu, (Station) s, _uleft, _lright); 
                    }
                }
                /* Add signs to list */
                for (Signs.Item s: Signs.search(_scale, _uleft, _lright)) {
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
            LatLng ref = s.getPosition().toLatLng(); 
            if (ref == null)
                return null;
            JsPoint x = new JsPoint(); 
            
            x.ident = s.getIdent();
            x.pos   = new double[] {ref.getLongitude(), ref.getLatitude()};
            x.title = s.getDescr() == null ? "" : fixText(s.getDescr());
            x.href  = s.getUrl() == null ? "" : s.getUrl();
            x.icon  = "/icons/"+ s.getIcon();
            return x;
        }
        
        
       
        /** Convert Tracker point to JSON point. 
         * Return null if point has no position.  
         */
        private JsPoint createPoint(TrackerPoint s, Action action) {
            LatLng ref = s.getPosition().toLatLng(); 
            if (ref == null) 
                return null;
             
            JsPoint x  = new JsPoint();
            x.ident  = s.getIdent();
            x.label  = createLabel(s, action);
            x.pos    = new double[] {roundDeg(ref.getLongitude()), roundDeg(ref.getLatitude())};
            x.title  = s.getDescr() == null ? "" : fixText(s.getDescr()); 
            x.redraw = s.isChanging();
            x.own    = (s instanceof AprsObject) 
                       && _api.getDB().getOwnObjects().hasObject(s.getIdent().replaceFirst("@.*",""));
            x.aprs   = (s instanceof AprsPoint); 
           
            String icon = action.getIcon(s.getIcon()); 
            if (s.iconOverride() && _api.getSar()!=null) 
               icon = s.getIcon(); 
            x.icon = "/icons/"+ (icon != null ? icon : _icon); 
            x.trail = createTrail(s, action);
            return x;
        }
       
       
       
        /** Create label or return null if label is to be hidden. */
        private JsLabel createLabel(TrackerPoint s, Action action) {
            boolean showSarInfo = login() || _api.getSar() == null || !_api.getSar().isAliasHidden();
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
                    res.linestring.add( 
                        new JsTPoint( 
                          new double[]{roundDeg(pos.getLongitude()), roundDeg(pos.getLatitude())}, it.getTS()
                        )
                    );
                 });
                return res;
            }
            else return null;
        }
       
       
        /**
         * Display a message path between nodes. 
         */
        protected void addLines(JsOverlay ov, Station s, Reference uleft, Reference lright)
        {
            LatLng ity = s.getPosition().toLatLng();
            Set<String> from = s.getTrafficTo();
            if (from == null || from.isEmpty()) 
                return;
           
            for (String f: from)
            {
                Station p = (Station)_api.getDB().getItem(f, null);
                if (p==null || !p.isInside(uleft, lright) || p.expired())
                    continue;
                
                LatLng itx = p.getPosition().toLatLng();
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
