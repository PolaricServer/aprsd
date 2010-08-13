 
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
package aprs;
import java.io.*;
import java.net.*;
import java.util.*;



public class InetChannel extends Channel implements Runnable
{
    private   String      _host;
    private   int         _port;
    private   String      _user, _pass, _filter;

    
    
    public InetChannel(Properties config) 
    {
        _host = config.getProperty("inetchannel.host", "localhost").trim();
        _port = Integer.parseInt(config.getProperty("inetchannel.port", "10151").trim());
        _user = config.getProperty("inetchannel.user", "TEST").trim();
        _pass = config.getProperty("inetchannel.pass", "-1").trim();
        _filter = config.getProperty("inetchannel.filter", ""); 
    }
 
 
    
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
    
 
    
    /**
     * Main thread - connects to server and awaits incoming packets. 
     */
    static final int MAX_RETRY = 8;  
    public void run()
    {

        int retry = 0;
        Socket sock = null; 
         
        while (retry <= MAX_RETRY) 
        { 
           try {
               sock = new Socket(_host, _port);
                 // 5 minutes timeout
               sock.setSoTimeout(1000 * 60 * 5);       
               if (!sock.isConnected()) 
               {
                   System.out.println("*** Connection to APRS server '"+_host+"' failed");
                   retry++;
                   continue; 
               }
               
               retry = 0; 
               System.out.println("*** Connection to APRS server '"+_host+"' established");
               BufferedReader rder = new BufferedReader(new InputStreamReader(sock.getInputStream(), _encoding));
               _out = new PrintWriter(new OutputStreamWriter(sock.getOutputStream(), _encoding));
               _out.print("user "+_user +" pass "+_pass+ " vers Polaric APRSD\r\n");
               if (_filter.length() > 0)
                   _out.print("# filter "+_filter+ "\r\n"); 
               _out.flush();
               while (true) 
               {
                   String inp = rder.readLine(); 
                   if (inp != null) {
                      System.out.println(new Date() + ":  "+inp);
                      receivePacket(inp);

                   }
                   else {   
                      System.out.println("*** Disconnected from APRS server '"+_host+"'");
                      break; 
                   }              
               }
           }
           catch (java.net.ConnectException e)
           {
              System.out.println("*** APRS server '"+_host+"' : "+e.getMessage());
              retry++; 
           }
           catch (java.net.SocketTimeoutException e)
           {
              System.out.println("*** APRS server: connection timeout");
           }
           catch(Exception e)
           {    System.out.println("*** Error in connection to APRS server '"+_host+"' : "+e); 
                e.printStackTrace(System.out); 
                retry += 2;
           }
        
           try { if (sock != null) sock.close(); } catch (Exception e) {}
           if (retry <= MAX_RETRY) 
               try { Thread.sleep(30000 * 2 ^ retry); } catch (Exception e) {} 
        }
        System.out.println("*** Couldn't connect to APRS server '"+_host+"' - giving up");        
    }
 
    public String toString() {return _host+":"+_port+", userid="+_user; }
}

