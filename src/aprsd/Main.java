/* 
 * Copyright (C) 2019-2023 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import java.util.*;
import java.io.*;
import java.text.*;
import java.util.concurrent.locks.*; 
import java.lang.reflect.Constructor;
import java.nio.charset.Charset;
import no.polaric.aprsd.http.*;
import no.polaric.aprsd.filter.*;
import java.util.concurrent.*;
import java.util.logging.*;



public class Main implements ServerAPI
{
   public  static String version = "3.1+";
   public  static String toaddr  = "APPS31";
   
   private static StationDB db = null;
   private static AprsParser parser = null;
   public  static AprsChannel ch1 = null;
   public  static AprsChannel ch2 = null;
   public static  Igate igate  = null;
   public static  MessageProcessor msgProc = null; 
   public static  OwnObjects ownobjects; 
   public static  OwnPosition ownpos = null; 
   public static  BullBoard bullboard = null;
   public static  RemoteCtl rctl;
   private static Properties _config, _defaultConf; 
   private static WebServer ws; 
   private static Channel.Manager _chanManager = new Channel.Manager();
   private static String _xconf = System.getProperties().getProperty("datadir", ".")+"/"+"config.xml";
   private static StatLogger stats; 
   public  static Logfile log;
   private static List<ServerAPI.SimpleCb> _shutdown = new ArrayList<ServerAPI.SimpleCb>();
    
    
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
       
   public void setDB(StationDB d) { 
       if (db != null && db instanceof StationDBImp)
          ((StationDBImp) db).kill(); 
       db=d; 
    }
       
       
   public AprsParser getAprsParser()
    { return parser; }
       
   public Channel.Manager getChanManager()
    { return _chanManager; }
  
   public Igate getIgate()
    { return igate; }
    
   public MessageProcessor getMsgProcessor()
    { return msgProc; }
                    
   public BullBoard getBullBoard() 
    { return bullboard; }
                    
   public OwnPosition getOwnPos()
    { return ownpos; } 
    
   public OwnObjects getOwnObjects()
    { return ownobjects; }
          
   public RemoteCtl getRemoteCtl()
    { return rctl; } 
    
   
   public void setInetChannel(AprsChannel ch) {
       ch1 = ch; 
       if (igate != null) 
           igate.setInetChan(ch1);
       msgProc.setInetChan(ch1);
       ownpos.setInetChan(ch1);
       ownobjects.setInetChan(ch1); 
   }
   
   public AprsChannel getInetChannel()
      { return ch1; }
    
    
   public void setRfChannel(AprsChannel ch) {
       ch2 = ch; 
       if (igate != null) 
           igate.setRfChan(ch2);
       msgProc.setRfChan(ch2);
       ownpos.setRfChan(ch2);
       ownobjects.setRfChan(ch2); 
   }
   
   public AprsChannel getRfChannel()
      { return ch2; }
      
      
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
    
    
   public void addShutdownHandler(ServerAPI.SimpleCb h) {
        _shutdown.add(h);
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
    
    
   public double[] getPosProperty(String pname)
    {
        String inp = _config.getProperty(pname, "0,0").trim(); 
        if (!inp.matches("[0-9]+(\\.([0-9]+))?\\,([0-9]+)(\\.([0-9]+))?")) {
            log.warn("Main", "Error in parsing position: "+inp);
            return new double[] {0,0};
        }
        String[] scoord = inp.split(",");
        double[] res = new double[] {0,0};
        if (scoord.length != 2)
            return res;
        res[0] = Double.parseDouble(scoord[0].trim());
        res[1] = Double.parseDouble(scoord[1].trim());
        return res;
    }
    
    
           
   public Map<String, Object> properties()
    { return PluginManager.properties(); }
   
   public String getVersion()
    { return version; }
    
   public String getToAddr()
    { return toaddr; }
    
    
   private ServerAPI api;
   
   public void init(String[] args) 
      // Here open configuration files, create a trace file, create ServerSockets, Threads
   {    
        /* Get properties from configfile */
        if (args.length < 1)
           System.out.println("Usage: Daemon <config-file>");
             
        String confdir = System.getProperties().getProperty("confdir", ".");  
        
        System.out.println();
        System.out.println();
        System.out.println("*************************************************");
        System.out.println("***   Polaric APRSD server startup            ***");
        System.out.println("***   See http://aprs.no/polaricserver        ***");
        System.out.println("*************************************************");
        System.out.println();
           
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
                System.out.println("*** OOPS. files is null");
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
           System.out.println("*** Plugins = "+plugins);
          _defaultConf.setProperty("channels", channels);
           System.out.println("*** Channels = "+channels);
           
           
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
           AuthInfo.addService("basic");
        }
        catch( Exception ioe )
        {
             System.out.println("*** ERROR: Couldn't init server.");
             ioe.printStackTrace(System.err);
        }
    }
    
    
    public void simpleInit() {
        _config = new Properties(); 
        log = new Logfile(this);
    }
    
    
    public void start()
    {
        try {
            properties().put("API", this);
            msgProc = new MessageProcessor(this);
            parser = new AprsParser(api, msgProc);
            bullboard = new BullBoard(api, msgProc);
                     
           
            /* Initialize signs early since plugins may use it (see below) */
            Signs.init(api);
            TrackerPoint.setApi(api);
            Station.init(api); 

            if (getBoolProperty("remotectl.on", false)) {
               log.info("Main", "Activate Remote Control");
               rctl = new RemoteCtl(api, msgProc);
            }
  
            /* Igate */
            igate = new Igate(api);

            
            /* Configure and Start HTTP server */
            int http_port = getIntProperty("httpserver.port", 8081);
            ws = new WebServer(api, http_port);

            db = new StationDBImp(api);
            
            /* APRS objects */
            ownobjects = db.getOwnObjects(); 
            
            /* 
             * Plug-ins. The webservice parts are started after the webserver is instantiated since they may use it or
             * start websockets 
             */           
            PluginManager.addList(getProperty("plugins", ""));
            ws.start();
  

            /* Start webservices (REST API) of plugins after Webserver is started */
            PluginManager.startWebservices();
            
            /* Add main webservices */
            TrackerPoint.setNotifier(ws.getNotifier());
            log.info("Main", "HTTP/WS server ready on port " + http_port);
           
           
            /* 
             * Channel setup. This should be done after plugins are installed since plugins may
             * add channel types. 
             */
            instantiate_channels(); 
           
            /* 
             * Default primary channels
             */
            String ch_inet_name = getProperty("channel.default.inet", null); 
            String ch_rf_name = getProperty("channel.default.rf", null);
            ch1 = (ch_inet_name != null && ch_inet_name.length() > 0 
                ? (AprsChannel) _chanManager.get(ch_inet_name) : null);
            ch2 = (ch_rf_name != null && ch_rf_name.length() > 0  
                ? (AprsChannel) _chanManager.get(ch_rf_name) : null);          
            
            if (ch2 != null && !ch2.isRf()) {
                log.warn("Main", "Channel " + ch_rf_name + " isn't a proper APRS RF channel - disabling");
                ch2 = null;
            }
            /* 
             * Igate 
             */     
            if (getBoolProperty("igate.on", false)) {
               log.info("Main", "Activate IGATE");
               igate.setChannels(ch2, ch1);
               igate.activate(this);
            }
         
         
            /* Own position */
            System.out.println("GPS ON = "+getBoolProperty("ownposition.gps.on", false));
            ownpos = new GpsPosition(api);
            db.addItem(ownpos); 

                     
           /* Set channels on various services */
            msgProc.setChannels(ch2, ch1);  
            ownpos.setChannels(ch2, ch1);
            ownobjects.setChannels(ch2, ch1);  
           
           
            /* Server statistics */
            if (getBoolProperty("serverstats.on", false)) {
               log.info("Main", "Activate server statistics");
               stats = new StatLogger(api, "serverstats", "serverstats.log");
            }
        }
        catch( Exception ioe )
        {
             log.error("Main", "Error when starting server:\n");
             ioe.printStackTrace(System.err);;
        }
        
    }
    
    
    
    public void instantiate_channels() 
    {               
        /*
         * default channel setup: one named aprsis type APRSIS and one named tnc type TNC2
         */
        AprsChannel.init(api);
        _chanManager.addClass("APRSIS", "no.polaric.aprsd.InetChannel");
        _chanManager.addClass("TNC2", "no.polaric.aprsd.Tnc2Channel");
        _chanManager.addClass("KISS", "no.polaric.aprsd.KissTncChannel");
        _chanManager.addClass("TCPKISS", "no.polaric.aprsd.TcpKissChannel");

        String[] channelns = { };
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
                if (ch instanceof AprsChannel ach) 
                    ach.addReceiver(parser);
                 
                if (getBoolProperty("channel."+chan+".on", false))
                    ch.activate(api);
            }
            else
                log.error("Main", "ERROR: Couldn't instantiate channel '"+chan+"' for type: '"+type+"'");
        }
    }
  

    
    public void stop()
      /* Stop the webserver, deactivate plugins, close things down... */
    {
         for (ServerAPI.SimpleCb f: _shutdown)
            f.cb(); 
            
         System.out.println("*** Stopping HTTP/WS server");
         try {
            Thread.sleep(2000);
            if (ws != null) ws.stop();
         } catch (Exception e) {
            e.printStackTrace(System.out);
         }

         MailBox.shutdown(); 
         
         System.out.println("*** Polaric APRSD shutdown"); 
         PluginManager.deactivateAll();
         if (db  != null) db.shutdown(); 
         if (ch1 != null) ch1.deActivate();
         if (ch2 != null) ch2.deActivate();
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
