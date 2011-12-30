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
 
public class TncChannel extends Channel implements Runnable
{
    private  String  _portName; 
    private  int     _baud;
    private  String  _myCall; /* Move this ? */
    private  String  _pathCmd, _unproto = Main.toaddr; 
    
    private  boolean        _close = false;
    private  boolean        _noBreak = false; 
    private  int            _max_retry;
    private  long           _retry_time;   
    private  BufferedReader _in;
    private  OutputStream   _ostream;
    private  SerialPort     _serialPort;
    private  Semaphore      _sem = new Semaphore(1, true);
    
    private static final String _initfile = Main.confdir+"/init.tnc";
    
 
    public TncChannel(Properties config) 
    {
        _myCall = config.getProperty("tncchannel.mycall", "").trim().toUpperCase();
        if (_myCall.length() == 0)
           _myCall = config.getProperty("default.mycall", "NOCALL").trim().toUpperCase();  
        
        _max_retry = Integer.parseInt(config.getProperty("tncchannel.retry", "0").trim());
        _retry_time = Long.parseLong(config.getProperty("tncchannel.retry.time", "30").trim()) * 60 * 1000; 
        _pathCmd = config.getProperty("tncchannel.pathcommand", "UNPROTO").trim();
        _portName = config.getProperty("tncchannel.port", "/dev/ttyS0").trim();
        _baud= Integer.parseInt(config.getProperty("tncchannel.baud", "9600").trim());
        _noBreak = config.getProperty("tncchannel.nobreak", "false").trim().matches("true|yes");
        // FIXME: set gnu.io.rxtx.SerialPorts property here instead of in startup script
    }
 
 
 
    @Override protected void regHeard(Packet p) 
    {
        _heard.put(p.from, new Heard(new Date(), p.via));
        /* FIXME: Check for TCPxx in third party packets and consider registering 
         * more than one path instance */
    }
 
    
    @Override public String getShortDescr()
       { return "RF"; }
 
    /**
     * Send packet on RF. 
     * The generic sendPacket method is not fully supported on TNC2 interface. 
     * If receiver callsign is not null and match TNC callsign, or explicitly 
     * requested, we use third party format.
     *
     * However, we try to change UNPROTO path, if requested but note that
     * the method for doing that is not the most reliable and efficient:
     * Break out of converse mode and try to send command to TNC.
     */ 
     
    public synchronized void sendPacket(Packet p)
    {
       if (_out == null)
          return;
       String unproto = p.to + (p.via != null && p.via.length()>0 ? " VIA "+p.via : "");
       if (!_unproto.equals(unproto))
          try {
             _sem.acquire();
             getCommandPrompt();
             _unproto = unproto;
             sendCommand(_out, _pathCmd + " " + unproto);
             sendCommand(_out, "k");
             _sem.release();
             Thread.sleep(200);
          }
          catch (Exception e) {}
       
       System.out.println("*** Send packet");
       if (p.thirdparty || (p.from != null && !p.from.equals(_myCall)))
           _out.print(
             "}" + p.from + ">" + p.to +
                ((p.via_orig != null && p.via_orig.length() > 0) ? ","+p.via_orig : "") +
                ":" + p.report + "\r" );
       else 
           _out.print(p.report+"\r");
       _out.flush();    
    }
   
    

    /**
     * Setup the serial port
     */
    private SerialPort connect () throws Exception
    {
        System.out.println("Serial port: "+_portName);
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
   
   
   
   /**
    * Send init commands to TNC. 
    * Get commands from init.tnc file and add a MYCALL command at the end. 
    */
   private void initCommands(String file, PrintWriter out) throws Exception
    {
        BufferedReader rd = new BufferedReader(new FileReader(file));
        while (rd.ready())
        {
            String line = rd.readLine();
            if (!line.startsWith("#")) 
                sendCommand(_out, line);
            sendCommand(_out,"MYCALL "+_myCall);
        }    
    }   
   
   
   
   /**
    * Send a command to TNC.
    */
   private void sendCommand(PrintWriter out, String line) throws Exception
   {
         out.print(line+"\r");
         out.flush(); 
         Thread.sleep(300);
         while (_in.ready()) 
             _in.read();
   }
   
   
   
   /**
    * Send a break or ctrl-c to TNC to return it to command mode. 
    */
   private void getCommandPrompt() throws Exception
   {     
      String line = "";
      if (_noBreak) {
         _ostream.write(3);
         _ostream.flush();
      }
      else
         _serialPort.sendBreak(3);
      Thread.sleep(50);
      _out.print("\r");
      _out.flush();
      Thread.sleep(200);
      while (_in.ready()) 
         line += (char) _in.read();
      
      if (line.contains("cmd:"))
         System.out.println("*** TNC in command mode");
      else
         System.out.println("*** Warning: Cannot get command prompt from TNC");
   }
   
   
   
   /**
    * Init the TNC - set it to converse mode
    */
    private synchronized void initTnc()
    {
        try {
          getCommandPrompt();
          _out.print("\r");
          Thread.sleep(200); 
          initCommands(_initfile, _out);
          sendCommand(_out, "k"); 
        }
        catch (Exception e) 
           { System.out.println("*** Error: initTnc: "+e); }
    }
    
    
    
    /**
     * Close down the channel. 
     */
    public void close() 
    { 
       System.out.println("*** Closing TNC channel");
       try {  
         _close = true;
          Thread.sleep(3000);
         getCommandPrompt();
         if (_out != null) _out.close(); 
         if (_in != null) _in.close(); 
       }
       catch (Exception e) {}
       if (_serialPort != null) _serialPort.close(); 
    }
       
       
       
       
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
                   
               _in = new BufferedReader(new InputStreamReader(_serialPort.getInputStream(), _rx_encoding));
               _out = new PrintWriter(new OutputStreamWriter(_serialPort.getOutputStream(), _tx_encoding));
               _ostream = _serialPort.getOutputStream();
               
               initTnc();
               while (!_close) 
               {
                   _sem.acquire(); 
                   try {
                      String inp = _in.readLine();
                      receivePacket(inp, false);
                   }
                   catch (java.io.IOException e) {}   
                   _sem.release();
                   Thread.yield();
               }
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

