
package no.polaric.aprsd.http;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.*;

import org.simpleframework.http.core.Container;
import org.simpleframework.http.core.ContainerSocketProcessor;
import org.simpleframework.transport.*;
import no.polaric.aprsd.*;
import org.simpleframework.transport.connect.Connection;
import org.simpleframework.transport.connect.SocketConnection;
import org.simpleframework.http.socket.service.*; 




public class WebServer implements ServerAPI.Web  {
   private final Connection _conn;
   private final SocketAddress _addr;  
   private final SocketProcessor _server;
   private final GeoMessages _messages;
   private final MapUpdater  _mapupdate, _smapupdate;
   private final Container   _wc;
   
   /** 
    * Configure a webserver. 
    * @Param 
    * @Param port server port to listen on
    * @Param wc   Container with HTTP services. 
    */
   public WebServer(ServerAPI api, int port, Container wc) throws IOException {
  
      /* Set up a map from paths to service implementations. */
      Map<String, Service> services = new HashMap<String, Service>(); 

      _messages = new GeoMessages(api, true);
      services.put("/messages_sec", _messages);
      
      _mapupdate = new MapUpdater(api, false);
      services.put("/mapdata", _mapupdate);
      
      _smapupdate = new MapUpdater(api, true);
      services.put("/mapdata_sec", _smapupdate);
      _smapupdate.link(_mapupdate);
      
      /* Create a router that uses the paths of the URLs to select the service. */
      Router wsrouter = new PathRouter (services, null); 
      
      _wc = wc;
      Container c = new RouterContainer(wc, wsrouter, 6);
      _server = new ContainerSocketProcessor(c, 24);
      _conn = new SocketConnection(_server);
      _addr = new InetSocketAddress(port);
   }

   public int nClients() {
       return _mapupdate.nClients() + _smapupdate.nClients();
   }
   
   public int nLoggedin() {
       return _smapupdate.nClients();
   }
   
   public int  nHttpReq() {
      return ((WebContainer) _wc).getReq();
   }
   
   public ServerAPI.Mbox getMbox() {
       return _messages;
   }
   
   public Notifier getNotifier() {
       return _smapupdate; 
   }
   
   public void start() throws IOException {
       _conn.connect(_addr);
   }

   public void stop() throws IOException {
       _conn.close();
   }
}
