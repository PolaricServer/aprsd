 
/* 
 * Copyright (C) 2009 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import java.io.*;
import java.net.*;
import java.util.*;


/**
 * Internet channel. Connect to APRS-IS server. 
 */
public class InetChannel extends TcpChannel
{
    private  String   _user, _pass, _filter;
    transient private  BufferedReader _rder = null;

    
    public InetChannel(ServerAPI api, String id) 
       { super(api, id); }
       
    
    @Override protected void getConfig() {
       super.getConfig();
       String id = getIdent();
       _user = _api.getProperty("channel."+id+".user", "").toUpperCase();
       if (_user.length() == 0)
           _user = _api.getProperty("default.mycall", "NOCALL").toUpperCase();
       _pass     = _api.getProperty("channel."+id+".pass", "-1");
       _filter   = _api.getProperty("channel."+id+".filter", ""); 
       _rfilter  = _api.getProperty("channel."+id+".rfilter", ""); 
    }
 

    @Override public String getShortDescr()
       { return "is"+_chno; }
       
        
    /**
     * Send a packet to APRS-IS.
     */ 
    public void sendPacket(Packet p)
    {  
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
        }      
    }
    
    
    @Override protected void _close()
    {
       try { 
          if (_rder != null) _rder.close(); 
          if (_out != null)  _out.close();
          if (_sock != null) _sock.close(); 
          Thread.sleep(500);
       } catch (Exception e) {}
    }
    
    

    
    @Override protected void regHeard(Packet p)
    {
        if (p.via.matches(".*(TCPIP\\*|TCPXX\\*).*"))
           _heard.put(p.from, new Heard(new Date(), p.via));
    }
    
    
    
    
    protected void receiveLoop() throws Exception
    {    
         _rder = new BufferedReader(new InputStreamReader(_sock.getInputStream(), _rx_encoding));
         _out = new PrintWriter(new OutputStreamWriter(_sock.getOutputStream(), _tx_encoding));         
         _out.print("user "+_user +" pass "+_pass+ " vers Polaric-APRSD "+_api.getVersion()+"\r\n");
         
         if (_filter.length() > 0)
             _out.print("# filter "+_filter+ "\r\n"); 
         _out.flush();
         
         while (!_close) 
         {
             String inp = _rder.readLine(); 
             if (inp != null) 
                receivePacket(inp, false);
             else {   
                logNote("Disconnected from APRS server '"+getHost()+"'");
                break; 
             }              
         }
    }
    

}

