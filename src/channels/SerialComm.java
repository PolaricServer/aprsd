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
import java.util.*;
import gnu.io.*;
import java.util.concurrent.Semaphore;

/**
 * Serial comm port
 */
 
public abstract class SerialComm implements Runnable
{
    private   String  _portName; 
    private   int     _baud;
    
    private String        _ident;
    private Channel.State _state;
    
    transient protected boolean      _close = false;
    transient private   int          _max_retry;
    transient private   long         _retry_time;   
    transient protected SerialPort   _serialPort;
    transient private   Thread       _thread;
    transient private   ServerAPI    _api;
    
 
    public SerialComm(ServerAPI api, String id, String pname, int bd, int retr, long rtime) 
    {
        _ident=id;
        _portName=pname;
        _baud=bd;
        _max_retry=retr;
        _retry_time=rtime;
        _api=api;
    }
  
   
    public Channel.State getState() 
        {return _state; }
        
   
    /** Start the service */
    public void activate() {
        _thread = new Thread(this, "serial."+_ident);
        _thread.start();
    }

  
    /** Stop the service */
    public void deActivate() {
        _close=true;
    }
    
    
    public boolean running() {
        return !_close; 
    }
    
    
    public OutputStream getOutputStream() throws IOException
        {return _serialPort.getOutputStream(); }
        
        
    public InputStream getInputStream() throws IOException
        {return _serialPort.getInputStream(); }
    

    public void sendBreak(int n)
        {_serialPort.sendBreak(n); }
        
        
        
    /**
     * Setup the serial port
     */
    private SerialPort connect () throws Exception
    {
        CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(_portName);
        if ( portIdentifier.isCurrentlyOwned() )
            _api.log().error("SerialComm", "Port "+ _portName + " is currently in use");
        else
        {
            CommPort commPort = portIdentifier.open(this.getClass().getName(), 2000);       
            if ( commPort instanceof SerialPort )
            {
                SerialPort serialPort = (SerialPort) commPort;
                serialPort.setSerialPortParams(_baud, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
                serialPort.enableReceiveTimeout(1000);
                if (!serialPort.isReceiveTimeoutEnabled())
                   _api.log().warn("SerialComm", "Timeout not enabled on serial port");
                return (SerialPort) commPort;
            }
            else
                _api.log().error("SerialComm", "Port " + _portName + " is not a serial port.");
        }    
        return null; 
    }
   

    protected abstract void receiveLoop() throws Exception;
       
       
    public void run()
    {
        int retry = 0;       
        _close = false;
        _api.log().debug("SerialComm", "Activating...");
        while (true) 
        {
            _state = Channel.State.STARTING;
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
                _api.log().debug("SerialComm", "Initialize on "+_portName);
                _serialPort = connect();
                if (_serialPort == null)
                    continue; 
                _state = Channel.State.RUNNING;
                receiveLoop();
            }
            catch(NoSuchPortException e) {
                _api.log().error("SerialComm", "Serial port " + _portName + " not found");
                e.printStackTrace(System.out);
            }
            catch(Exception e) {   
                e.printStackTrace(System.out); 
                //    close(); FIXME: callback to channel? 
            }  
                   
            if (_close) {
                _state = Channel.State.OFF;
                _api.log().debug("SerialComm", "Channel closed");
                return;
            }
           
            retry++;      
        }
        _api.log().warn("SerialComm", "Couldn't connect device on'"+_portName+"' - giving up");
        _state = Channel.State.FAILED;
     }


}

