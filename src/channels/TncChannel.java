/* 
 * Copyright (C) 2011 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import java.util.*;
import gnu.io.*;
import java.util.concurrent.Semaphore;

/**
 * TNC channel. For devices in TNC2 compatible mode.
 */
 
public abstract class TncChannel extends Channel implements Runnable
{
    private   String  _portName; 
    private   int     _baud;
    protected String  _myCall; /* Move this ? */
    
    transient protected boolean      _close = false;
    transient private   int          _max_retry;
    transient private   long         _retry_time;   
    transient protected ServerAPI    _api;
    transient protected SerialPort   _serialPort;
    transient private   Semaphore    _sem = new Semaphore(1, true);
    transient protected Logfile      _log; 
    transient private   Thread       _thread;
    transient private   int          _chno;
       
    private static int _next_chno = 0;
    
    
 
    public TncChannel(ServerAPI api, String id) 
    {
        _init(api, "channel", id);
        _api = api;
        _chno = _next_chno;
        _next_chno++;
        
        _thread = new Thread(this, "channel."+id);
        _state = State.OFF;
    }
  
   
    /**
    * Load/reload configuration parameters. Called each time channel is activated. 
    */
   protected void getConfig()
   {
        String id = getIdent();
        _myCall = _api.getProperty("channel."+id+".mycall", "").toUpperCase();
        if (_myCall.length() == 0)
           _myCall = _api.getProperty("default.mycall", "NOCALL").toUpperCase();       
        _max_retry = _api.getIntProperty("channel."+id+".retry", 0);
        _retry_time = Long.parseLong(_api.getProperty("channel."+id+".retry.time", "30")) * 60 * 1000; 
        _portName = _api.getProperty("channel."+id+".port", "/dev/ttyS0");
        _baud = _api.getIntProperty("channel."+id+".baud", 9600);
        // FIXME: set gnu.io.rxtx.SerialPorts property here instead of in startup script
        _log = new Logfile(_api, id, "rf.log");
   }
   
   
    /** Start the service */
    public void activate(ServerAPI a) {
        getConfig();
        _thread.start();
    }

  
    /** Stop the service */
    public void deActivate() {
        close();
    }
    
    
    @Override protected void regHeard(Packet p) 
    {
        _heard.put(p.from, new Heard(new Date(), p.via));
        /* FIXME: Check for TCPxx in third party packets and consider registering 
         * more than one path instance */
    }
 
    
    @Override public String getShortDescr()
       { return "rf"+_chno; }
 

   

    /**
     * Setup the serial port
     */
    private SerialPort connect () throws Exception
    {
        CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(_portName);
        if ( portIdentifier.isCurrentlyOwned() )
            logNote("ERROR: Port "+ _portName + " is currently in use");
        else
        {
            CommPort commPort = portIdentifier.open(this.getClass().getName(), 2000);       
            if ( commPort instanceof SerialPort )
            {
                SerialPort serialPort = (SerialPort) commPort;
                serialPort.setSerialPortParams(_baud, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
                serialPort.enableReceiveTimeout(1000);
                if (!serialPort.isReceiveTimeoutEnabled())
                   logNote("WARNING: Timeout not enabled on serial port");
                return (SerialPort) commPort;
            }
            else
                logNote("ERROR: Port " + _portName + " is not a serial port.");
        }    
        return null; 
    }
   
   
    
    
    public abstract void close();   
    protected abstract void receiveLoop() throws Exception;
       
       
    public void run()
    {
        int retry = 0;             
        while (true) 
        {
           _state = State.STARTING;
           if (retry <= _max_retry || _max_retry == 0) 
               try { 
                   long sleep = 30000 * (long) retry;
                   if (sleep > _retry_time) 
                      sleep = _retry_time; /* Default: Max 30 minutes */
                   Thread.sleep(sleep); 
               } 
               catch (Exception e) {break;} 
           else break;
        
           try {
               logNote("Initialize TNC on "+_portName);
               _serialPort = connect();
               if (_serialPort == null)
                   continue; 
               _state = State.RUNNING;
               receiveLoop();
           }
           catch(NoSuchPortException e)
           {
                logNote("WARNING: serial port " + _portName + " not found");
                e.printStackTrace(System.out);
           }
           catch(Exception e)
           {   
                e.printStackTrace(System.out); 
                close();
           }  
                   
           if (_close) {
              _state = State.OFF;
              return;
           }
           
           retry++;      
        }
        logNote("Couldn't connect to TNC on'"+_portName+"' - giving up");
        _state = State.FAILED;
     }
       
       
       

    public String toString() { return "TNC on " + _portName+", "+_baud+" baud]"; }

}

