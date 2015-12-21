
package no.polaric.aprsd.http;
import no.polaric.aprsd.*;
import org.simpleframework.http.core.Container;
import org.simpleframework.transport.SocketProcessor;
import org.simpleframework.http.core.ContainerSocketProcessor;
import org.simpleframework.transport.connect.Connection;
import org.simpleframework.transport.connect.SocketConnection;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;
import org.simpleframework.http.Query;
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
import java.util.concurrent.*;
import org.xnap.commons.i18n.*;



public class WebContainer implements Container, ServerAPI.ServerStats
{
  
   protected  ServerAPI  _api;
   protected  String     _serverurl;
   protected  boolean    _infraonly;
   protected  int        _max_load;
   protected int _requests = 0, _reqNo = 0;
   
   
   private static class _Handler {
      public Object obj; 
      public Method method;
      _Handler(Object o, Method m) {obj = o; method = m; }
   }
   
   private       Map<String, _Handler> _handlers = new HashMap<String, _Handler>();        
   private final Executor executor;

   public  static final String _encoding = "UTF-8";        

   
   
   private class Task implements Runnable {
       private final Response resp;
       private final Request req;
       
       public Task(Request rq, Response rs) {
          resp = rs;
          req = rq;
       }

       public void run() {
         int reqNo;
         synchronized (WebContainer.this) {
            _requests++; _reqNo++;
            reqNo = _reqNo;
         }
       
         try {
            String uri = req.getTarget().replaceAll("\\?.*", ""); 
            boolean allowed = 
                 req.getClientAddress().getAddress().isLoopbackAddress(); 
                
            resp.setValue("Server", "Polaric APRSD 1.8");
            resp.setValue("Content-Type", "text/html; charset=utf-8");
         
            _Handler h = _handlers.get(uri);
            if (h != null && allowed)  
                h.method.invoke(h.obj, req, resp);
            
            else {
               OutputStream os = resp.getOutputStream();
               PrintWriter out =  new PrintWriter(new OutputStreamWriter(os, _encoding));
               if (_requests > _max_load) {
                  resp.setCode(503); 
                  resp.setDescription("Service Unavailable");
                  System.out.println("*** Server overload or threads not finishing");
               }
               
               if (!allowed) {
                  out.println("<html><body>Access denied.</body></html>");
                  resp.setCode(403); 
                  resp.setDescription("Forbidden");
                  System.out.println("*** HTTP access denied. From: "+req.getClientAddress());
               }
               else {
                  out.println("<html><body>Unknown service: "+uri+"</body></html>");
                  resp.setCode(404); 
                  resp.setDescription("Not found");
               }
               out.close();
            }
         
            long time = System.currentTimeMillis();  
            resp.setDate("Date", time);
            resp.setDate("Last-Modified", time);
         }
         catch (Throwable e) {
             System.out.println("*** HTTP REQ exception: "+e);
             e.printStackTrace(System.out);
             if (e instanceof InvocationTargetException)
                e = e.getCause();
             try {
                OutputStream os = resp.getOutputStream();
                PrintWriter out =  new PrintWriter(new OutputStreamWriter(os, _encoding));
                out.println("<html><body>Exception: "+e+"</body></html>");
                resp.setCode(500); 
                resp.setDescription("Internal server error");
                out.close();
             } catch (Throwable ee) {};
         } 
         finally {
            synchronized(WebContainer.this) { _requests--; } }
      }
   } // Task
   
   
   
   
   public WebContainer(ServerAPI api) throws IOException
   {
      _api=api; 
      _infraonly   = api.getBoolProperty("map.infraonly", false);
      _serverurl   = api.getProperty("server.url", "/srv");
      _max_load    = api.getIntProperty("server.maxload", 200);

      /* Thread pool for high loads. 
       * This may not be needed when we move to websockets. 
       */
      executor = Executors.newCachedThreadPool();
   }
   
   
   
   /** 
    * Number of client requests in progress.
    */
   public int getClients() {return _requests; }
  
  
  
   /**
    * Total number of requests since startup.
    */
   public int getReq() { return _reqNo; }
   
   
   
   /**
    * Adds a HTTP handler. Go through methods. All public methods that starts with 'handle_' are 
    * considered handler-methods and are added to the handler-map. Key (URL target part) 
    * is derived from the method name after the 'handle_' prefix. Nothing else is assumed 
    * of the handler class.
    *
    * @param o : Handler object
    * @param prefix : Prefix of the url target part. If null, no prefix will be assumed. 
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
   


   /** 
    * Generic HTTP serve method. Dispatches to other methods based on uri
    * @param req Request object
    * @param resp Response object
    */
   public void handle (Request req, Response resp)
   {
       /* Use the fixed internal threadpool for low loads and start new threads
        * when load is over 8. 
        */
       Task task = new Task(req, resp);
       if (_requests > 8) 
           executor.execute(task);
       else
           task.run();
   }
   
}

