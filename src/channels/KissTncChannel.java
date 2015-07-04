/* 
 * Copyright (C) 2015 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
 * TNC channel. For devices in KISS compatible mode.
 */
 
public class KissTncChannel extends TncChannel
{

    protected InputStream _istream; 
    protected OutputStream _ostream; 
  
    Kiss _kiss; 

    
    
    public KissTncChannel(ServerAPI api, String id) 
    {
       super(api, id);
       Properties config = api.getConfig();
    }
    
    
    

    /**
     * Send packet on RF. 
     */ 
    public synchronized void sendPacket(AprsPacket p)
    {
        _log.log(" [>" + this.getShortDescr() + "] " + p);
        try {
           _kiss.sendPacket(p);
           _sent++;
        }
        catch (IOException e)
           { logNote("Error: KissTncChannel.sendPacket: "+e); }
    }
   
   
   
    /**
     * Close down the channel. 
     */
    @Override public void close() 
    { 
       logNote("Closing TNC channel");
       try {  
         _close = true;
         Thread.sleep(3000);
         if (_ostream != null) _ostream.close(); 
         if (_istream != null) _istream.close(); 
       }
       catch (Exception e) {}
       if (_serialPort != null) _serialPort.close(); 
    }
    
    
    
    @Override protected void receiveLoop() throws Exception
    {
        _ostream = _serialPort.getOutputStream();
        _istream = _serialPort.getInputStream(); 
        _kiss = new Kiss(_istream, _ostream); 
        
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
    
       
}

