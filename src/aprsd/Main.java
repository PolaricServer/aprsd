/* 
 * Copyright (C) 2016 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
 
 
package no.polaric.aprsd;
import uk.me.jstott.jcoord.*;
import java.util.*;
import java.io.*;
import java.text.*;
import java.util.concurrent.locks.*; 
import java.lang.reflect.Constructor;
import java.nio.charset.Charset;
import no.polaric.aprsd.http.*;
import no.polaric.aprsd.filter.*;
import java.util.concurrent.*;



public class Main implements ServerAPI
{
   public  static String version = "2.1+";
   public static String toaddr  = "APPS21";
   
   private static StationDB db = null;
   private static AprsParser parser = null;
   public  static AprsChannel ch1 = null;
   public  static AprsChannel ch2 = null;
   public static  Igate igate  = null;
   public static  OwnObjects ownobjects; 
   public static  OwnPosition ownpos = null; 
   public static  BullBoard bullboard = null;
   public static  RemoteCtl rctl;
   public static  SarMode  sarmode = null;
   private static Properties _config, _defaultConf; 
   private static WebServer ws; 
   private static Channel.Manager _chanManager = new Channel.Manager();
   private static SarUrl sarurl;
   private static String _xconf = System.getProperties().getProperty("datadir", ".")+"/"+"config.xml";
   private static StatLogger stats; 
   public  static Logfile log;
   public  static DeadlockDetector deadlockDetector;
    
    
   /* API interface methods 
    * Should they be here or in a separate class ??
    * Should they be static or should the API interface be a static variable? 
    */
   public static Logfile getLog() 
    { return log; }
    
   public WebServer getWebserver()
    { return ws; }
    
   public Logfile log() 
    { return log; }
    
   public StationDB getDB() 
    { return db; }
   
   public AprsParser getAprsParser()
    { return parser; }
       
   public Channel.Manager getChanManager()
    { return _chanManager; }
  
   public Igate getIgate()
    { return igate; }
    
   public MessageProcessor getMsgProcessor()
    { return db.getMsgProcessor(); /* Move from StationDB */ }
                    
   public BullBoard getBullBoard() 
    { return bullboard; }
                    
   public OwnPosition getOwnPos()
    { return ownpos; }       
          
   public RemoteCtl getRemoteCtl()
    { return rctl; } 
    
   
   public void setInetChannel(AprsChannel ch) {
       ch1 = ch; 
       if (igate != null) 
           igate.setChannels(ch2, ch1);
       db.getMsgProcessor().setChannels(ch2, ch1);
       ownpos.setChannels(ch2, ch1);
       ownobjects.setChannels(ch2, ch1); 
   }
   
   public AprsChannel getInetChannel()
      { return ch1; }
      
      
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
        { log.warn("Main","cannot instantiate class: "+e); }
    } 
    
    
    
    
   public Properties getConfig()
    { return _config; }
    
    
   public void saveConfig() 
   { 
       try {
            _defaultConf.clear();
            FileOutputStream cfout = new FileOutputStream(_xconf);
            _config.storeToXML(cfout, "Configuration for Polaric APRSD");
       }
       catch (java.io.IOException e) {log.warn("Main", "Cannot write file "+e);}
   }
          
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
    
   public void setSar(String reason, String src, String filt, boolean hideAlias)
    { sarmode = new SarMode(reason, src, filt, hideAlias); }
 
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
           _defaultConf = new Properties();
           _defaultConf.load(fin);
           fin.close(); 
           String plugins = _defaultConf.getProperty("plugins", "");
           String channels = _defaultConf.getProperty("channels", "");
           
           /* Scan subdirectory config.d for additional config files 
            * placed there by plugins. 
            */
           File pconfdir = new File(confdir+"/config.d");
           File[] files = pconfdir.listFiles( new FileFilter() {
                    public boolean accept(File x)
                       { return x.canRead() && x.getName().matches(".*\\.ini"); }
                 });
           
           if (files == null)
                System.out.println("OOPS. files is null");
           for (File f : files) {
                Properties xConf = new Properties(); 
                FileInputStream ffin = new FileInputStream(f.getAbsolutePath());
                xConf.load( ffin );
                ffin.close();
                
                String newplug = xConf.getProperty("plugins","");
                if (newplug.length() > 0) {
                    if (plugins.length() > 0)
                       plugins += ", ";       
                    plugins += newplug; 
                }
                
                String newchan = xConf.getProperty("channels", "");
                if (newchan.length() > 0) {
                    if (channels.length() > 0)
                       channels += ", ";       
                    channels += newchan; 
                }
                
                for (String key: xConf.stringPropertyNames())
                   if (!key.equals("plugins") && !key.equals("channels"))
                      _defaultConf.setProperty(key, xConf.getProperty(key));
           }
           _defaultConf.setProperty("plugins", plugins);
           System.out.println("plugins = "+plugins);
          _defaultConf.setProperty("channels", channels);
           System.out.println("channels = "+channels);
           
           
           /* 
            * Allow default config properties to be overridden
            * programmatically and saved elsewhere. The original config file
            * now functions as default values! Note that there is a special plugin
            * that is responsible for updating override-config and saves the 
            * file when it terminates. If such a plugin is not installed, this file 
            * is not used. 
            */
           _config = new Properties(_defaultConf); 
           try { 
               FileInputStream cfin = new FileInputStream(_xconf); 
               _config.loadFromXML(cfin);
           }
           catch (java.io.FileNotFoundException e) {}
           
           
           /* API */
           api = this; // new Main();
           PluginManager.setServerApi(api);
           log = new Logfile(this);
           
           ViewFilter.init(api);
        }
        catch( Exception ioe )
        {
             System.out.println("ERROR: Couldn't init server.");
             ioe.printStackTrace(System.err);
        }
    }
    
    
    public void start()
    {
        try {
           properties().put("API", this);
                      
           /* Database of stations/objects */
           db  = new StationDBImp(api);   
           
           /* Start parser and connect it to channel(s) if any */
           parser = new AprsParser(api, getMsgProcessor());
           
           bullboard = new BullBoard(api, getMsgProcessor());
                     
           /* Configure and Start HTTP server */
           int http_port = getIntProperty("httpserver.port", 8081);
           ws = new WebServer(api, http_port);
           ws.start();
           TrackerPoint.setNotifier(ws.getNotifier());
           log.info("Main", "HTTP/WS server ready on port " + http_port);

           /* Deadlock detection */
           deadlockDetector = new DeadlockDetector(new DeadlockConsoleHandler(), 120, TimeUnit.SECONDS);
           deadlockDetector.start();
           
           /* Initialize signs early since plugins may use it (see below) */
           Signs.init(api);
                      
           /* Plugins. Note that plugins are installed and started before main webservices, channels
            * aprs parser and own position/objects. If some core service is to be modified or extended
            * by plugins it must be installed before plugins. 
            *
            * FIXME: Do this with 'plugins.preinit' before db is created and the rest of the plugins later
            *  OR add an init function to the plugin that can be called early, OR find another way to solve
            *  this problem!!!
            */
           PluginManager.addList(getProperty("plugins", ""));
           
           TrackerPoint.setApi(api);
           Station.init(api); 
           
           /* Add main webservices */
           ws.addHandler(new Webservices(api), null);
           ws.addHandler(new ConfigService(api), null); 
           
           /*
            * default channel setup: one named aprsis type APRSIS and one named tnc type TNC2
            */
            AprsChannel.init(api);
           _chanManager.addClass("APRSIS", "no.polaric.aprsd.InetChannel");
           _chanManager.addClass("TNC2", "no.polaric.aprsd.Tnc2Channel");
           _chanManager.addClass("KISS", "no.polaric.aprsd.KissTncChannel");
           _chanManager.addClass("TCPKISS", "no.polaric.aprsd.TcpKissChannel");
           
           /* FIXME: IS THIS RIGHT???? */
           String[] channelns = {"aprsis", "tnc"};
           if (getProperty("channel.aprsis.type", "").equals(""))
              _config.setProperty("channel.aprsis.type", "APRSIS"); 
           if (getProperty("channel.tnc.type", "").equals(""))
              _config.setProperty("channel.tnc.type", "TNC2");  
           
           String[] c = getProperty("channels", "").split(",(\\s*)");
           if (c.length > 0)
             channelns = c; 
           
           
           /* Try to instantiate channels in channel list */
           for (String chan: channelns) {
                 /* Define and activate channel */
                 String type = getProperty("channel."+chan+".type", "");
                 Channel ch = _chanManager.newInstance(api, type, chan);
                 if (ch != null) {
                    /* FIXME: add parser in AprsChannel constructor?? */
                    if (ch instanceof AprsChannel) 
                        ((AprsChannel)ch).addReceiver(parser);
                 
                    if (getBoolProperty("channel."+chan+".on", false))
                        ch.activate(api);
                 }
                 else
                    log.error("Main", "ERROR: Couldn't instantiate channel '"+chan+"' for type: '"+type+"'");
           }

           
           if (getBoolProperty("remotectl.on", false)) {
               log.info("Main", "Activate Remote Control");
               rctl = new RemoteCtl(api, db.getMsgProcessor());
           }
            
           if (getBoolProperty("sarurl.on", false)) {
               log.info("Main", "Activate Sar URL");
               sarurl = new SarUrl(api);
           }
  
           /* 
            * Default channels
            */
           String ch_inet_name = getProperty("channel.default.inet", "aprsis"); 
           String ch_rf_name = getProperty("channel.default.rf", "tnc");
           ch1 = (ch_inet_name.length() > 0 ? (AprsChannel) _chanManager.get(ch_inet_name) : null);
           ch2 = (ch_rf_name.length() > 0  ? (AprsChannel) _chanManager.get(ch_rf_name) : null);          

           /* Igate.
            * FIXME:  Should create igate object also if not on to allow it to be 
            * activated by a remote command. Note that if inetchannel or tncchannel does not exist, 
            * igate will not activate. Should those channels always be created????
            */     
           if (getBoolProperty("igate.on", false)) {
               log.info("Main", "Activate IGATE");
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
               log.info("Main", "Activate GPS");
               ownpos = new GpsPosition(api);
           }
           else
               ownpos = new OwnPosition(api);

           ownpos.setChannels(ch2, ch1);
           db.addPoint(ownpos); 
           
           /* APRS objects */
           ownobjects = db.getOwnObjects(); 
           ownobjects.setChannels(ch2, ch1); 

           
           /* Server statistics */
           if (getBoolProperty("serverstats.on", false)) {
               log.info("Main", "Activate server statistics");
               stats = new StatLogger(api, "serverstats", "serverstats.log");
           }
            
        }
        catch( Exception ioe )
        {
             log.error("Main", "Couldn't start server:\n");
             ioe.printStackTrace(System.err);;
        }
        
    }
  

    
    public void stop()
      /* Stop the webserver, deactivate plugins, close things down... */
    {
         log.info("Main", "Stopping HTTP/WS server");
         try {
            ws.stop();
         } catch (Exception e) {}

         log.info("Main", "Polaric APRSD shutdown"); 
         PluginManager.deactivateAll();
         if (db  != null) db.save(); 
         if (ch1 != null) ch1.close();
         if (ch2 != null) ch2.close();
    }
    
    
    
    public void destroy()
      // Destroy any object created in init()
       {}
       
       
    public static void main(String[] args)
    { 
        var main = new Main();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            main.stop();
        }));
        main.init(args);
        main.start(); 
    }
    
   
}
