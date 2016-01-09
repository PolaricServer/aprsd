
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




public class WebServer {
   private final Connection _conn;
   private final SocketAddress _addr;  
   private final SocketProcessor _server;
   
   /** 
    * Configure a webserver. 
    * @Param 
    * @Param port server port to listen on
    * @Param wc   Container with HTTP services. 
    */
   public WebServer(ServerAPI api, int port, Container wc) throws IOException {
  
      /* Set up a map from paths to service implementations. */
      Map<String, Service> services = new HashMap<String, Service>(); 
      services.put("/chatroom", new ChatRoom(api));
      services.put("/messages", new GeoMessages(api));
      
      /* Create a router that uses the paths of the URLs to select the service. */
      Router wsrouter = new PathRouter (services, null); 
      
      Container c = new RouterContainer(wc, wsrouter, 2);
      _server = new ContainerSocketProcessor(c, 12);
      _conn = new SocketConnection(_server);
      _addr = new InetSocketAddress(port);
   }

   
   public void start() throws IOException {
       _conn.connect(_addr);
   }

   public void stop() throws IOException {
       _conn.close();
   }
}
