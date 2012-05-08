
package no.polaric.aprsd.http;
import no.polaric.aprsd.*;

import org.simpleframework.http.core.Container;
import org.simpleframework.transport.connect.Connection;
import org.simpleframework.transport.connect.SocketConnection;
import org.simpleframework.http.*;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.io.PrintStream;
import java.lang.reflect.*;

import uk.me.jstott.jcoord.*;
import java.util.*;
import java.io.*;
import java.text.*;
import com.mindprod.base64.Base64;
import java.util.concurrent.locks.*; 


public class HttpServer implements Container
{
   
   protected  ServerAPI  _api;
   protected  String     _serverurl;
   protected  boolean    _infraonly;
   
   private static class _Handler {
     public Object obj; 
     public Method method;
     _Handler(Object o, Method m) {obj = o; method = m; }
   }
   
   private    Map<String, _Handler> _handlers = new HashMap<String, _Handler>();        
   
   public static final String _encoding = "UTF-8";        
   
   
   
   
   public HttpServer(ServerAPI api, int port, Properties config) throws IOException
   {
      _api=api; 
      _infraonly   = config.getProperty("map.infraonly", "false").trim().matches("true|yes");
      _serverurl   = config.getProperty("server.url", "/srv").trim();
      
      /* Configure this web container */
      Connection connection = new SocketConnection(this);
      SocketAddress address = new InetSocketAddress(port);
      connection.connect(address);
   }
   
  
   
   /**
    * Adds a HTTP handler. Go through methods. All public methods that starts with 'handle_' are 
    * considered handler-methods and are added to the handler-map. Key (URL target part) 
    * is derived from the method name after the 'handle_' prefix. Nothing else is assumed 
    * of the handler class.
    *
    * @param o: Handler object
    * @param prefix: Prefix of the url target part. If null, no prefix will be assumed. 
    */
   public void addHandler(Object o, String prefix)
   { 
      for (Method m : o.getClass().getMethods())
         /* FIXME: Should consider using annotation to identify what methods are handlers. */
         if (m.getName().matches("handle_.+")) {
            /* FIXME: Should check if method is public, type of parameters and return value */
            String key = m.getName().replaceFirst("handle_", "");
            if (_handlers.containsKey(key))
                System.out.println("*** WARNING: add HTTP handler. Key '"+key+"' already exists, overriding");
            if (prefix != null && prefix.charAt(0) != '/')
                prefix = "/" + prefix;
            key = (prefix==null ? "" : prefix) + "/" + key;
            System.out.println("*** Add HTTP handler method: "+key+" --> "+m.getName());
            _handlers.put(key, new _Handler(o, m));
         }
   }
   
   
   
   protected int _requests = 0, _reqNo = 0;

   /** 
    * Generic HTTP serve method. Dispatches to other methods based on uri
    */
   public void handle (Request req, Response resp)
   {
       int reqNo;
       synchronized (this) {
         _requests++; _reqNo++;
         reqNo = _reqNo;
       }
       
       try {
         String uri = req.getTarget().replaceAll("\\?.*", ""); 
         long time = System.currentTimeMillis();
         resp.set("Server", "Polaric Server 1.1");
         resp.setDate("Date", time);
         resp.setDate("Last-Modified", time); 
         resp.set("Content-Type", "text/html; charset=utf-8");
         
         _Handler h = _handlers.get(uri);
         if (h != null) 
            h.method.invoke(h.obj, req, resp);
         else {
            OutputStream os = resp.getOutputStream();
            PrintWriter out =  new PrintWriter(new OutputStreamWriter(os, _encoding));
            out.println("<html><body>Unknown service: "+uri+"</body></html>");
            resp.setCode(404); 
            resp.setText("Not found");
            out.close();
         }
       }
       catch (Throwable e) 
          { System.out.println("*** HTTP REQ exception: "+e.getMessage());
            e.printStackTrace(System.out); } 
       finally {
          synchronized(this) { _requests--; } }
   }
   
}

