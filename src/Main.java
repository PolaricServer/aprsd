
package no.polaric.aprsd;
import uk.me.jstott.jcoord.*;
import java.util.*;
import java.io.*;
import java.text.*;
import java.util.concurrent.locks.*; 
import java.lang.reflect.Constructor;
import java.nio.charset.Charset;


public class Main implements PluginManager.ServerAPI
{
   public static String version = "1.0+dev";
   private static StationDB db = null;
   public static InetChannel ch1 = null;
   public static TncChannel  ch2 = null;
   public static Igate igate  = null;
   public static OwnObjects ownobjects; 
   public static RemoteCtl rctl;
   private static Properties _config = new Properties();
   

   /* Experimental !! 
    * Database interface must be known here. The interface is 
    * implemented by a plugin 
    */
   public static Database dblog; 
   
   /* API interface methods 
    * Should they be here or in a separate class ??
    */
   public StationDB getDB() 
    { return db; }
   public Set<String> getChannels(Channel.Type type)
    { return null; /* TBD */ }
   public Channel getChannel(String id)
    { return null; /* TBD */ }
   public void addChannel(Channel.Type type, String id, Channel ch)
    { }
   public MessageProcessor getMessageProcessor()
    { return db.getMsgProcessor(); /* Move from StationDB */ }
   public Properties getConfig()
    { return _config; }
   public Map<String, Object> getObjectMap()
    { return PluginManager.getObjectMap(); }
 
 
 
   public static void main( String[] args )
   {
        /* Get properties from configfile */
        if (args.length < 1)
           System.out.println("Usage: Daemon <config-file>");
          
        Runtime.getRuntime().addShutdownHook( new Thread() 
             {
                public void run() 
                   { 
                     System.out.println("*** Polaric APRSD shutdown"); 
                     if (db  != null) db.save(); 
                     if (ch1 != null) ch1.close();
                     if (ch2 != null) ch2.close();
                   } 
             });
             
        try {
           FileInputStream fin = new FileInputStream(args[0]);
           _config.load(fin);
           System.out.println( "*** Polaric APRSD startup ++" );
           
           
           /* Database of stations/objects */
           db  = new StationDBImp(_config); 
           AprsPoint.setDB(db);

           /* Start parser and connect it to channel(s) if any */
           AprsParser p = new AprsParser(db, db.getMsgProcessor());
           if (_config.getProperty("igate.on", "false").trim().matches("true|yes")) {
               System.out.println("*** Activate IGATE");
               igate = new Igate(_config);
           }
           if (_config.getProperty("inetchannel.on", "false").trim().matches("true|yes"))  {
               System.out.println("*** Activate Internet Channel");
               ch1 = new InetChannel(_config);
               ch1.setReceivers((Channel.Receiver) p, igate); 
               Thread t = new Thread(ch1, "InetChannel");
               t.start(); 
           }
           if (_config.getProperty("tncchannel.on", "false").trim().matches("true|yes")) {
               System.out.println("*** Activate TNC Channel");
               ch2 = new TncChannel(_config);
               ch2.setReceivers((Channel.Receiver) p, igate);
               Thread t = new Thread(ch2, "TncChannel");
               t.start(); 
           } 
           if (_config.getProperty("remotectl.on", "false").trim().matches("true|yes")) {
               System.out.println("*** Activate Remote Control");
               rctl = new RemoteCtl(_config, db.getMsgProcessor(), db);
           }
 
           
           /* Message processing */
           db.getMsgProcessor().setChannels(ch2, ch1);  

           /* Igate */  
           if (igate != null)
               igate.setChannels(ch2, ch1);

           /* APRS objects */
           ownobjects = db.getOwnObjects(); 
           ownobjects.setChannels(ch2, ch1); 
           
           /* Start HTTP server */
           int http_port = Integer.parseInt(_config.getProperty("httpserver.port", "8081"));

           Class cls = Class.forName("no.polaric.aprsd.http.Webserver");
           Constructor con = cls.getConstructors()[0];
           Object ws = con.newInstance(db, http_port, _config);
           
           /* API and Plugins */
           PluginManager.setServerApi(new Main());
           PluginManager.addList(_config.getProperty("plugins", ""));
           
           /* Experimental: This is provided by the DatabasePlugin */
           dblog = (Database) PluginManager.getObjectMap().get("databaselog");
           
           
           System.out.println( "*** HTTP server ready on port " + http_port);
           
           
        }
        catch( Exception ioe )
        {
             System.err.println( "*** Couldn't start server:\n");
             ioe.printStackTrace(System.err);
             System.exit( -1 );
        }
        
   }
}
