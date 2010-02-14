
package aprs;
import uk.me.jstott.jcoord.*;
import java.util.*;
import java.io.*;
import java.text.*;
import java.util.concurrent.locks.*; 
import java.lang.reflect.Constructor;
import java.nio.charset.Charset;


public class Main
{
   private static InetChannel ch1 = null;
   private static TncChannel  ch2 = null;
   private static StationDB db = null;
   static OwnObjects ownobjects;
   static MessageProcessor msg;
   static RemoteCtl rctl;


 
   public static void main( String[] args )
   {
        /* Get properties from configfile */
        if (args.length < 1)
           System.out.println("Usage: Daemon <config-file>");
           
        Properties config = new Properties();
       
   
        Runtime.getRuntime().addShutdownHook( new Thread() 
             {
                public void run() 
                   { System.out.println("*** LA3T APRS Server shutdown"); 
                     if (db  != null) db.save(); 
                     if (ch2 != null) ch2.close(); 
                   } 
             });
             
        try {
           FileInputStream fin = new FileInputStream(args[0]);
           config.load(fin);
           System.out.println( "*** LA3T APRS Server startup" );
           
           
           /* Database of stations/objects */
           db  = new StationDBImp(config); 
           AprsPoint.setDB(db);
           msg = new MessageProcessor(config);

           /* Start parser and connect it to channel(s) if any */
           AprsParser p = new AprsParser(db, msg);
           Igate igate  = null;
           if (config.getProperty("igate.on", "false").trim().matches("true|yes")) {
               System.out.println("*** Activate IGATE");
               igate = new Igate(config);
           }
           if (config.getProperty("inetchannel.on", "false").trim().matches("true|yes"))  {
               System.out.println("*** Activate Internet Channel");
               ch1 = new InetChannel(config);
               ch1.setReceivers((Channel.Receiver) p, igate); 
               Thread t = new Thread(ch1);
               t.start(); 
           }
           if (config.getProperty("tncchannel.on", "false").trim().matches("true|yes")) {
               System.out.println("*** Activate TNC Channel");
               ch2 = new TncChannel(config);
               ch2.setReceivers((Channel.Receiver) p, igate);
               Thread t = new Thread(ch2);
               t.start(); 
           } 
           if (config.getProperty("remotectl.on", "false").trim().matches("true|yes")) {
               System.out.println("*** Activate Remote Control");
               rctl = new RemoteCtl(config, msg, db);
           }

           
           /* Message processing */
           msg.setChannels(ch2, ch1);  

           /* Igate */  
           if (igate != null)
               igate.setChannels(ch2, ch1);

           /* APRS objects */
           ownobjects = new OwnObjects(config, db);
           ownobjects.setChannels(ch2, ch1);
           
           /* Start HTTP server */
           int http_port = Integer.parseInt(config.getProperty("httpserver.port", "8081"));

           Class cls = Class.forName("aprs.Webserver");
           Constructor<HttpServer> con = cls.getConstructors()[0];
           HttpServer ws = con.newInstance(db, http_port, config);
           
           System.out.println( "*** HTTP server ready on port " + http_port);
        }
        catch( Exception ioe )
        {
             System.err.println( "*** Couldn't start HTTP server:\n");
             ioe.printStackTrace(System.err);
             System.exit( -1 );
        }
        
   }
}
