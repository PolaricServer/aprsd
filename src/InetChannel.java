 
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
public class InetChannel extends Channel implements Runnable, Serializable
{
    private   String      _host;
    private   int         _port;
    private   String      _user, _pass, _filter;
    private   boolean     _close = false;
    
    transient private   int         _max_retry;
    transient private   long        _retry_time;   
    transient private   Socket      _sock = null; 
    transient private   BufferedReader _rder = null;
    transient private   ServerAPI   _api;
    




    public InetChannel(ServerAPI api, String prefix) 
    {
        if (prefix==null)
           prefix = "inetchannel"; 
        Properties config = api.getConfig();
        _init(config, prefix);
        _api = api;
        
        _host = config.getProperty(prefix+".host", "localhost").trim();
        _port = Integer.parseInt(config.getProperty(prefix+".port", "14580").trim());
        _max_retry = Integer.parseInt(config.getProperty(prefix+".retry", "0").trim());
        _retry_time = Long.parseLong(config.getProperty(prefix+".retry.time", "30").trim()) * 60 * 1000; 
        _user = config.getProperty(prefix+".user", "").trim().toUpperCase();
        if (_user.length() == 0)
           _user = config.getProperty("default.mycall", "NOCALL").trim().toUpperCase();
        _pass = config.getProperty(prefix+".pass", "-1").trim();
        _filter = config.getProperty(prefix+".filter", ""); 
    }
 

    @Override public String getShortDescr()
       { return "IS"; }
       
        
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
        }      
    }
    
    private void _close()
    {
       try { 
          if (_rder != null) _rder.close(); 
          if (_out != null) _out.close();
          if (_sock != null) _sock.close(); 
          Thread.sleep(500);
       } catch (Exception e) {}
    }
    
    public void close()
    {
       try {
         _close = true;
         Thread.sleep(5000);
         _close();
       } catch (Exception e) {}  
    }
    

    
    @Override protected void regHeard(Packet p)
    {
        if (p.via.matches(".*(TCPIP\\*|TCPXX\\*).*"))
           _heard.put(p.from, new Heard(new Date(), p.via));
    }
    

    
    /**
     * Main thread - connects to APRS-IS server and awaits incoming packets. 
     */
    public void run()
    {
        int retry = 0;             
        while (true) 
        { 
           try {
               _sock = new Socket(_host, _port);
                 // 5 minutes timeout
               _sock.setSoTimeout(1000 * 60 * 5);       
               if (!_sock.isConnected()) 
               {
                   System.out.println("*** Connection to APRS server '"+_host+"' failed");
                   retry++;
                   continue; // WHATS GOING ON HERE?
               }
               
               retry = 0; 
               System.out.println("*** Connection to APRS server '"+_host+"' established");
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
                      System.out.println("*** Disconnected from APRS server '"+_host+"'");
                      break; 
                   }              
               }
           }
           catch (java.net.ConnectException e)
           {
                System.out.println("*** APRS server '"+_host+"' : "+e.getMessage());
           }
           catch (java.net.SocketTimeoutException e)
           {
                System.out.println("*** APRS server'"+_host+"' : socket timeout");
           }
           catch(Exception e)
           {   
                System.out.println("*** APRS server '"+_host+"' : "+e); 
                e.printStackTrace(System.out);
           }
           finally 
             { _close(); }
        
           if (_close)
                   return;
                   
           retry++;
           if (retry <= _max_retry || _max_retry == 0) // MOVE THIS TO TOP OF LOOP
               try { 
                   long sleep = 30000 * (long) retry;
                   if (sleep > _retry_time) 
                      sleep = _retry_time; /* Default: Max 30 minutes */
                   Thread.sleep(sleep); 
               } 
               catch (Exception e) {} 
           else break;
        }
        System.out.println("*** Couldn't connect to APRS server '"+_host+"' - giving up");        
    }
 
    public String toString() { return _host+":"+_port+", userid="+_user; }
}

