
package no.polaric.aprsd;
import uk.me.jstott.jcoord.*;
import java.util.*;
import java.io.*;
import java.text.*;
import java.util.concurrent.locks.*; 
import java.lang.reflect.Constructor;
import java.nio.charset.Charset;
import no.polaric.aprsd.http.*;


public class Main implements ServerAPI
{
   public  static String version = "1.1-beta1";
   public static String toaddr  = "APPS11";
   
   private static StationDB db = null;
   public  static InetChannel ch1 = null;
   public  static TncChannel  ch2 = null;
   public static  Igate igate  = null;
   public static  OwnObjects ownobjects; 
   public static  OwnPosition ownpos = null; 
   public static  RemoteCtl rctl;
   public static  SarMode  sarmode = null;
   private static Properties _config = new Properties();
   private static HttpServer ws;
   private static Channel.Manager _chanManager = new Channel.Manager();
   private static SarUrl sarurl;
   
   /* Experimental !! 
    * Database interface must be known here. The interface is 
    * implemented by a plugin 
    */
   public static AprsHandler dblog = new AprsHandler.Dummy();
   
   /* API interface methods 
    * Should they be here or in a separate class ??
    */
   public StationDB getDB() 
    { return db; }
   
   public AprsHandler getAprsHandler() 
    { return dblog; }
    
   public void setAprsHandler(AprsHandler log) 
    { dblog = log; } 
       
   public Channel.Manager getChanManager()
    { return _chanManager; }
  
   public Igate getIgate()
    { return igate; }
    
   public MessageProcessor getMsgProcessor()
    { return db.getMsgProcessor(); /* Move from StationDB */ }
                    
   public OwnPosition getOwnPos()
    { return ownpos; }       
          
   public RemoteCtl getRemoteCtl()
    { return rctl; } 
   
   public void addHttpHandler(Object obj, String prefix)
    { ws.addHandler(obj, prefix); }
    
   public ServerAPI.ServerStats getHttps()
    { return ws; } 
    
   public Properties getConfig()
    { return _config; }
   
   public Map<String, Object> getObjectMap()
    { return PluginManager.getObjectMap(); }
   
   public String getVersion()
    { return version; }
    
   public String getToAddr()
    { return toaddr; }
 
   public SarUrl getSarUrl()
    { return sarurl; }
    
   public SarMode getSar()
    { return sarmode; }
    
   public void setSar(String reason, String src, String filt)
    { sarmode = new SarMode(reason, src, filt); }
 
   public void clearSar()
    { sarmode = null; }
    
    
    
   private ServerAPI api;
   
   public void init(String[] args) 
      // Here open configuration files, create a trace file, create ServerSockets, Threads
   {
        /* Get properties from configfile */
        if (args.length < 1)
           System.out.println("Usage: Daemon <config-file>");
             
        try {
           FileInputStream fin = new FileInputStream(args[0]);
           _config.load(fin);
           
           /* API */
           api = this; // new Main();
           PluginManager.setServerApi(api);
           
           /* Database of stations/objects */
           db  = new StationDBImp(api); 
           AprsPoint.setDB(db);
        }
        catch( Exception ioe )
        {
             System.err.println( "*** Couldn't init server:\n");
             ioe.printStackTrace(System.err);
        }
    }
    
    
    public void start()
      // Start the Thread, accept incoming connections   
    {
        try {

                   
           /* Start HTTP server */
           int http_port = Integer.parseInt(_config.getProperty("httpserver.port", "8081"));
           ws = new HttpServer(api, http_port, _config);
           ws.addHandler(new Webserver(api, _config), null);
           System.out.println( "*** HTTP server ready on port " + http_port);
             
           /* Plugins */
           PluginManager.addList(_config.getProperty("plugins", ""));
        
        
        
          /* Start parser and connect it to channel(s) if any */
           AprsParser p = new AprsParser(api, db.getMsgProcessor());

           /*
            * default channel setup: one named aprsis type APRSIS and one named tnc type TNC2
            */
           _chanManager.addClass("APRSIS", "no.polaric.aprsd.InetChannel");
           _chanManager.addClass("TNC2", "no.polaric.aprsd.Tnc2Channel");
           _chanManager.addClass("KISS", "no.polaric.aprsd.KissTncChannel");
           
           String[] channelns = {"aprsis", "tnc"};
           if (_config.getProperty("channel.aprsis.type", "").trim().equals(""))
              _config.setProperty("channel.aprsis.type", "APRSIS"); 
           if (_config.getProperty("channel.tnc.type", "").trim().equals(""))
              _config.setProperty("channel.aprsis.type", "TNC2");  
           
           String[] c = _config.getProperty("channels", "").trim().split(",(\\s*)");
           if (c.length > 0)
             channelns = c; 
           
           int i = 0;
           
           Channel[] ch = new Channel[channelns.length];
           for (String chan: channelns) {
                if (_config.getProperty("channel."+chan+".on", "true").trim().matches("true|yes"))  {
                    /* Define and activate channel */
                    String type = _config.getProperty("channel."+chan+".type", "").trim();
                    ch[i] = _chanManager.newInstance(api, type, chan);
                    if (ch[i] != null)
                       ch[i].addReceiver(p);
                    else
                       System.out.println("ERROR: Couldn't instantiate channel '"+chan+"' for type: "+type);
                    i++;
                }
           }

           
           if (_config.getProperty("remotectl.on", "false").trim().matches("true|yes")) {
               System.out.println("*** Activate Remote Control");
               rctl = new RemoteCtl(_config, db.getMsgProcessor(), api);
           }
            
           if (_config.getProperty("sarurl.on", "false").trim().matches("true|yes")) {
               System.out.println("*** Activate Sar URL");
               sarurl = new SarUrl(_config);
           }
  
           /* 
            * Default channels
            */
           String ch_inet_name = _config.getProperty("channel.default.inet", "aprsis").trim(); 
           String ch_rf_name = _config.getProperty("channel.default.rf", "tnc").trim();
           Channel ch1 = (ch_inet_name.length() > 0 ? _chanManager.get(ch_inet_name) : null);
           Channel ch2 = (ch_rf_name.length() > 0  ? _chanManager.get(ch_rf_name) : null);          

           /* Igate.
            * FIXME:  Should create igate object also if not on to allow it to be 
            * activated by a remote command. Note that if inetchannel or tncchannel does not exist, 
            * igate will not activate. Should those channels always be created????
            */     
           if (_config.getProperty("igate.on", "false").trim().matches("true|yes")) {
               System.out.println("*** Activate IGATE");
               igate = new Igate(api);
               igate.setChannels(ch2, ch1);
               igate.activate(this);
           }
           
          /* FIXME: There should be a way to set default inet and RF channels (in ChannelManager?)
           * For now, we use the channels given in igate.channels.
           */
          
          /* Message processing */
           db.getMsgProcessor().setChannels(ch2, ch1);  
          
          /* Own position */
          if (_config.getProperty("ownposition.gps.on", "false").trim().matches("true|yes")) {
               System.out.println("*** Activate GPS");
               ownpos = new GpsPosition(api);
           }
           else
               ownpos = new OwnPosition(api);

           ownpos.setChannels(ch2, ch1);
           db.addStation(ownpos); 
           
           /* APRS objects */
           ownobjects = db.getOwnObjects(); 
           ownobjects.setChannels(ch2, ch1); 

            
        }
        catch( Exception ioe )
        {
             System.err.println( "*** Couldn't start server:\n");
             ioe.printStackTrace(System.err);;
        }
        
   }
  

    
    public void stop()
      // Inform the Thread to terminate the run(), close the ServerSockets
    {
         System.out.println("*** Polaric APRSD shutdown"); 
         if (db  != null) db.save(); 
         if (ch1 != null) ch1.close();
         if (ch2 != null) ch2.close();
    }
    
    
    
    public void destroy()
      // Destroy any object created in init()
   {}
   
}
