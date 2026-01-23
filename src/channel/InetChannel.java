/* 
 * Copyright (C) 2020-2025 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
 
package no.polaric.aprsd.channel;
import no.polaric.aprsd.*;
import no.polaric.aprsd.aprs.*;
import java.io.*;
import java.net.*;
import java.util.*;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonSubTypes.*;

/**
 * Internet channel. Connect to APRS-IS server. 
 */
public class InetChannel extends TcpChannel
{
    private  String   _user, _pass, _filter;
    private  BufferedReader _rder = null;
    private AprsFilter _xfilter;
    private long _blocked = 0;
    


    public InetChannel(AprsServerConfig conf, String id) 
       { super(conf, id); }
       
       
       
    @Override public void activate(AprsServerConfig a) {
       super.activate(_conf);
       String id = getIdent();
       _user = _conf.getProperty("channel."+id+".user", "").toUpperCase();
       if (_user.length() == 0)
       _user = _conf.getProperty("default.mycall", "NOCALL").toUpperCase();
       _pass  = _conf.getProperty("channel."+id+".pass", "-1");
       _filter = _conf.getProperty("channel."+id+".filter", "");
       String xfilt = _conf.getProperty("channel."+id+".xfilter", "*");
       _xfilter = AprsFilter.createFilter( xfilt, null);
       setReceiveFilter(_conf.getProperty("channel."+id+".rfilter", "")); 
    }
    
    
    /* 
     * Information about config to be exchanged in REST API
     */
    
    @JsonTypeName("APRSIS")
    public static class JsConfig extends Channel.JsConfig {
        public long heardpackets, heard, duplicates, sentpackets, blocked;
        public int port, pass; 
        public String host, filter, xfilter;
    }
       
       
    public JsConfig getJsConfig() {
        var cnf = new JsConfig();
        cnf.heard = nHeard();
        cnf.heardpackets = nHeardPackets(); 
        cnf.duplicates = nDuplicates(); 
        cnf.sentpackets = nSentPackets();
        cnf.blocked = _blocked;
        cnf.type  = "APRSIS";
        cnf.host  = _conf.getProperty("channel."+getIdent()+".host", "localhost");
        cnf.port  = _conf.getIntProperty("channel."+getIdent()+".port", 21);
        cnf.pass  = _conf.getIntProperty("channel."+getIdent()+".pass", 0); 
        cnf.filter = _conf.getProperty("channel."+getIdent()+".filter", "");
        cnf.xfilter = _conf.getProperty("channel."+getIdent()+".xfilter", "*");
        return cnf;
    }
    
    
    public void setJsConfig(Channel.JsConfig ccnf) {
        var cnf = (JsConfig) ccnf;
        var props = _conf.config();
        props.setProperty("channel."+getIdent()+".host", cnf.host);
        props.setProperty("channel."+getIdent()+".port", ""+cnf.port);
        props.setProperty("channel."+getIdent()+".pass", ""+cnf.pass);
        props.setProperty("channel."+getIdent()+".filter", cnf.filter); 
        props.setProperty("channel."+getIdent()+".xfilter", cnf.xfilter);
    }
    
       
        
    /**
     * Send a packet to APRS-IS.
     */ 
    public boolean sendPacket(AprsPacket p)
    {  
        if (!isReady() || !canSend)
            return false; 
        if (p.via == null || p.via.equals("")) {
            p = p.clone(); 
            p.via = "TCPIP*";
        }
        if (_out != null) {
            _out.println(p.from+">"+p.to + 
                ((p.via != null && p.via.length() > 0) ? ","+p.via : "") + 
                ":" + p.report );
                /* Should type char be part of report? */
            _out.flush();
            _sent++;
            return true;
        } 
        return false; 
    }
    
    

    @Override protected void _close()
    {
       try { 
          Thread.sleep(200);
          if (_out != null)  _out.close();
          if (_comm != null) _comm.deActivate(); 
          Thread.sleep(500);
       } catch (Exception e) {}
    }
    
    

    
    @Override protected void regHeard(AprsPacket p)
    {
        if (p.via.matches(".*(TCPIP\\*|TCPXX\\*).*"))
           _heard.put(p.from, new Heard(new Date(), p.via));
    }
    
    
    
    
    protected void receiveLoop() throws Exception
    {    
         _rder = new BufferedReader(new InputStreamReader(_comm.getInputStream(), _rx_encoding));
         _out = new PrintWriter(new OutputStreamWriter(_comm.getOutputStream(), _tx_encoding));         
         _out.print("user "+_user +" pass "+_pass+ " vers Polaric-APRSD "+_conf.getVersion());
         
         if (_filter.length() > 0)
             _out.print(" filter "+_filter);
         _out.print("\r\n");
         _out.flush();
         
         while (_comm.running()) 
         {
            try { 
                String inp = _rder.readLine(); 
                if (inp != null) {
                    if (inp.charAt(0) == '#') {
                        if (inp.length() > 7 && inp.matches("# Note:.*"))
                            _conf.log().info("InetChannel", inp.substring(2));
                        continue;
                    }
                    AprsPacket p = AprsPacket.fromString(inp);
                    if (p==null)
                        ;
                    else if (_xfilter == null || _xfilter.test(p))
                        receivePacket(p, false);
                    else
                        _blocked++;
                }
                else {   
                    _conf.log().info("InetChannel", chId()+"Disconnected from APRS server '"+getHost()+"'");
                    break; 
                }
            }
            catch (java.net.SocketException e) {
                _conf.log().info("InetChannel", chId()+"Socket closed"+e.getMessage());
                break;
            }
         }
    }
    

}

