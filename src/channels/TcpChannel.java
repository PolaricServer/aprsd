 
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
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;


/**
 * TCP channel. Connect to a server on the internet. 
 */
public abstract class TcpChannel extends AprsChannel implements Runnable, Serializable
{
    private   String   _host;
    private   int      _port;
    protected boolean  _close = false;
    protected String   _backup; 
    
    transient protected Socket       _sock = null;     
    transient private   int          _max_retry;
    transient private   long         _retry_time;   
    transient private   Thread       _thread;
    transient protected int          _chno;
    
    private static int _next_chno = 0;
    


    
    public TcpChannel(ServerAPI api, String id) 
    {
        _init(api, "channel", id);
        _api = api;
        _chno = _next_chno;
        _next_chno++;
        _state = State.OFF;
    }
 
    
   /**
    * Load/reload configuration parameters. Called each time channel is activated. 
    */
   protected void getConfig()
   {
        String id = getIdent();
        _host = _api.getProperty("channel."+id+".host", "localhost");
        _port = _api.getIntProperty("channel."+id+".port", 21);
        _backup = _api.getProperty("channel."+id+".backup", null);
        _max_retry  = _api.getIntProperty("channel."+id+".retry", 8);
        _retry_time = Long.parseLong(_api.getProperty("channel."+id+".retry.time", "10")) * 60 * 1000; 
        if (_backup != null)
           _api.getChanManager().addBackup(_backup);
   }
   
 
    /** Start the service */
    public void activate(ServerAPI a) {
        getConfig(); 
        _thread = new Thread(this, "channel."+getIdent());
        _thread.start();
        
        /* If there is a backup running, deactivate it */
        Channel back = _api.getChanManager().get(_backup); 
        if (back != null)
           back.deActivate();
    }

    
    /** Stop the service */
    public void deActivate() {
        close();
    }
    
    public String getHost() 
        {return _host;}
    
    
    
    public void close()
    {
       try {
         _close = true;
         Thread.sleep(1000);
         _close();
       } catch (Exception e) {}  
    }
    
    
    protected abstract void _close();
    protected abstract void receiveLoop() throws Exception;
    
    
    private transient final ScheduledExecutorService scheduler =
        Executors.newScheduledThreadPool(1);
    
    
    protected boolean try_backup()
    {
        if (_backup != null) {
           final AprsChannel ch = (AprsChannel) _api.getChanManager().get(_backup);         
           if (ch == null) {
              _api.log().info("TcpChannel", chId()+"Backup channel '"+_backup+"' not found");
              return false;
           }
           _api.log().info("TcpChannel", chId()+"Activating backup channel '"+_backup+"'");
           ch.activate(_api);
           if (this == _api.getInetChannel())
              _api.setInetChannel(ch);
           
           /* Re-try the first channel after two hours */
           scheduler.schedule(new Runnable() {
                 public void run() { 
                    _api.log().info("TcpChannel", chId()+"Try to re-activate channel '"+this+"' after using backup");
                    ch.deActivate(); 
                    activate(_api); 
                 }
              }, 2 * 60 * 60, TimeUnit.SECONDS);
        }
        return true;
    }
    
    
    
    
    /**
     * Main thread - connects to APRS-IS server and awaits incoming packets. 
     */
    public void run()
    {
        int retry = 0;   
        _close = false; 
        _api.log().info("TcpChannel", chId()+"Activating...");
        while (true) 
        { 
           _state = State.STARTING;
           if (retry <= (_backup!=null ? _max_retry : _max_retry *2) || _max_retry == 0) 
              try { 
                 long sleep = 60000 * (long) retry;
                 if (sleep > _retry_time) 
                    sleep = _retry_time; 
                 Thread.sleep(sleep);
              } 
              catch (Exception e) {} 
           else break;       
         
           try {
               _sock = new Socket(_host, _port);
                 // 5 minutes timeout
               _sock.setSoTimeout(1000 * 60 * 5);       
               if (!_sock.isConnected()) 
               {
                   _api.log().warn("TcpChannel", chId()+"Connection to server '"+_host+"' failed");
                   retry++;
                   continue; // WHATS GOING ON HERE?
               }
               
               retry = 0; 
                _api.log().info("TcpChannel", chId()+"Connection to server '"+_host+"' established");
               _state = State.RUNNING;
               receiveLoop();  
           }
           catch (java.net.ConnectException e)
           {
                _api.log().warn("TcpChannel", chId()+"Server '"+_host+"' : "+e.getMessage());
           }
           catch (java.net.SocketTimeoutException e)
           {
                _api.log().warn("TcpChannel", chId()+"Server '"+_host+"' : socket timeout");
           }
           catch (java.net.UnknownHostException e)
           {
                _api.log().warn("TcpChannel", chId()+"Server '"+_host+"' : Unknown host");
                retry = _max_retry-2;
                /* One more chance. Then give up */
           }
           catch(Exception e)
           {   
                _api.log().error("TcpChannel", chId()+"Server '"+_host+"' : "+e); 
                e.printStackTrace(System.out);
           }
           finally 
             { _close(); }
        
           if (_close) {
              _state = State.OFF;
               _api.log().info("TcpChannel", chId()+"Channel closed");
              return;
           }
                   
           retry++;
        }
         _api.log().warn("TcpChannel", chId()+"Couldn't connect to server '"+_host+"' - giving up");   
        _state = State.FAILED;
        
        /* Try to start backup channel if available */  
        try_backup();
    }
 
    public String toString() { return _host+":"+_port; }
}

