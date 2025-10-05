/* 
 * Copyright (C) 2020-2025 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
 *
 *  This program is free software: you can redistribute it and/or modify
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
import java.util.*;
import com.fazecast.jSerialComm.*;
import java.util.concurrent.Semaphore;




/**
 * Serial communication device
 */
 
public class SerialComm extends CommDevice implements Runnable
{
    private   String     _portName; 
    private   int        _baud;
    private   int        _max_retry;
    private   long       _retry_time;  
    private   SerialPort _serialPort;

    
 
    public SerialComm(AprsServerConfig conf, String id, String pname, int bd, int retr, long rtime) 
    {
        super(conf, id);
        _portName = pname;
        _baud = bd;
        _max_retry = retr;
        _retry_time = rtime;
    }
  
    
    public OutputStream getOutputStream() throws IOException
        { return _serialPort.getOutputStream(); }
        
        
    public InputStream getInputStream() throws IOException
        { return _serialPort.getInputStream(); }
    

    public void sendBreak(int n)
        { _serialPort.setBreak(); try { Thread.sleep(n); } catch (Exception e) {} _serialPort.clearBreak(); }
        
        
        
    /**
     * Setup the serial port
     */
    private SerialPort connect () throws Exception
    {
        SerialPort serialPort = com.fazecast.jSerialComm.SerialPort.getCommPort(_portName);
        if (serialPort == null) {
            _conf.log().error("SerialComm", "Port " + _portName + " not found");
            return null;
        }
        
        serialPort.setBaudRate(_baud);
        serialPort.setNumDataBits(8);
        serialPort.setNumStopBits(com.fazecast.jSerialComm.SerialPort.ONE_STOP_BIT);
        serialPort.setParity(com.fazecast.jSerialComm.SerialPort.NO_PARITY);
        serialPort.setComPortTimeouts(com.fazecast.jSerialComm.SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 1000, 0);
        
        if (!serialPort.openPort()) {
            _conf.log().error("SerialComm", "Port "+ _portName + " could not be opened (may be in use)");
            return null;
        }
        
        return serialPort;
    }
   
   
   
   
    public void run()
    {
        int retry = 0;       
        _conf.log().debug("SerialComm", "Activating...");      
        try { Thread.sleep(500); } catch (Exception e) {}
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
                _conf.log().debug("SerialComm", "Initialize on "+_portName);
                _serialPort = connect();
                if (_serialPort == null)
                    continue; 
                _state = Channel.State.RUNNING;
                if (_worker != null) 
                    _worker.worker();
            }
            catch(Exception e) {   
                e.printStackTrace(System.out); 
                //    close(); FIXME: callback to channel? 
            }  
                   
            if (!running()) {
                _state = Channel.State.OFF;
                _conf.log().debug("SerialComm", "Channel closed");
                return;
            }
           
            retry++;      
        }
        _conf.log().warn("SerialComm", "Couldn't connect device on'"+_portName+"' - giving up");
        _state = Channel.State.FAILED;
     }


}

