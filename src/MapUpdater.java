/* 
 * Copyright (C) 2017-2025 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import no.polaric.core.*;
import no.polaric.core.httpd.*;
import no.polaric.aprsd.point.*;
import io.javalin.*;
import io.javalin.websocket.*; 
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.io.IOException;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import java.net.*;
import org.eclipse.jetty.websocket.api.UpgradeRequest;



/**
 * Map overlay updater using Websockets.  
 */
public abstract class MapUpdater extends WsNotifier
{

   
   public abstract class Client extends WsNotifier.Client 
   {
       private boolean   _subscribe; 
       private long      _lastSent = 0;
       private boolean   _pending = false; 
       private String    _baseLayer = "none";
       
       protected LatLng  _uleft;     /* Area of interest: upper left */
       protected LatLng  _lright;    /* Area of interest: lower right */
       protected String  _filter;
       protected String  _tag;
       protected boolean _keep = false;
       protected long    _scale = 0;
              
              
       public Client(WsContext ctx) 
          { super(ctx); }
       
       
       /**
        * Returns true if point is inside the client's area of interest 
        * and client wants to be updated, but not more often than once per 5 seconds. 
        */
       public boolean isInside(TrackerPoint st, boolean postpone ) {
          _pending = _pending || (_subscribe && st != null && st.isInside(_uleft, _lright));
          if (!postpone && _pending) {
             _pending = false; 
             return true; 
          }
          return false; 
       }
       
       
       /** Returns the overlay. */
       public abstract String getOverlayData(boolean metaonly);

       
       public void subscribe() {}
      
       
       
       /** Receive text frame from client. */
       @Override synchronized public void handleTextFrame(String text) 
       {
           String[] parms = text.split(",");
           /* SUBSCRIBE filter,x1,x2,x3,x4,scale,tag
            * tag is optional
            */
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
                 _keep = false;
                 
                 if (parms.length > 7) {
                    _keep = "true".equals(parms[7]);
                    if (!parms[7].matches("true|false"))     
                        _tag = parms[7].trim();
                    else if (parms.length > 8)
                        _tag = parms[8].trim();
                 }
                 _subscribe = true;
                 subscribe(); 
                 
                 send(getOverlayData(false) ); 
               
              }
              else
                 _conf.log().warn("MapUpdater", "SUBSCRIBE command with too few parameters. uid="+sesId(_ctx)); 
           }
           else if (parms[0].equals("BASELAYER")) {
                  _baseLayer = parms[1];
           }
           else if (!parms[0].equals("****"))
              _conf.log().warn("MapUpdater", "Unknown command from client. uid="+sesId(_ctx));
       }   
   } 
     
   
   
   
//   private LStatLogger _stats;
   private MapUpdater _link;  
   private Timer hb = new Timer();
   protected long    _updates = 0;
   
          
          
   public MapUpdater(ServerConfig conf) { 
        super(conf); 
      
        /* Periodic task to send updates to clients */
        hb.schedule( new TimerTask() 
            { public void run() {       
                postText( x -> ((Client)x).getOverlayData(false), 
                          x -> ((Client)x).isInside(null, false) ); 
            } 
        } , 10000, 5000); 
        
        /* Periodic task to count usage of map layers */
        hb.schedule( new TimerTask() { 
            public void run() { 
         //      if (_stats != null) {
         //         for (String x : _clients.keySet())
         //            _stats.count( ((Client)_clients.get(x))._baseLayer); 
         //      }
            } 
        } , 30000, 60000); 
   }  
    
          
/*          
    public void setStatLogger(LStatLogger l) {
       _stats = l;
    }
*/

    /**
     * Websocket close handler.
     * FIXME can this be removed?
    @OnWebSocketClose
    public void onClose(Session conn, int statusCode, String reason) {
       String user = _getUid(conn);
       _conf.log().info("MapUpdater", "Connection closed"+(reason==null ? "" : ": "+reason)+". Unsubscribing user: "+user);
       closeSes(conn);
    }
     */
   
   /** 
     * When opening the websocket, send client an overlay with only metainfo. 
     */
   @Override protected boolean subscribe(WsContext ctx, WsNotifier.Client client) 
   {
      client.send( ((Client)client).getOverlayData(true) );  
         
      /* Update the time of last access for the logged in user. 
       * FIXME: Move this to PubSub class ? ? 
       */
      WebServer ws = (WebServer) _conf.getWebserver(); 
      if (client.userName() != null)
         ws.userDb().get(client.userName()).updateTime();
         
      _conf.log().info("MapUpdater", "Client added: "+sesId(ctx)+
            (client.userName() == null ? "" : ", " + client.userName()));
      return true;
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
