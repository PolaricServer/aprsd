package no.polaric.aprsd.http;
import no.polaric.aprsd.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.io.IOException;
import org.simpleframework.http.Cookie;
import org.simpleframework.http.Request;
import org.simpleframework.http.socket.*;
import org.simpleframework.http.socket.service.Service;
import uk.me.jstott.jcoord.*; 


/**
 * Map overlay updater using Websockets.  
 */
public class MapUpdater extends WsNotifier implements Notifier
{

   
   public class Client extends WsNotifier.Client 
   {
       private LatLng  _uleft;     /* Area of interest: upper left */
       private LatLng  _lright;    /* Area of interest: lower right */
       private boolean _subscribe; 
       private String  _filter;
       private long    _scale = 0;
       private long    _lastSent = 0;
       private boolean _pending = false; 
       
       public Client(FrameChannel ch, long uid) 
          { super(ch, uid); }
       
       
       /**
        * Returns true if point is inside the client's area of interest 
        * and client wants to be updated, but not more often than once per 10 seconds. 
        */
       public boolean isInside(TrackerPoint st) {
          _pending = _pending || (_subscribe && st != null && st.isInside(_uleft, _lright));
          if (_pending) {
             long t = System.currentTimeMillis();
             if (t > _lastSent + 1000 * 10) {
                 _lastSent = t;
                 _pending = false;
                 return true;
             }
          }
          return false; 
       }
       
       
       /** Returns the XML overlay. */
       public String getXmlOverlay(boolean metaonly) {
           StringWriter outs = new StringWriter();
           PrintWriter out = new PrintWriter(outs, false);
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

       
       /** Receive text frame from client. */
       @Override public void onTextFrame(Request req, String text) 
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
                 _uleft  = new LatLng((double) x4, (double) x1); 
                 _lright = new LatLng((double) x2, (double) x3);
                 _scale  = Long.parseLong( parms[6] );
                 _subscribe = true;
                 
                 try { sendText(getXmlOverlay(false) ); }
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
   
   public MapUpdater(ServerAPI api, boolean trust) throws IOException { 
      super(api, trust); 
      hb.schedule( new TimerTask() 
        { public void run() { signal(null); } } , 5000, 5000); 
   }  
    
    
   
   /** Factory method */
   @Override public WsNotifier.Client newClient(FrameChannel ch, long uid) 
     { return new Client(ch, uid); }
   

   /** 
     * When opening the websocket, send client an overlay with only metainfo. 
     */
   @Override public boolean subscribe(long uid, WsNotifier.Client client, Request req) 
   { 
       try {
         client.sendText( ((Client)client).getXmlOverlay(true) );
         
         String origin = req.getValue("X-Forwarded-For");
         _api.log().debug("MapUpdater", "Client added: "+uid+
                  (origin == null ? req.getClientAddress().getAddress() : ", "+origin) + 
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
      postText( x -> ((Client)x).getXmlOverlay(false), 
                x -> ((Client)x).isInside(st) );
      if (_link != null) 
         _link.signal(st);
   }

   /** Link to another instance of this class to receive signal. */
   public void link(MapUpdater n) {
      _link = n; 
   }
}
