package no.polaric.aprsd.http;
import no.polaric.aprsd.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.io.IOException;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import java.net.*;
import uk.me.jstott.jcoord.*; 
import org.eclipse.jetty.websocket.api.UpgradeRequest;



/**
 * Map overlay updater using Websockets.  
 */
@WebSocket(maxIdleTime=360000)
public class MapUpdater extends WsNotifier implements Notifier
{

   
   public class Client extends WsNotifier.Client 
   {
       private boolean   _subscribe; 
       private long      _lastSent = 0;
       private boolean   _pending = false; 
       
       protected LatLng  _uleft;     /* Area of interest: upper left */
       protected LatLng  _lright;    /* Area of interest: lower right */
       protected String  _filter;
       protected long    _scale = 0;
              
       public Client(Session conn) 
          { super(conn); }
       
       
       /**
        * Returns true if point is inside the client's area of interest 
        * and client wants to be updated, but not more often than once per 10 seconds. 
        */
       public boolean isInside(TrackerPoint st, boolean postpone ) {
          _pending = _pending || (_subscribe && st != null && st.isInside(_uleft, _lright));
          if (!postpone && _pending) {
             _pending = false; 
             return true; 
          }
          return false; 
       }
       
       
       /** Returns the overlay. XML format. */
       public String getOverlayData(boolean metaonly) {
           StringWriter outs = new StringWriter();
           PrintWriter out = new PrintWriter(outs, false);
           _updates++;
           
           String meta = 
               metaTag("login", getUsername()) +
               metaTag("loginuser", (_login ? "true" : "false")) + 
               metaTag("adminuser", (_admin ? "true" : "false")) +
               metaTag("updateuser", (_sar ? "true" : "false")) + 
               metaTag("sarmode", (_api.getSar() !=null ? "true" : "false")) +
               metaTag("clientses", ""+getUid());
               
           boolean showSarInfo = _login || _api.getSar() == null || !_api.getSar().isAliasHidden();
           try {     
              printOverlay( meta, out, 0 /* seq */, 
                   _filter, _scale, _uleft, _lright, 
                   _login, metaonly, showSarInfo );
              return outs.toString();
           }
           catch (IOException e) {
              _api.log().error("MapUpdater", "Error when writing XML: "+e);
              return "";
           }
       }

       
       public void subscribe() {}
       
       
       
       /** Receive text frame from client. */
       @Override public void onTextFrame(String text) 
       {
           _api.log().debug("MapUpdater", "Client "+_uid+": " + text);
           String[] parms = text.split(",");
           /* SUBSCRIBE filter,x1,x2,x3,x4,scale */
           if (parms[0].equals("SUBSCRIBE")) {
              if (parms.length > 6) {
                 _filter = parms[1];
                 Double x1 = Double.parseDouble( parms[2] );
                 Double x2 = Double.parseDouble( parms[3] );
                 Double x3 = Double.parseDouble( parms[4] );    
                 Double x4 = Double.parseDouble( parms[5] );
                 if (x1 > 180.0) x1 = 180.0; if (x1 < -180.0) x1 = -180.0;
                 if (x2 > 180.0) x2 = 180.0; if (x2 < -180.0) x2 = -180.0;
                 if (x3 > 90.0) x3 = 90.0; if (x3 < -90.0) x3 = -90.0;
                 if (x4 > 90.0) x4 = 90.0; if (x4 < -90.0) x4 = -90.0;
                 
                 _uleft  = new LatLng((double) x4, (double) x1); 
                 _lright = new LatLng((double) x2, (double) x3);
                 _scale  = Long.parseLong( parms[6] );
                 _subscribe = true;
                 subscribe(); 
                 
                 try { sendText(getOverlayData(false) ); }
                 catch (IOException e) 
                  {  _api.log().error("MapUpdater", "Couldn't sendtext to client "+_uid+": "+e); }
              }
              else
                 _api.log().warn("MapUpdater", "SUBSCRIBE command with too few parameters. uid="+_uid); 
           }
           else
              _api.log().warn("MapUpdater", "Unknown command from client. uid="+_uid);
       }   
   } /* Class client */
     
    
   private MapUpdater _link;  
   private Timer hb = new Timer();
   protected long    _updates = 0;
          
          
   public MapUpdater(ServerAPI api, boolean trust) { 
      super(api, trust); 
      
      /* Periodic task to send updates to clients */
      hb.schedule( new TimerTask() 
        { public void run() {       
              postText( x -> ((Client)x).getOverlayData(false), 
                        x -> ((Client)x).isInside(null, false) ); 
           } 
        } , 10000, 10000); 
   }  
    
    
   
   /** Factory method */
   @Override public WsNotifier.Client newClient(Session conn) 
     { return new Client(conn); }
   

   
   /** 
     * When opening the websocket, send client an overlay with only metainfo. 
     */
   @Override public boolean subscribe(InetSocketAddress uid, WsNotifier.Client client) 
   {
       try {
         client.sendText( ((Client)client).getOverlayData(true) );  
         
         UpgradeRequest req = client.getSession().getUpgradeRequest();  
         String origin = req.getHeader("X-Forwarded-For");
         _api.log().info("MapUpdater", "Client added: "+uid+
                  (origin != null ? ", "+origin : "") + 
                  (client._username != null ? ", username='"+client._username+"'" : ""));
         return true;
      }
      catch (IOException e) {
         _api.log().error("MapUpdater", "Couldn't send text to client "+uid+": "+e);
         return false;
      }
   }

   
   /** Signal of change from a tracker point. */
   public void signal(TrackerPoint st) {
      /* 
       * postText will not generate and send text here. 
       * This is postponed to a periodic task to avoid 
       * deadlock problems and to avoid sending too often. 
       */
      postText( "", x -> ((Client)x).isInside(st, true) );
      if (_link != null) 
         _link.signal(st);
   }
   

   /** Link to another instance of this class to receive signal. */
   public void link(MapUpdater n) {
      _link = n; 
   }
   
   
   public long nUpdates() {
     return _updates; 
   }
}
