
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
       
      /* Create a router that uses the paths of the URLs to select the service. */
      Router wsrouter = new PathRouter (services, null); 
      
      Container c = new RouterContainer(wc, wsrouter, 2);
      _server = new ContainerSocketProcessor(c, 12);
      _conn = new SocketConnection(_server);
      _addr = new InetSocketAddress(port);
   }

   public ServerAPI.Mbox getMbox() {
       return _messages;
   }
   
   public void start() throws IOException {
       _conn.connect(_addr);
   }

   public void stop() throws IOException {
       _conn.close();
   }
}
