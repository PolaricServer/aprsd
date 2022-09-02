
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



public interface JsonPoints 
{

    class JsOverlay {
        public String  view;       /* filter name */
        /* FIXME: Consider adding some session info here */
      
        public AuthInfo       authorization;
        public boolean        sarmode; 
        public List<JsPoint>  points;
        public List<String>   delete;
        public List<JsLine>   lines;
        public List<JsTPoint> pcloud;
      
        public JsOverlay(String v) {
            view = v;
        }
    }

   
    class JsPoint {
        public String   ident;
        public double[] pos;
        public String   title;
        public boolean  redraw;    // true if isChanging()
        public boolean  own;       // May use a tag instead? 
        public boolean  aprs;      // May use a tag instead?
        public boolean  telemetry; // May use a tag instead? 
        public boolean  sarAuth=false;
        public String   icon;  
        public String   href;
        public JsLabel  label;      
        public JsTrail  trail;
    }

   
    /* FIXME: Consider merge with JsPoint */
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
        public String path;
        
        public JsTPoint(double[] p, Date t) { 
            pos = p; 
            time = t;
        }
        public JsTPoint(TPoint p) {
            LatLng ref = (LatLng) p.getPosition();
            pos = new double[] {ref.getLongitude(), ref.getLatitude()}; 
            time = p.getTS();
            path = ServerBase.cleanPath(p.getPath());
        }
    }
    
    class JsLine {
        public String ident;
        public double[] from, to;
        public String type;
        public JsLine(String id, double[] f, double[] t, String typ)
          { ident=id; from=f; to=t; type=typ; }
    }
    
}
