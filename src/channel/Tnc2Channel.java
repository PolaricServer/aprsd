/* 
 * Copyright (C) 2020 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
 *
 * This program is free software: you can redistribute it and/or modify
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
import gnu.io.*;
import java.util.concurrent.Semaphore;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonSubTypes.*;


/**
 * TNC2 channel. For devices in TNC2 compatible mode.
 */
 
public class Tnc2Channel extends TncChannel
{

    private  String         _pathCmd, _unproto; 
    private  boolean        _noBreak = false; 
    private  BufferedReader _in; 
    private  OutputStream   _ostream;    
    private  Semaphore      _sem = new Semaphore(1, true);
   
    private static final String _initfile = System.getProperties().getProperty("confdir", ".")+"/init.tnc";
    

 
    public Tnc2Channel(AprsServerConfig conf, String id) 
       { super(conf, id); }
       
       
 
    @Override public void activate(AprsServerConfig a) {
       super.activate(a);
       _unproto = _conf.getToAddr();  
       _pathCmd = _conf.getProperty("channel."+getIdent()+".pathcommand", "UNPROTO");  
       _noBreak = _conf.getBoolProperty("channel."+getIdent()+".nobreak", false);
    }
    
    
    
    /* 
     * Information about config to be exchanged in REST API
     */
    @JsonTypeName("TNC2")
    public static class  JsConfig extends Channel.JsConfig {
        public long heard, heardpackets, duplicates, sentpackets; 
        public String host, port; 
        public int baud, kissport;
    }
       
    
    public JsConfig getJsConfig() {
        var cnf = new JsConfig();
        cnf.heard = nHeard();
        cnf.heardpackets = nHeardPackets(); 
        cnf.duplicates = nDuplicates(); 
        cnf.sentpackets = nSentPackets();
        
        cnf.type = "TNC2";
        cnf.baud = _conf.getIntProperty("channel."+getIdent()+".baud", 9600);
        cnf.port = _conf.getProperty("channel."+getIdent()+".port", "/dev/ttyS0");
        return cnf;
    }
    
    
    public void setJsConfig(Channel.JsConfig ccnf) {
        var cnf = (JsConfig) ccnf;
        var props = _conf.config();
        props.setProperty("channel."+getIdent()+".port", ""+cnf.port);
        props.setProperty("channel."+getIdent()+".baud", ""+cnf.baud);
    }
    
 
    /**
     * Send packet on RF. 
     * The generic sendPacket method is not fully supported on TNC2 interface. 
     * If receiver callsign is not null and do not match TNC callsign, we use 
     * third party format.
     *
     * However, we try to change UNPROTO path, if requested but note that
     * the method for doing that is not the most reliable and efficient:
     * Break out of converse mode and try to send command to TNC.
     */ 
     
    public synchronized boolean sendPacket(AprsPacket p)
    {
       if (!isReady() || !canSend)
            return false; 
    
       if (_out == null)
          return false;
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
        
       _log.log(" [>" + this.getIdent() + "] " + p);
       
       if (p.from != null && !p.from.equals(_myCall)) {
           _log.add("*** Force third party format. TX path = '"+unproto+"'"); 
           _out.print(thirdPartyReport(p));
       }
       else 
           _out.print(p.report+"\r");
       _out.flush();    
       _sent++;
       return true;
    }
   
    
    
    
   
   /**
    * Send a break or ctrl-c to TNC to return it to command mode. 
    */
   private void getCommandPrompt() throws Exception
   {     
      boolean reset_retry = false; 
      while (true) {
         String line = "";
         if (_noBreak) {
            _ostream.write(3);
            _ostream.flush();
         }
         else
            _serial.sendBreak(3);
         
         if (reset_retry) {
            Thread.sleep(2000); 
            _out.print("RESET\r");
            _out.flush(); 
         }
         
         Thread.sleep(50);
         _out.print("\r");
         _out.flush();
         Thread.sleep(150);
         while (_in.ready()) 
            line += (char) _in.read();
      
         if (line.contains("cmd:"))
             _conf.log().debug("TncChannel", chId()+"TNC in command mode");
         else 
            if (reset_retry) {
                 _conf.log().error("TncChannel", chId()+"Cannot get command prompt from TNC. Giving up"); 
                reset_retry = false;
            }
            else {
                 _conf.log().warn("TncChannel", chId()+"Cannot get command prompt from TNC. Trying a RESET"); 
                reset_retry = true; 
                continue; 
            }
         return;
      }        
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
           {  _conf.log().error("TncChannel", chId()+"initTnc: "+e); }
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
       _conf.log().info("Tnc2Channel", chId()+"Closing channel..");
       try {  
         _serial.deActivate(); 
         Thread.sleep(3000);
         getCommandPrompt();
         if (_out != null) _out.close(); 
         if (_in != null)  _in.close(); 
       }
       catch (Exception e) {}
    }
    
    
    
    @Override protected void receiveLoop() throws Exception
    {
        _in = new BufferedReader(new InputStreamReader(_serial.getInputStream(), _rx_encoding));
        _out = new PrintWriter(new OutputStreamWriter(_serial.getOutputStream(), _tx_encoding));
        _ostream = _serial.getOutputStream();
        
        initTnc();
        while (_serial.running()) 
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

