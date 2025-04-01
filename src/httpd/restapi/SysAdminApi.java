/* 
 * Copyright (C) 2018-2025 by Øyvind Hanssen (ohanssen@acm.org)
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
 

package no.polaric.aprsd.http;
import spark.Request;
import spark.Response;
import spark.route.Routes;
import static spark.Spark.get;
import static spark.Spark.put;
import static spark.Spark.*;
import java.util.*; 
import java.io.*;
import java.util.stream.*;
import no.polaric.aprsd.*;



/**
 * Implement REST API for system-admin.  
 */
public class SysAdminApi extends ServerBase {
    
    /* Register subtypes for deserialization */
    static { 
        ServerBase.addSubtype(InetChannel.JsConfig.class, "APRSIS");
        ServerBase.addSubtype(InetSrvChannel.JsConfig.class, "APRSIS-SRV");
        ServerBase.addSubtype(KissTncChannel.JsConfig.class, "KISS");
        ServerBase.addSubtype(TcpKissChannel.JsConfig.class, "TCPKISS");
        ServerBase.addSubtype(Tnc2Channel.JsConfig.class, "TNC2");
        ServerBase.addSubtype(Router.JsConfig.class, "ROUTER");
    }
    
    
    private ServerAPI _api; 
    
    public SysAdminApi(ServerAPI api) {
        super(api);
        _api = api;
    }
    
    
    
    
    public static class SysInfo 
    {
        public Date runsince; 
        public String version;
        public int items;
        public int ownobj;
        public int clients, loggedin;
        public long usedmem;
        public List<String> plugins;
        public List<ChannelInfo> channels;
        public String remotectl;
    }
    
    
    public static record ChannelInfo 
       ( String name, 
         boolean active, boolean rfchan, boolean inetchan, boolean isrf, boolean isaprs, 
         GenChanInfo generic, Channel.JsConfig specific)
    {}
    
    
    public static record GenChanInfo 
        (Channel.State state, String tag, boolean restricted, boolean inRouter)
    { 
        public GenChanInfo(ServerAPI api, Channel ch) {
            this (
                ch.getState(),
                api.getProperty("channel."+ch.getIdent()+".tag", ""),
                api.getBoolProperty("channel."+ch.getIdent()+".restricted", false),
                (ch instanceof AprsChannel ach ? ach.isInRouter() : false)
            );
        }
    }


    
    
    /*
     * (re-) configure a channel f rom a channelinfo object. 
     */
    public  void setChannelProps(Channel ch, ChannelInfo cnf, String type) {
        var props = _api.getConfig();
        props.setProperty("channel."+ch.getIdent()+".on", ""+cnf.active);
        props.setProperty("channel."+ch.getIdent()+".tag", cnf.generic.tag);
        props.setProperty("channel."+ch.getIdent()+".restricted", ""+cnf.generic.restricted);
        if (type != null)
            props.setProperty("channel."+ch.getIdent()+".type", type);
        
        try {
        if (ch instanceof AprsChannel ach) {
            if (ach.isRf()) {
                if (_api.getRfChannel() == ach && !cnf.rfchan) {
                    _api.setRfChannel(null);
                    props.remove("channel.default.rf");
                }
                else if (cnf.rfchan) {
                    _api.setRfChannel(ach);
                    props.setProperty("channel.default.rf", ach.getIdent());
                }
            }
            else {
                if (_api.getInetChannel() == ach && !cnf.inetchan) {
                    _api.setInetChannel(null);
                    props.remove("channel.default.inet");
                }
                else if (cnf.inetchan) {
                    _api.setInetChannel(ach);
                    props.setProperty("channel.default.inet", ach.getIdent());
                }
            }
        }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
        
        ch.setJsConfig((Channel.JsConfig) cnf.specific);
        if (cnf.active) {
            ch.deActivate(); 
            ch.activate(_api);
        }
        else if (ch.isActive())
            ch.deActivate();
    }
    
    
    
    public void removeChannelConfig(Channel ch) {
        Properties cnf = _api.getConfig();
        for (Object key: cnf.keySet())
            if (((String)key).startsWith("channel."+ch.getIdent()))
                cnf.remove((String) key);
    }
    
    
    /* 
     * Re-create the channel list which is a comma-separated 
     * text-string
     */
    public void updateChanList() {
        var mgr = _api.getChanManager(); 
        var i = 0;
        String chlist = ""; 
        
        for (String chn : mgr.getKeys()) {
            if (i>0)
                chlist+=",";
            chlist += chn; 
            i++;
        }
        Properties cnf = _api.getConfig();
        cnf.setProperty("channels", chlist);
    }
    
    
    
    
    public static record ClientInfo
       (Date created, String cid, long in, long out, String userid, boolean mobile)
    {}
    
    
    
    public static record ServerConfig 
    (
        String mycall, 
        boolean igate, boolean rfigate, boolean objigate, 
        int radius, 
        String path_igate,
        String path_messages, 
        String path_objects, 
        String always_rf, 
        boolean remotectl,
        int remote_radius,
        String rc_server,
        String authkey
    ) 
    {
        public ServerConfig(ServerAPI api) {
            this (
                api.getProperty("default.mycall", "NOCALL"),
                api.getBoolProperty("igate.on", false),
                api.getBoolProperty("igate.rfgate.allow", false),
                api.getBoolProperty("objects.rfgate.allow", false),
                api.getIntProperty("objects.rfgate.range", 10),
                api.getProperty("igate.rfgate.path", ""),
                api.getProperty("message.rfpath", ""),
                api.getProperty("objects.rfpath", ""),
                api.getProperty("message.alwaysRf", ""),
                api.getBoolProperty("remotectl.on", false),
                api.getIntProperty("remotectl.radius", 10),
                api.getProperty("remotectl.connect", ""),
                api.getProperty("message.auth.key", "")
            );
        }
        
        public void save(ServerAPI api) {
            Properties  prop = api.getConfig();
            prop.setProperty("default.mycall", mycall);
            prop.setProperty("igate.on", ""+igate);
            prop.setProperty("igate.rfgate.allow", ""+rfigate);
            prop.setProperty("objects.rfgate.allow", ""+objigate);
            prop.setProperty("objects.rfgate.range", ""+radius);
            prop.setProperty("igate.rfgate.path", path_igate);
            prop.setProperty("message.rfpath", path_messages);
            prop.setProperty("objects.rfpath", path_objects);
            prop.setProperty("message.alwaysRf", ""+always_rf);
            prop.setProperty("remotectl.on", ""+remotectl);
            prop.setProperty("remotectl.radius", ""+remote_radius);
            prop.setProperty("remotectl.connect", rc_server);
            prop.setProperty("message.auth.key", authkey);
        }
        
    }
    
    
    public static record OwnPos 
    (
        boolean txon, boolean allowrf, boolean compress, 
        String symbol, 
        String rfpath,
        String comment,
        double[] pos,
        boolean gpson, boolean adjustclock,
        String gpsport,
        int baud, 
        int minpause, int maxpause, int mindist, int maxturn
    ) 
    {
        public OwnPos(ServerAPI api) {
            this (
                api.getBoolProperty("ownposition.tx.on", false),
                api.getBoolProperty("ownposition.tx.allowrf", false),
                api.getBoolProperty("ownposition.tx.compress", false),
                api.getProperty("ownposition.symbol", "/c"),
                api.getProperty("ownposition.tx.rfpath", ""),
                api.getProperty("ownposition.tx.comment", ""),
                api.getPosProperty("ownposition.position"), 
                api.getBoolProperty("ownposition.gps.on", false),
                api.getBoolProperty("ownposition.gps.adjustclock", false),
                api.getProperty("ownposition.gps.port", ""),
                api.getIntProperty("ownposition.gps.baud", 4800),
                api.getIntProperty("ownposition.tx.minpause", 0),
                api.getIntProperty("ownposition.tx.maxpause", 0),
                api.getIntProperty("ownposition.tx.mindist",  0),
                api.getIntProperty("ownposition.tx.maxturn",  0)
            );
        } 
        
        public void save(ServerAPI api) {
            Properties  prop = api.getConfig();
            prop.setProperty("ownposition.tx.on", ""+txon);
            prop.setProperty("ownposition.tx.allowrf", ""+allowrf);
            prop.setProperty("ownposition.tx.compress", ""+compress);
            prop.setProperty("ownposition.symbol", symbol);
            prop.setProperty("ownposition.tx.rfpath", rfpath);
            prop.setProperty("ownposition.tx.comment", comment);
            prop.setProperty("ownposition.position", pos[0]+","+pos[1]);
            prop.setProperty("ownposition.gps.on", ""+gpson);
            prop.setProperty("ownposition.gps.adjustclock", ""+adjustclock);
            prop.setProperty("ownposition.gps.port", gpsport);
            prop.setProperty("ownposition.gps.baud", ""+baud);
            prop.setProperty("ownposition.tx.minpause", ""+minpause);
            prop.setProperty("ownposition.tx.maxpause", ""+maxpause);
            prop.setProperty("ownposition.tx.mindist",  ""+mindist);
            prop.setProperty("ownposition.tx.maxturn",  ""+maxturn);
        }   
    }
    
    
    
    /** 
     * Return an error status message to client. 
     * FIXME: Move to superclass. 
     */
    public String ERROR(Response resp, int status, String msg)
      { resp.status(status); return msg; }
      
      
    private Date _time = new Date();
    
    
    /** 
     * Set up the webservices. 
     */
    public void start() {     
    
        /******************************************
         * Restart server program
         ******************************************/
        put("/system/adm/restart", (req, resp) -> {
            ProcessBuilder pb = new ProcessBuilder("/usr/bin/sudo", "-n", "/usr/bin/polaric-restart");
            pb.inheritIO();
            pb.start(); 
            return "Ok";
        });     
             
             
             
        /******************************************
         * Get system status info
         ******************************************/
        get("/system/adm/status", "application/json", (req, resp) -> {
            SysInfo res = new SysInfo();
            res.runsince = _time;
            res.version = _api.getVersion();
            res.items = _api.getDB().nItems();
            res.ownobj = _api.getDB().getOwnObjects().nItems();
            res.clients = _api.getWebserver().nClients();
            res.loggedin = _api.getWebserver().nLoggedin();
            res.usedmem = StationDBBase.usedMemory();
            
            /* Plugins */
            PluginManager.Plugin[] plugins = PluginManager.getPlugins();
            res.plugins = new ArrayList<String>(); 
            for (PluginManager.Plugin x: plugins)
                res.plugins.add(x.getDescr());
        
            /* Connected servers */
            RemoteCtl rctl = _api.getRemoteCtl(); 
            res.remotectl = (rctl == null ? "" : rctl.toString());
            
            return res;
        }, ServerBase::toJson );
        
        
        
        /******************************************
         * Get clients
         ******************************************/
        get("/system/adm/clients", (req, resp) -> {
            WebServer ws = (WebServer) _api.getWebserver();
            List<ClientInfo> res = new ArrayList<ClientInfo>();
            
            for ( WsNotifier.Client x : ws.getJsonMapUpdater().clients())
                res.add(new ClientInfo(x.created(), x.getUid(), x.nIn(), x.nOut(), x.getUsername(), x.isMobile()));
            
            return res;
        }, ServerBase::toJson );
        
        
        
        
        /******************************************
         * Get server config
         ******************************************/
        get("/system/adm/server", (req, resp) -> {
            ServerConfig conf = new ServerConfig(_api);
            return conf;
        }, ServerBase::toJson );
                
        
        
        
        /******************************************
         * Update server config
         ******************************************/
        put("/system/adm/server", (req, resp) -> {
            ServerConfig conf = (ServerConfig) ServerBase.fromJson(req.body(), ServerConfig.class);
            conf.save(_api);
            // FIXME: Make sure server reboots/reloads settings
            return "Ok";
        });
        
        
        /******************************************
         * Get server config - own position
         ******************************************/
        get("/system/adm/ownpos", (req, resp) -> {
            OwnPos conf = new OwnPos(_api);
            return conf;
        }, ServerBase::toJson );
                
           
           
        /******************************************
         * Update server config
         ******************************************/
        put("/system/adm/ownpos", (req, resp) -> {
            OwnPos conf = (OwnPos) ServerBase.fromJson(req.body(), OwnPos.class);
            conf.save(_api);
            // FIXME: Make sure server reboots/reloads settings
            return "Ok";
        });
        
        
        
        /******************************************
         * Get server config - get channel names
         ******************************************/
        get("/system/adm/channels", (req, resp) -> {
            Set<String> chans = _api.getChanManager().getKeys();
            List<ChannelInfo> res = new ArrayList<ChannelInfo>();
            for (String chn:  _api.getChanManager().getKeys()) {
                Channel ch = _api.getChanManager().get(chn);
                
                res.add( new ChannelInfo(ch.getIdent(), ch.isActive(),  
                   ch==_api.getRfChannel(), ch==_api.getInetChannel(), ch.isRf(),
                   ch instanceof AprsChannel,
                   new GenChanInfo(_api, ch), 
                   ch.getJsConfig()));
            }
            return res;
        }, ServerBase::toJson );
        
            
       /******************************************
        * Return list of connected clients
        ******************************************/
        get("/system/adm/channels/*/clients", (req, resp) -> {
            var chname = req.splat()[0];
            Channel.Manager mgr = _api.getChanManager(); 
            Channel ch = mgr.get(chname);
            if (ch==null)
                return ERROR(resp, 404, "Channel not found: "+chname);
            
            List<InetSrvClient.Info> res = new ArrayList<InetSrvClient.Info>();
            if (ch instanceof InetSrvChannel ich) {
                for (InetSrvClient x: ich.getClients())
                    res.add(x.getInfo());
                return res;
            }
            else
                return ERROR(resp, 400, "Invalid channel type: "+chname);
        }, ServerBase::toJson );
        
        
        
        /******************************************
         * Get server config - get channel
         ******************************************/
        get("/system/adm/channels/*", (req, resp) -> {
            var chname = req.splat()[0];
            Channel.Manager mgr = _api.getChanManager(); 
            Channel ch = mgr.get(chname);
            if (ch==null)
                return ERROR(resp, 404, "Channel not found: "+chname);
                
            return new ChannelInfo(
                ch.getIdent(), 
                _api.getBoolProperty("channel."+ch.getIdent()+".on", false),  
                ch==_api.getRfChannel(), ch==_api.getInetChannel(), ch.isRf(), 
                ch instanceof AprsChannel, 
                new GenChanInfo(_api, ch), 
                ch.getJsConfig()
            );
        }, ServerBase::toJson );
    
        

        
       /******************************************
        * Update channel
        ******************************************/
        put("/system/adm/channels/*", (req, resp) -> {
        try {
            var chname = req.splat()[0];
            var mgr = _api.getChanManager(); 
            var ch = mgr.get(chname);
            if (ch==null)
                return ERROR(resp, 404, "Channel not found: "+chname);

            ChannelInfo conf = (ChannelInfo) ServerBase.fromJson(req.body(), ChannelInfo.class);
            if (conf==null)
                return ERROR(resp, 400, "Couldn't parse input");  
                
            setChannelProps(ch, conf, null);
            return "Ok";
            
        } catch (Exception e ) {
             e.printStackTrace(System.out);
             return ERROR(resp, 500, "ERRROR: "+e.getMessage());  
        }
        
        });
    
    
    
       /******************************************
        * Add a channel
        ******************************************/
        post("/system/adm/channels", (req, resp) -> {
        try {
            ChannelInfo conf = (ChannelInfo) ServerBase.fromJson(req.body(), ChannelInfo.class);
            if (conf==null)
                return ERROR(resp, 400, "Couldn't parse input");
                
            String tname=conf.specific.type;
            var chmgr = _api.getChanManager();
            var ch = chmgr.newInstance(_api, tname, conf.name);
            if (ch==null)
                return ERROR(resp, 400, "Couldn't instantiate channel: "+tname+": "+conf.name);
            setChannelProps(ch, conf, tname);
            if (ch instanceof AprsChannel ach)
                ach.addReceiver(_api.getAprsParser());
            updateChanList();
            return "Ok";
            
        } catch (Exception e ) {
             e.printStackTrace(System.out);
             return ERROR(resp, 500, "ERRROR: "+e.getMessage());  
        }    
            
        });  
        
        
            
       /******************************************
        * Remove a channel and its config
        ******************************************/
        delete("/system/adm/channels/*", (req, resp) -> {
            try {
            var chname = req.splat()[0];
            var mgr = _api.getChanManager(); 
            var ch = mgr.get(chname);
            if (ch==null)
                return ERROR(resp, 404, "Channel not found: "+chname);
            ch.deActivate();
            removeChannelConfig(ch);
            mgr.removeInstance(chname);
            updateChanList();
            
            } catch (Exception e) {
                e.printStackTrace(System.out);
                return ERROR(resp, 500, "ERRRROR");  
            }
            return "Ok";
        });
        
    }
}


