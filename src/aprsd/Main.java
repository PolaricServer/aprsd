
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
   public  static String version = "1.1.1+";
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

   public void addHttpHandlerCls(String cn, String prefix)
    {
       try{
          Class cls = Class.forName(cn); 
          Constructor[] cc = cls.getConstructors();
          ws.addHandler(cc[0].newInstance(this), prefix);
       }
       catch (Exception e)
        { System.out.println("*** WARNING: cannot instantiate class: "+e); }
    }
    
   public ServerAPI.ServerStats getHttps()
    { return ws; } 
    
   public Properties getConfig()
    { return _config; }
          
   public String getProperty(String pname, String dvalue)
    { String x = _config.getProperty(pname, dvalue); 
      return (x == null ? x : x.trim()); }
   
   public boolean getBoolProperty(String pname, boolean dvalue)
    { return _config.getProperty(pname, (dvalue  ? "true" : "false"))
                 .trim().matches("TRUE|true|YES|yes"); } 
                 
   public int getIntProperty(String pname, int dvalue)
    {  return Integer.parseInt(_config.getProperty(pname, ""+dvalue).trim()); }
           
   public Map<String, Object> properties()
    { return PluginManager.properties(); }
   
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
             
        String confdir = System.getProperties().getProperty("confdir", ".");  
             
        try {
           FileInputStream fin = new FileInputStream(args[0]);
           _config.load(fin);
           fin.close(); 
           String plugins = getProperty("plugins", "");
           
           /* Scan subdirectory config.d for additional config files 
            * placed there by plugins. 
            */
           File pconfdir = new File(confdir+"/config.d");
           File[] files = pconfdir.listFiles( new FileFilter() {
                    public boolean accept(File x)
                       { return x.canRead() && x.getName().matches(".*\\.ini"); }
                 });
           
           for (File f : files) {
                System.out.println("*** Config file: "+f.getName());
                FileInputStream ffin = new FileInputStream(f.getAbsolutePath());
               _config.load( ffin );
                ffin.close();
                String newplug = getProperty("plugins","");
                if (newplug.length() > 0) {
                    if (plugins.length() > 0)
                       plugins += ", ";       
                    plugins += getProperty("plugins","");
                }
           }
           _config.setProperty("plugins", plugins);
           
           
           /* Allow config parameters to be overridden programmatically 
            * using the original config as defaults. 
            */
           _config = new Properties(_config); 
           
           /* API */
           api = this; // new Main();
           PluginManager.setServerApi(api);
        }
        catch( Exception ioe )
        {
             System.err.println( "*** Couldn't init server:\n");
             ioe.printStackTrace(System.err);
        }
    }
    
    
    public void start()
    {
        try {
           properties().put("API", this);
                   
           /* Start HTTP server */
           int http_port = getIntProperty("httpserver.port", 8081);
           ws = new HttpServer(api, http_port);
           System.out.println( "*** HTTP server ready on port " + http_port);
           
           /* Database of stations/objects */
           db  = new StationDBImp(api);   
           
           /* Plugins. Note that plugins are installed and started before main webservices, channels
            * aprs parser and own position/objects. If some core service is to be modified or extended
            * by plugins it must be installed before plugins. 
            */
           PluginManager.addList(getProperty("plugins", ""));
           
           AprsPoint.setApi(api);
           Station.init(api); 
           
           /* Add main webservices */
           ws.addHandler(new Webserver(api), null);
        
           /* Start parser and connect it to channel(s) if any */
           AprsParser p = new AprsParser(api, db.getMsgProcessor());

           /*
            * default channel setup: one named aprsis type APRSIS and one named tnc type TNC2
            */
           _chanManager.addClass("APRSIS", "no.polaric.aprsd.InetChannel");
           _chanManager.addClass("TNC2", "no.polaric.aprsd.Tnc2Channel");
           _chanManager.addClass("KISS", "no.polaric.aprsd.KissTncChannel");
           
           String[] channelns = {"aprsis", "tnc"};
           if (getProperty("channel.aprsis.type", "").equals(""))
              _config.setProperty("channel.aprsis.type", "APRSIS"); 
           if (getProperty("channel.tnc.type", "").equals(""))
              _config.setProperty("channel.aprsis.type", "TNC2");  
           
           String[] c = getProperty("channels", "").split(",(\\s*)");
           if (c.length > 0)
             channelns = c; 
           
           int i = 0;
           
           Channel[] ch = new Channel[channelns.length];
           for (String chan: channelns) {
                if (getBoolProperty("channel."+chan+".on", true))  {
                
                    /* Define and activate channel */
                    String type = getProperty("channel."+chan+".type", "");
                    ch[i] = _chanManager.newInstance(api, type, chan);
                    if (ch[i] != null)
                       ch[i].addReceiver(p);
                    else
                       System.out.println("*** ERROR: Couldn't instantiate channel '"+chan+"' for type: '"+type+"'");
                    i++;
                }
           }

           
           if (getBoolProperty("remotectl.on", false)) {
               System.out.println("*** Activate Remote Control");
               rctl = new RemoteCtl(api, db.getMsgProcessor());
           }
            
           if (getBoolProperty("sarurl.on", false)) {
               System.out.println("*** Activate Sar URL");
               sarurl = new SarUrl(api);
           }
  
           /* 
            * Default channels
            */
           String ch_inet_name = getProperty("channel.default.inet", "aprsis"); 
           String ch_rf_name = getProperty("channel.default.rf", "tnc");
           Channel ch1 = (ch_inet_name.length() > 0 ? _chanManager.get(ch_inet_name) : null);
           Channel ch2 = (ch_rf_name.length() > 0  ? _chanManager.get(ch_rf_name) : null);          

           /* Igate.
            * FIXME:  Should create igate object also if not on to allow it to be 
            * activated by a remote command. Note that if inetchannel or tncchannel does not exist, 
            * igate will not activate. Should those channels always be created????
            */     
           if (getBoolProperty("igate.on", false)) {
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
          if (getBoolProperty("ownposition.gps.on", false)) {
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
         PluginManager.deactivateAll();
         if (db  != null) db.save(); 
         if (ch1 != null) ch1.close();
         if (ch2 != null) ch2.close();
    }
    
    
    
    public void destroy()
      // Destroy any object created in init()
   {}
   
}
