 
/* 
 * Copyright (C) 2020 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
 * TCP communication device. Connect to a server on the internet. 
 */
public class TcpComm extends CommDevice implements Runnable
{
    private   String   _host;
    private   int      _port;  
    
    protected Socket   _sock = null;     
    private   int      _max_retry;
    private   long     _retry_time;   
    
    
    /* To be implemented using a lambda function */
    interface Worker {
        void worker() throws Exception; 
    }
   
   
    public TcpComm(ServerAPI api, String id, String host, int port, int retr, long rtime) 
    {
        super(api, id);
        _host = host;
        _port = port;
        _max_retry = retr;
        _retry_time = rtime;
    }

    
    public OutputStream getOutputStream() throws IOException
        { return _sock.getOutputStream(); }
        
        
    public InputStream getInputStream() throws IOException
        {return _sock.getInputStream(); }
        
        
    public String getHost() 
        { return _host; }
    

    
    /**
     * Main thread - connects to APRS-IS server and awaits incoming packets. 
     */
    public void run()
    {
        int retry = 0;   
        _api.log().info("TcpChannel", "Activating...");
        try { Thread.sleep(500); } catch (Exception e) {}
        while (true) 
        { 
           _state = Channel.State.STARTING;
            if (retry <= _max_retry *2 || _max_retry == 0) 
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
                if (!_sock.isConnected()) {
                   _api.log().warn("TcpChannel", "Connection to server '"+_host+"' failed");
                   retry++;
                   continue; // WHATS GOING ON HERE?
                }
               
                retry = 0; 
                _api.log().info("TcpChannel", "Connection to server '"+_host+"' established");
                _state = Channel.State.RUNNING;
                if (_worker != null)
                    _worker.worker();  
            }
            catch (java.net.ConnectException e) {
                _api.log().warn("TcpChannel", "Server '"+_host+"' : "+e.getMessage());
            }
            catch (java.net.SocketTimeoutException e) {
                _api.log().warn("TcpChannel", "Server '"+_host+"' : socket timeout");
            }
            catch (java.net.UnknownHostException e) {
                _api.log().warn("TcpChannel", "Server '"+_host+"' : Unknown host");
                retry = _max_retry-2;
                /* One more chance. Then give up */
            }
           catch(Exception e) {   
                _api.log().error("TcpChannel", "Server '"+_host+"' : "+e); 
                e.printStackTrace(System.out);
            }
            finally 
                { /* Do some tidying?? */ }
        
            if (!running()) {
                _state = Channel.State.OFF;
                _api.log().info("TcpChannel", "Channel closed");
                return;
            }
                   
            retry++;
        }
         _api.log().warn("TcpChannel", "Couldn't connect to server '"+_host+"' - giving up");   
        _state = Channel.State.FAILED;
        
    }
    
}

