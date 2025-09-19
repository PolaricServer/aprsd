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
 
package no.polaric.aprsd;
import no.polaric.aprsd.point.*;
import no.polaric.aprsd.channel.*;
import no.arctic.core.*;
import no.arctic.core.httpd.*;
import no.arctic.core.auth.*;
import io.javalin.Javalin;
import io.javalin.http.Context;
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
    
    
    private AprsServerConfig _conf; 
    
    public SysAdminApi(AprsServerConfig conf) {
        super(conf);
        _conf = conf;
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
        public GenChanInfo(ServerConfig conf, Channel ch) {
            this (
                ch.getState(),
                conf.getProperty("channel."+ch.getIdent()+".tag", ""),
                conf.getBoolProperty("channel."+ch.getIdent()+".restricted", false),
                (ch instanceof AprsChannel ach ? ach.isInRouter() : false)
            );
        }
    }


    
    
    /*
     * (re-) configure a channel f rom a channelinfo object. 
     */
    public  void setChannelProps(Channel ch, ChannelInfo cnf, String type) {
        if (ch.getIdent().equals(""))
            return;
        var props = _conf.getConfig();
        props.setProperty("channel."+ch.getIdent()+".on", ""+cnf.active);
        props.setProperty("channel."+ch.getIdent()+".tag", cnf.generic.tag);
        props.setProperty("channel."+ch.getIdent()+".restricted", ""+cnf.generic.restricted);
        if (type != null)
            props.setProperty("channel."+ch.getIdent()+".type", type);
        
        try {
        if (ch instanceof AprsChannel ach) {
            if (ach.isRf()) {
                if (_conf.getRfChannel() == ach && !cnf.rfchan) {
                    _conf.setRfChannel(null);
                    props.remove("channel.default.rf");
                }
                else if (cnf.rfchan) {
                    _conf.setRfChannel(ach);
                    props.setProperty("channel.default.rf", ach.getIdent());
                }
            }
            else {
                if (_conf.getInetChannel() == ach && !cnf.inetchan) {
                    _conf.setInetChannel(null);
                    props.remove("channel.default.inet");
                }
                else if (cnf.inetchan) {
                    _conf.setInetChannel(ach);
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
            ch.activate(_conf);
        }
        else if (ch.isActive())
            ch.deActivate();
    }
    
    
    
    public void removeChannelConfig(Channel ch) {
        Properties cnf = _conf.getConfig();
        for (Object key: cnf.keySet())
            if (((String)key).startsWith("channel."+ch.getIdent()))
                cnf.remove((String) key);
    }
    
    
    /* 
     * Re-create the channel list which is a comma-separated 
     * text-string
     */
    public void updateChanList() {
        var mgr = _conf.getChanManager(); 
        var i = 0;
        String chlist = ""; 
        
        for (String chn : mgr.getKeys()) {
            if (i>0)
                chlist+=",";
            chlist += chn; 
            i++;
        }
        Properties cnf = _conf.getConfig();
        cnf.setProperty("channels", chlist);
    }
    
    
    
    
    public static record ClientInfo
       (Date created, String cid, long in, long out, String userid, boolean mobile)
    {}
    
    
    
    public static record ServerConfigData 
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
        public ServerConfigData(AprsServerConfig conf) {
            this (
                conf.getProperty("default.mycall", "NOCALL"),
                conf.getBoolProperty("igate.on", false),
                conf.getBoolProperty("igate.rfgate.allow", false),
                conf.getBoolProperty("objects.rfgate.allow", false),
                conf.getIntProperty("objects.rfgate.range", 10),
                conf.getProperty("igate.rfgate.path", ""),
                conf.getProperty("message.rfpath", ""),
                conf.getProperty("objects.rfpath", ""),
                conf.getProperty("message.alwaysRf", ""),
                conf.getBoolProperty("remotectl.on", false),
                conf.getIntProperty("remotectl.radius", 10),
                conf.getProperty("remotectl.connect", ""),
                conf.getProperty("message.auth.key", "")
            );
        }
        
        public void save(AprsServerConfig conf) {
            Properties  prop = conf.getConfig();
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
        public OwnPos(AprsServerConfig conf) {
            this (
                conf.getBoolProperty("ownposition.tx.on", false),
                conf.getBoolProperty("ownposition.tx.allowrf", false),
                conf.getBoolProperty("ownposition.tx.compress", false),
                conf.getProperty("ownposition.symbol", "/c"),
                conf.getProperty("ownposition.tx.rfpath", ""),
                conf.getProperty("ownposition.tx.comment", ""),
                conf.getPosProperty("ownposition.pos"), 
                conf.getBoolProperty("ownposition.gps.on", false),
                conf.getBoolProperty("ownposition.gps.adjustclock", false),
                conf.getProperty("ownposition.gps.port", ""),
                conf.getIntProperty("ownposition.gps.baud", 4800),
                conf.getIntProperty("ownposition.tx.minpause", 0),
                conf.getIntProperty("ownposition.tx.maxpause", 0),
                conf.getIntProperty("ownposition.tx.mindist",  0),
                conf.getIntProperty("ownposition.tx.maxturn",  0)
            );
        } 
        
        public void save(AprsServerConfig conf) {
            Properties  prop = conf.getConfig();
            prop.setProperty("ownposition.tx.on", ""+txon);
            prop.setProperty("ownposition.tx.allowrf", ""+allowrf);
            prop.setProperty("ownposition.tx.compress", ""+compress);
            prop.setProperty("ownposition.symbol", symbol);
            prop.setProperty("ownposition.tx.rfpath", rfpath);
            prop.setProperty("ownposition.tx.comment", comment);
            prop.setProperty("ownposition.pos", pos[0]+","+pos[1]);
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
    public Object ERROR(Context ctx, int status, String msg)
      { ctx.status(status); ctx.result(msg); return null;}
      
 
 
      
    private Date _time = new Date();
    
    
    /** 
     * Set up the webservices. 
     */
    public void start() {     
            
        protect("/system/adm/*", "admin");
            
        /******************************************
         * Restart server program
         ******************************************/
        a.put("/system/adm/restart", (ctx) -> {
            ProcessBuilder pb = new ProcessBuilder("/usr/bin/sudo", "-n", "/usr/bin/polaric-restart");
            pb.inheritIO();
            pb.start(); 
            ctx.result("Ok");
        });     
             
             
             
        /******************************************
         * Get system status info
         ******************************************/
        a.get("/system/adm/status", (ctx) -> {
            SysInfo res = new SysInfo();
            res.runsince = _time;
            res.version = _conf.getVersion();
            res.items = _conf.getDB().nItems();
            res.ownobj = _conf.getDB().getOwnObjects().nItems();
            res.clients = _conf.getWebserver().nClients();
            res.loggedin = _conf.getWebserver().nLoggedin();
            res.usedmem = StationDBBase.usedMemory();
            
            /* Plugins */
            PluginManager.Plugin[] plugins = PluginManager.getPlugins();
            res.plugins = new ArrayList<String>(); 
            for (PluginManager.Plugin x: plugins)
                res.plugins.add(x.getDescr());
        
            /* Connected servers */
            RemoteCtl rctl = _conf.getRemoteCtl(); 
            res.remotectl = (rctl == null ? "" : rctl.toString());
            
            ctx.json(res);
        });
        
        
        
        /******************************************
         * Get clients
         ******************************************/
        a.get("/system/adm/clients", (ctx) -> {
            MyWebServer ws = (MyWebServer) _conf.getWebserver();
            List<ClientInfo> res = new ArrayList<ClientInfo>();
            
            for ( WsNotifier.Client x : ws.getJsonMapUpdater().clients())
                res.add(new ClientInfo(x.created(), x.uid(), x.nIn(), x.nOut(), x.userName(), x.isMobile()));
            ctx.json(res);
        });
        
        
        
        
        /******************************************
         * Get server config
         ******************************************/
        a.get("/system/adm/server", (ctx) -> {
            ServerConfigData confdata = new ServerConfigData(_conf);
            ctx.json(confdata);
        });
                
        
        
        
        /******************************************
         * Update server config
         ******************************************/
        a.put("/system/adm/server", (ctx) -> {
            ServerConfigData confdata = (ServerConfigData) ServerBase.fromJson(ctx.body(), ServerConfigData.class);
            confdata.save(_conf);
            // FIXME: Make sure server reboots/reloads settings
            ctx.result("Ok");
        });
        
        
        /******************************************
         * Get server config - own position
         ******************************************/
        a.get("/system/adm/ownpos", (ctx) -> {
            OwnPos opos = new OwnPos(_conf);
            ctx.json(opos);
        });
                
           
           
        /******************************************
         * Update server config
         ******************************************/
        a.put("/system/adm/ownpos", (ctx) -> {
            OwnPos opos = (OwnPos) ServerBase.fromJson(ctx.body(), OwnPos.class);
            opos.save(_conf);
            // FIXME: Make sure server reboots/reloads settings
            ctx.result("Ok");
        });
        
        
        
        /******************************************
         * Get server config - get channel names
         ******************************************/
        a.get("/system/adm/channels", (ctx) -> {
            Set<String> chans = _conf.getChanManager().getKeys();
            List<ChannelInfo> res = new ArrayList<ChannelInfo>();
            for (String chn:  _conf.getChanManager().getKeys()) {
                Channel ch = _conf.getChanManager().get(chn);
                
                res.add( new ChannelInfo(ch.getIdent(), ch.isActive(),  
                   ch==_conf.getRfChannel(), ch==_conf.getInetChannel(), ch.isRf(),
                   ch instanceof AprsChannel,
                   new GenChanInfo(_conf, ch), 
                   ch.getJsConfig()));
            }
            ctx.json(res);
        });
        
            
       /******************************************
        * Return list of connected clients
        ******************************************/
        a.get("/system/adm/channels/{ch}/clients", (ctx) -> {
            var chname = ctx.pathParam("ch");
            Channel.Manager mgr = _conf.getChanManager(); 
            Channel ch = mgr.get(chname);
            if (ch==null) {
                ERROR(ctx, 404, "Channel not found: "+chname);
                return;
            }
            List<InetSrvClient.Info> res = new ArrayList<InetSrvClient.Info>();
            if (ch instanceof InetSrvChannel ich) {
                for (InetSrvClient x: ich.getClients())
                    res.add(x.getInfo());
                ctx.json(res);
            }
            else
                ERROR(ctx, 400, "Invalid channel type: "+chname);
        });
        
        
        
        /******************************************
         * Get server config - get channel
         ******************************************/
        a.get("/system/adm/channels/{ch}", (ctx) -> {
            var chname = ctx.pathParam("ch");
            Channel.Manager mgr = _conf.getChanManager(); 
            Channel ch = mgr.get(chname);
            if (ch==null)
                ERROR(ctx, 404, "Channel not found: "+chname);
            else    
                ctx.json(
                    new ChannelInfo(
                        ch.getIdent(), 
                        _conf.getBoolProperty("channel."+ch.getIdent()+".on", false),  
                        ch==_conf.getRfChannel(), ch==_conf.getInetChannel(), ch.isRf(), 
                        ch instanceof AprsChannel, 
                        new GenChanInfo(_conf, ch), 
                        ch.getJsConfig()
                    ));
        });
    
        

        
       /******************************************
        * Update channel
        ******************************************/
        a.put("/system/adm/channels/{ch}", (ctx) -> {
        try {
            var chname = ctx.pathParam("ch");
            var mgr = _conf.getChanManager(); 
            var ch = mgr.get(chname);
            if (ch==null) {
                ERROR(ctx, 404, "Channel not found: "+chname);
                return; 
            }
            ChannelInfo conf = (ChannelInfo) ServerBase.fromJson(ctx.body(), ChannelInfo.class);
            if (conf==null)
                ERROR(ctx, 400, "Couldn't parse input");  
            else {     
                setChannelProps(ch, conf, null);
                ctx.result("Ok");
            }
            
        } catch (Exception e ) {
             e.printStackTrace(System.out);
             ERROR(ctx, 500, "ERRROR: "+e.getMessage());  
        }
        });
    
    
    
       /******************************************
        * Add a channel
        ******************************************/
        a.post("/system/adm/channels", (ctx) -> {
        try {
            ChannelInfo conf = (ChannelInfo) ServerBase.fromJson(ctx.body(), ChannelInfo.class);
            if (conf==null) {
                ERROR(ctx, 400, "Couldn't parse input");
                return;
            }
            
            String tname=conf.specific.type;
            var chmgr = _conf.getChanManager();
            var ch = chmgr.newInstance(_conf, tname, conf.name);
            if (ch==null)
                ERROR(ctx, 400, "Couldn't instantiate channel: "+tname+": "+conf.name);
            else {
                setChannelProps(ch, conf, tname);
                if (ch instanceof AprsChannel ach)
                    ach.addReceiver(_conf.getAprsParser());
                updateChanList();
                ctx.result("Ok");
            }
            
        } catch (Exception e ) {
             e.printStackTrace(System.out);
             ERROR(ctx, 500, "ERROR: "+e.getMessage());  
        }    
            
        });  
        
        
            
       /******************************************
        * Remove a channel and its config
        ******************************************/
        a.delete("/system/adm/channels/{ch}", (ctx) -> {
            try {
                var chname = ctx.pathParam("ch");
                var mgr = _conf.getChanManager(); 
                var ch = mgr.get(chname);
                if (ch==null) {
                    ERROR(ctx, 404, "Channel not found: "+chname);
                    return;
                }
                ch.deActivate();
                removeChannelConfig(ch);
                mgr.removeInstance(chname);
                updateChanList();
            
            } catch (Exception e) {
                e.printStackTrace(System.out);
                ERROR(ctx, 500, "ERROR");  
                return;
            }
            ctx.result("Ok");
        });
        
    }
}


