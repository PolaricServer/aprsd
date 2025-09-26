/* 
 * Copyright (C) 2020-2025 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import no.arctic.core.*;
import no.polaric.aprsd.*;
import java.io.*;
import java.util.*;
import gnu.io.*;
import java.util.concurrent.Semaphore;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonSubTypes.*;

/**
 * KISS over a TCP stream
 */
 
public class TcpKissChannel extends TcpChannel
{
    private   InputStream  _istream;
    private   OutputStream _ostream;
    private   Kiss         _kiss;
    private   int          _kissport;
    protected Logfile      _log; 
    
    
    
    public TcpKissChannel(AprsServerConfig conf, String id) 
       { super(conf, id); }
        
        
    
    @Override public void activate(AprsServerConfig a) {
       super.activate(_conf);
       _kissport = _conf.getIntProperty("channel."+getIdent()+".kissport", 0);
       _log = new Logfile(_conf, getIdent(), "rf.log");
    }
    
    
           
    /* 
     * Information about config to be exchanged in REST API
     */
    @JsonTypeName("TCPKISS")
    public static class  JsConfig extends Channel.JsConfig {
        public long heard, heardpackets, duplicates, sentpackets; 
        public String host;  
        public int port, kissport;
    }
       
       
    public JsConfig getJsConfig() {
        var cnf = new JsConfig();
        cnf.heard = nHeard();
        cnf.heardpackets = nHeardPackets(); 
        cnf.duplicates = nDuplicates(); 
        cnf.sentpackets = nSentPackets();
        
        cnf.type = "TCPKISS";
        cnf.kissport = _conf.getIntProperty("channel."+getIdent()+".kissport", 0);
        cnf.host=_conf.getProperty("channel."+getIdent()+".host", "localhost");
        cnf.port=_conf.getIntProperty("channel."+getIdent()+".port", 21);
        return cnf; 
    }
        

    public void setJsConfig(Channel.JsConfig ccnf) {
        var cnf = (JsConfig) ccnf;
        var props = _conf.config();
        props.setProperty("channel."+getIdent()+".host", cnf.host);
        props.setProperty("channel."+getIdent()+".port", ""+cnf.port);
        props.setProperty("channel."+getIdent()+".kissport", ""+cnf.kissport);
    }
    
    
   
   /* Return true if this is a RF channel */
    @Override public boolean isRf() {
        return true; 
    }
    
    
    
    /**
     * Close down the channel. 
     */
    @Override protected void _close()
    {
       try { 
          if (_istream != null) _istream.close(); 
          if (_ostream != null) _ostream.close();
          if (_comm != null) _comm.deActivate(); 
          Thread.sleep(500);
       } catch (Exception e) {}
    }
  
  
   
    public synchronized boolean sendPacket(AprsPacket p)
    {        
        if (!isReady() || !canSend)
            return false; 
        _log.log(" [>" + this.getIdent() + "] " + p);
        try {
           if (_kiss!=null) {
              _kiss.sendPacket(p);
              _sent++;
              return true; 
           }
        }
        catch (IOException e)
           {  _conf.log().error("TcpKissChannel", chId()+"KissTncChannel.sendPacket: "+e); }
        return false; 
    }
  
  
  
    @Override protected void receiveLoop() throws Exception
    {
         _istream = _comm.getInputStream();
         _ostream = _comm.getOutputStream();
         _kiss = new Kiss(_istream, _ostream, _kissport); 
        
         while (!_close) 
         {
            try { 
                AprsPacket p = _kiss.receivePacket();
                receivePacket(p, false);
            }
            catch (Kiss.Timeout e) {}
            Thread.yield();
         }
    }
    
    
    @Override protected void regHeard(AprsPacket p) 
    {
        _heard.put(p.from, new Heard(new Date(), p.via));
        /* FIXME: Check for TCPxx in third party packets and consider registering 
         * more than one path instance */
    }
 
       
}
