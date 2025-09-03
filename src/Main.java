/* 
 * Copyright (C) 2025 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 */
 
 
package no.polaric.aprsd; 
import no.arctic.core.*;
import no.arctic.core.httpd.*;
import no.arctic.core.auth.*;
import no.polaric.aprsd.point.*;
import no.polaric.aprsd.filter.*;
import no.polaric.aprsd.channel.*;
import io.javalin.Javalin;
import java.util.*;
import java.io.*;




public class Main implements AprsServerAPI {

    public  static String version = "4.0~pre1";
    public  static String toaddr  = "APPS40";
    
    private static StationDB db = null;
    public  WebServer webserver;
    public  Logfile log;
    private List<ServerAPI.SimpleCb> _shutdown = new ArrayList<ServerAPI.SimpleCb>();
    public  static  AprsChannel ch1 = null;
    public  static  AprsChannel ch2 = null;
    private static  Properties _config, _defaultConf; 
    public  static  OwnObjects ownobjects;   
    public  static  OwnPosition ownpos = null; 
    public static   BullBoard bullboard = null;
    public  static  Igate igate  = null;
    private static  Channel.Manager _chanManager = new Channel.Manager();
    public  static  RemoteCtl rctl;
    public  static  MessageProcessor msgProc = null; 
    private static  String _xconf = System.getProperties().getProperty("datadir", ".")+"/"+"config.xml";
    private AprsServerAPI api;
    private static AprsParser parser = null;
    
    
        
    public String getVersion()
        { return version; }
    
    
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
    
    
    public Logfile log() 
        { return log; }

        
    public WebServer getWebserver()
        { return webserver; }
            
                
    public StationDB getDB() 
        { return db; }
        
        
    public void setDB(StationDB d) { 
       if (db != null && db instanceof StationDBImp)
          ((StationDBImp) db).kill(); 
       db=d; 
    }    
    
    
    public String getToAddr()
        { return toaddr; }
    
      
      
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
    
    
    public Channel.Manager getChanManager()
      { return _chanManager; }
    
    
    public Igate getIgate()
      { return igate; }
      
      
    public AprsParser getAprsParser()
    { return parser; }
    
    
    public OwnPosition getOwnPos()
      { return ownpos; } 
              
              
   public OwnObjects getOwnObjects()
      { return ownobjects; }
    
              
    public RemoteCtl getRemoteCtl()
      { return rctl; } 
    
        
    public MessageProcessor getMsgProcessor()
      { return msgProc; }
    
    
    public BullBoard getBullBoard() 
      { return bullboard; }
    
    
    /**
     * Add shutdown handler function. 
     */
    public void addShutdownHandler(SimpleCb cb){
        _shutdown.add(cb);
    }

        
           
    public Map<String, Object> properties()
        { return PluginManager.properties(); }
    
    
           
 
    
    
    
    
    public void init(String[] args) 
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
           
           if (files !=  null) 
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
        
        
        
    public void instantiate_channels() 
    {               
        /*
         * default channel setup: one named aprsis type APRSIS and one named tnc type TNC2
         */
        AprsChannel.init(api);
        _chanManager.addClass("APRSIS", "no.polaric.aprsd.channel.InetChannel");
        _chanManager.addClass("APRSIS-SRV", "no.polaric.aprsd.channel.InetSrvChannel");
        _chanManager.addClass("TNC2", "no.polaric.aprsd.channel.Tnc2Channel");
        _chanManager.addClass("KISS", "no.polaric.aprsd.channel.KissTncChannel");
        _chanManager.addClass("TCPKISS", "no.polaric.aprsd.channel.TcpKissChannel");
        _chanManager.addClass("ROUTER", "no.polaric.aprsd.channel.Router");

        String[] channelns = { };
        String[] c = getProperty("channels", "").split(",(\\s*)");
        if (c.length > 0)
            channelns = c; 
        List<Channel> chlist = new ArrayList<Channel>();
        
        /* Try to instantiate channels in channel list */
        for (String chan: channelns) {
            /* Define and activate channel */
            if (_chanManager.get(chan) != null)
                continue;
            String type = getProperty("channel."+chan+".type", "");
            Channel ch = _chanManager.newInstance(api, type, chan);
            
            if (ch != null) {
                chlist.add(ch);
                if (getBoolProperty("channel."+ch.getIdent()+".on", false))
                    ch.activate(api);
            }
            else
                log.error("Main", "ERROR: Couldn't instantiate channel '"+chan+"' for type: '"+type+"'");
        }
        
        for (Channel ch : chlist) {
            if (ch instanceof AprsChannel ach && !ach.isInRouter()) 
                ach.addReceiver(parser);
        }
    }
  
  
  

    public void start() {
        System.out.println("""
             ______      __          _  
            / __  /___  / /__ ______(_)___  
           / /_/ / __ \\/ / __` / __/ / __/ 
          / ____/ /_/ / / /_/ / / / / /__   
         / /    \\____/_/\\__,_/_/ /_/\\___/  
        /_/  S e r v e r  4    
        """);
        
        properties().put("API", this);
        msgProc = new MessageProcessor(this);
        parser = new AprsParser(api, msgProc);
        bullboard = new BullBoard(api, msgProc);
                        
        Signs.init(api);
        TrackerPoint.setApi(this);
        Station.init(api); 
            
        if (getBoolProperty("remotectl.on", false)) {
               log.info("Main", "Activate Remote Control");
               rctl = new RemoteCtl(api, msgProc);
            }
            
        /* Igate */
        igate = new Igate(api);
            
        db = new StationDBImp(this);
        ownobjects = db.getOwnObjects(); 
        
        webserver = new MyWebServer(this, 8081);
        webserver.start();    

        
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
        
                 
        /* Own position */
        boolean gpson = api.getBoolProperty("ownposition.gps.on", false);
        System.out.println("GPS ON = "+gpson);
        if (gpson) 
            ownpos = new GpsPosition(api);
        else
            ownpos = new OwnPosition(api);
        db.addItem(ownpos); 

                     
        /* Set channels on various services */
        msgProc.setChannels(ch2, ch1);  
        ownpos.setChannels(ch2, ch1);
        ownobjects.setChannels(ch2, ch1);  
        
        
    }
    
    
    public void stop() {
         for (ServerAPI.SimpleCb f: _shutdown)
            f.cb(); 
         msgProc.save();
    }

    
    public static void main(String[] args) 
    {
        Main setup = new Main(); 
        setup.init(args);
        setup.start();        
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            setup.stop();
        }));
    }
}

