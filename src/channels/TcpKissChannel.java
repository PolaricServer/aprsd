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
 * KISS over a TCP stream
 */
 
public class TcpKissChannel extends TcpChannel
{
    private   InputStream  _istream;
    private   OutputStream _ostream;
    private   Kiss         _kiss;
    private   int          _kissport;
    protected Logfile      _log; 
    
    
    
    public TcpKissChannel(ServerAPI api, String id) 
       { super(api, id); }
        
        
    
    @Override public void activate(ServerAPI a) {
       super.activate(_api);
       _kissport = _api.getIntProperty("channel."+getIdent()+".kissport", 0);
       _log = new Logfile(_api, getIdent(), "rf.log");
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
  
  
   
    public synchronized void sendPacket(AprsPacket p)
    {
        _log.log(" [>" + this.getShortDescr() + "] " + p);
        try {
           if (_kiss!=null) {
              _kiss.sendPacket(p);
              _sent++;
           }
        }
        catch (IOException e)
           {  _api.log().error("TcpKissChannel", chId()+"KissTncChannel.sendPacket: "+e); }
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
 
    
    @Override public String getShortDescr()
       { return "rf"+_chno; }
       
}
