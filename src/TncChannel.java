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
      
    transient private   int          _max_retry;
    transient private   long         _retry_time;   
    transient protected SerialPort   _serialPort;
    transient private   Semaphore    _sem = new Semaphore(1, true);
    transient protected Logfile      _log; 
    transient private   Thread       _thread;
    transient private   int          _chno;
    
    private static int _next_chno = 0;
    
    
 
    public TncChannel(ServerAPI api, String id) 
    {
        _init(api, "channel", id);
        _chno = _next_chno;
        _next_chno++;
        
        _myCall = api.getProperty("channel."+id+".mycall", "").toUpperCase();
        if (_myCall.length() == 0)
           _myCall = api.getProperty("default.mycall", "NOCALL").toUpperCase();       
        _max_retry = api.getIntProperty("channel."+id+".retry", 0);
        _retry_time = Long.parseLong(api.getProperty("channel."+id+".retry.time", "30")) * 60 * 1000; 
        _portName = api.getProperty("channel."+id+".port", "/dev/ttyS0");
        _baud = api.getIntProperty("channel."+id+".baud", 9600);
        // FIXME: set gnu.io.rxtx.SerialPorts property here instead of in startup script
        _log = new Logfile(api, id, "rf.log");
        _thread = new Thread(this, "channel."+id);
        _thread.start();
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
            System.out.println("*** ERROR: Port "+ _portName + " is currently in use");
        else
        {
            CommPort commPort = portIdentifier.open(this.getClass().getName(), 2000);       
            if ( commPort instanceof SerialPort )
            {
                SerialPort serialPort = (SerialPort) commPort;
                serialPort.setSerialPortParams(_baud, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
                serialPort.enableReceiveTimeout(1000);
                if (!serialPort.isReceiveTimeoutEnabled())
                   System.out.println("*** WARNING: Timeout not enabled on serial port");
                return (SerialPort) commPort;
            }
            else
                System.out.println("*** ERROR: Port " + _portName + " is not a serial port.");
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
               System.out.println("*** Initialize TNC on "+_portName);
               _serialPort = connect();
               if (_serialPort == null)
                   continue; 
               receiveLoop();
           }
           catch(NoSuchPortException e)
           {
                System.out.println("*** WARNING: serial port " + _portName + " not found");
                e.printStackTrace(System.out);
           }
           catch(Exception e)
           {   
                e.printStackTrace(System.out); 
                close();
           }  
           retry++;      
        }
        System.out.println("*** Couldn't connect to TNC on'"+_portName+"' - giving up");
     }
       
       
       

    public String toString() { return "TNC on " + _portName+", "+_baud+" baud]"; }

}

