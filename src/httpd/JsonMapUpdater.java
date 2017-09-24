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
import com.owlike.genson.*; 
import no.polaric.aprsd.filter.*;


/**
 * Map overlay updater using Websockets.  
 */

@WebSocket(maxIdleTime=360000)
public class JsonMapUpdater extends MapUpdater implements Notifier
{
   class JsOverlay {
      public String  view;       /* filter name */
      /* FIXME: Consider adding som session info here */
      
      List<JsPoint>  points;
      List<String> delete;
      
      public JsOverlay(String v) {
         view = v;
      }
   }

   
   class JsPoint {
       public String   ident;
       public double[] pos;
       public String   title;
       public boolean  redraw;  // true if isChanging()
       public boolean  own;     // May use a tag instead? 
       public String   icon;   
       public JsLabel  label;      
       public JsTrail  trail;
   }

   
   class JsLabel {
       public String id;        // May be different from point id because of aliases? 
       public String style;     // This is actually a CSS class
       public boolean hidden;
   }
   
   
   /* Do we need a more generic linestring as well? Line? */
    
   class JsTrail {
       public String[] style;
         // FIXME: Style or colours. Do colour selection on client? 
        
       public List<JsTPoint> linestring;  
       public JsTrail(String[] s) 
         { style=s; linestring = new LinkedList<JsTPoint>(); }
   }

   
   class JsTPoint {
       public double[] pos;
       public Date time;
       public JsTPoint(TPoint p)
         { pos = new double[] { ((LatLng)p.getPosition()).getLongitude(), ((LatLng)p.getPosition()).getLatitude() };
           time = p.getTS();
         }
    }
    
    
   
   
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
           _updates++;
           JsOverlay mu = new JsOverlay(_filter);
           if (!metaonly)
              addPoints(mu);
           return mapper.serialize(mu);
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
             RuleSet vfilt = ViewFilter.getFilter(_filter, _login);
             for (TrackerPoint s: _api.getDB().search(_uleft, _lright)) 
             {          
                /* Apply filter. */
                Action action = vfilt.apply(s, _scale); 
                if ( s.getPosition() == null ||
                     ( s.getSource().isRestricted() && !action.isPublic() && !_login) ||
                     action.hideAll())
                   continue;
                
                /* Add point to delete-list */
                if (!s.visible() || (_api.getSar() != null && !_login && _api.getSar().filter(s))) {
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
                }
             }
          }
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
           x.pos    = new double[] {ref.getLongitude(), ref.getLatitude()};
           x.title  = s.getDescr() == null ? "" : fixText(s.getDescr()); 
           x.redraw = s.isChanging();
           x.own    = (s instanceof AprsObject) 
                       && _api.getDB().getOwnObjects().hasObject(s.getIdent().replaceFirst("@.*",""));
           
           String icon = action.getIcon(s.getIcon()); 
           if (s.iconOverride() && _api.getSar()!=null) 
              icon = s.getIcon(); 
           x.icon = _wfiledir + "/icons/"+ (icon != null ? icon : _icon); 
           x.trail = createTrail(s, action);
           return x;
       }
       
       
       
       /** Create label or return null if label is to be hidden. */
       private JsLabel createLabel(TrackerPoint s, Action action) {
           boolean showSarInfo = _login || _api.getSar() == null || !_api.getSar().isAliasHidden();
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
               h.forEach( it -> res.linestring.add(new JsTPoint(it) ) );
               return res;
           }
           else return null;
       }
       
       
   } /* Class client */
    
    
    
   /* Factory method. */
   @Override public WsNotifier.Client newClient(Session ses) 
      { return new Client(ses); }
    
    
   public JsonMapUpdater(ServerAPI api, boolean trust) { 
      super(api, trust); 
   }
}
