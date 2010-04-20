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
import java.util.*;
import gnu.io.*;
  /* Similar to javax.comm */


public class TncChannel extends Channel implements Runnable
{
    private  String _portName; 
    private  int _baud;
    private  BufferedReader _in;
    private  SerialPort _serialPort;
    private String  _myCall; /* Move this ? */
    private boolean _close = false;
    
    
    public TncChannel(Properties config) 
    {
        _myCall = config.getProperty("tncchannel.mycall", "").trim();
        if (_myCall.length() > 0)
           _myCall = config.getProperty("igate.mycall", "N0CALL").trim();
           
        _portName = config.getProperty("tncchannel.port", "localhost").trim();
        _baud= Integer.parseInt(config.getProperty("tncchannel.baud", "9600").trim());
        
    }
 
 
 
    /**
     * The generic sendPacket method is unsupported on generic TNCs. 
     * We cannot set addresses per packet. If callsign is not null and match
     * TNC callsign, or explicitly requested, we use third party format. 
     */ 
    public void sendPacket(Packet p)
    {
       if (p.thirdparty || (p.to != null && !p.to.equals(_myCall)))
           _out.print(
             "}" + p.from + ">" + p.to +
                ((p.via != null && p.via.length() > 0) ? ","+p.via : "") + 
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
            System.out.println("*** Error: Port "+ _portName + " is currently in use");
        else
        {
            CommPort commPort = portIdentifier.open(this.getClass().getName(), 2000);
            
            if ( commPort instanceof SerialPort )
            {
                SerialPort serialPort = (SerialPort) commPort;
                serialPort.setSerialPortParams(_baud, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
                serialPort.enableReceiveTimeout(5000);
                return (SerialPort) commPort;
            }
            else
                System.out.println("*** Error: Port " + _portName + " is not a serial port.");
        }    
        return null; 
    }
   
   
  
   
   /**
    * Init the TNC - set it to converse mode
    */
    private void initTnc()
    {
        System.out.println("*** Init TNC");
        try {
          Thread.sleep(100);
          OutputStream o = _serialPort.getOutputStream();
          o.write(3);
          o.flush();
          Thread.sleep(500); 
          _out.print("k\r"); 
          _out.flush();
        }
        catch (Exception e) 
           { System.out.println("*** Error: initTnc: "+e); }
    }
    
    
    
    private void restoreTnc()
    {
       try {
          OutputStream o = _serialPort.getOutputStream();
          o.write(3);
          o.flush();
          Thread.sleep(500);
       }
       catch (Exception e) 
          {  System.out.println("*** Error: restoreTnc: "+e); }
    }
    
    
    
    public void close() 
    { 
       System.out.println("*** Closing TNC channel");
       try {
         restoreTnc(); 
         _close = true;
         Thread.sleep(5000);
         if (_out != null) _out.close(); 
         if (_in != null) _in.close(); 
       }
       catch (Exception e) {}
       if (_serialPort != null) _serialPort.close(); 
    }
       
       
    
    public void run()
    {
           try {
               _serialPort = connect();
               if (_serialPort == null)
                   return; /* Throw exception instead? Move to constructor? */
                   
               _in = new BufferedReader(new InputStreamReader(_serialPort.getInputStream(), _encoding));
               _out = new PrintWriter(new OutputStreamWriter(_serialPort.getOutputStream(), _encoding));
               initTnc();
               while (true) 
               {
                   try {
                      String inp = _in.readLine(); 
                      System.out.println(new Date() + ":  "+inp);
                      receivePacket(inp);
                   }
                   catch (java.io.IOException e) {
                      if (_close) { System.err.println("*** Stopping TNC thread"); return; } 
                      else continue;             
                   }
               }
           }

           catch(Exception e)
           {   
                e.printStackTrace(System.out); 
           }  
     }



}

