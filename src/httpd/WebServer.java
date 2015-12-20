
package no.polaric.aprsd.http;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

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
   
   
   public WebServer(ServerAPI api, int port, Container wc) throws IOException {
    
     /* Web socket service via router. Add service later.. */
      Router wsrouter = new DirectRouter(null); 
      
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
