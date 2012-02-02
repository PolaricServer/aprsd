/* 
 * Copyright (C) 2012 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
 * TNC2 channel. For devices in TNC2 compatible mode.
 */
 
public class Tnc2Channel extends TncChannel implements Runnable
{

    transient private  String         _pathCmd, _unproto = Main.toaddr;; 
    transient private  boolean        _close = false;
    transient private  boolean        _noBreak = false; 
    transient private  BufferedReader _in; 
    transient private  OutputStream   _ostream;    
    transient private  Semaphore      _sem = new Semaphore(1, true);
   
    private static final String _initfile = Main.confdir+"/init.tnc";

    
    
    public Tnc2Channel(Properties config) 
    {
       super(config); 
      _pathCmd = config.getProperty("tncchannel.pathcommand", "UNPROTO").trim();  
      _noBreak = config.getProperty("tncchannel.nobreak", "false").trim().matches("true|yes");
    }
 
 
 
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
             sendCommand(_out, _pathCmd + " " + unproto+"\r");
             _out.print("k\r");
             _out.flush();
             _sem.release();
             Thread.sleep(100);
          }
          catch (Exception e) {}
        
       _log.log(" [>" + this.getShortDescr() + "] " + p);
       
       if (p.thirdparty || (p.from != null && !p.from.equals(_myCall))) {
           _log.add("*** TX path = '"+unproto+"'"); 
           _out.print(
             "}" + p.from + ">" + p.to +
                ((p.via_orig != null && p.via_orig.length() > 0) ? ","+p.via_orig : "") +
                ":" + p.report + "\r" );
       }
       else 
           _out.print(p.report+"\r");
       _out.flush();    
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
      Thread.sleep(150);
      while (_in.ready()) 
         line += (char) _in.read();
      
      if (line.contains("cmd:"))
         System.out.println("*** TNC in command mode");
      else
         System.out.println("*** Warning: Cannot get command prompt from TNC");
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
    * Init the TNC - set it to converse mode
    */
    private synchronized void initTnc()
    {
        try {
          getCommandPrompt();
          _out.print("\r");
          Thread.sleep(200); 
          initCommands(_initfile, _out);
          _out.print("k\r"); 
          _out.flush();
          Thread.sleep(200);
        }
        catch (Exception e) 
           { System.out.println("*** Error: initTnc: "+e); }
    }
    
  
   
    /**
     * Send a command to TNC.
     */
    protected void sendCommand(PrintWriter out, String line) throws Exception
    {
         out.print(line+"\r");
         out.flush(); 
         Thread.sleep(300);
         while (_in.ready()) 
             _in.read();
    }
   
       
        
    
    /**
     * Close down the channel. 
     */
    @Override public void close() 
    { 
       System.out.println("*** Closing TNC channel");
       try {  
         _close = true;
         Thread.sleep(3000);
         getCommandPrompt();
         if (_out != null) _out.close(); 
         if (_in != null)  _in.close(); 
       }
       catch (Exception e) {}
       if (_serialPort != null) _serialPort.close(); 
    }
    
    
    
    @Override protected void receiveLoop() throws Exception
    {
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

}

