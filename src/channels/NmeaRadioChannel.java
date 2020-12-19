 
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
import java.net.*;
import java.util.*;



/**
 * Internet channel. Connect to APRS-IS server. 
 */
public class NmeaRadioChannel extends Channel
{
    private  BufferedReader _rder = null;
    private  CommDevice _comm; 
    private  int        _chno;
    
    private static int _next_chno = 0;
            
    
    
    public NmeaRadioChannel(ServerAPI api, String id) { 
        _init(api, "channel", id);    
        _chno = _next_chno;
        _next_chno++; 
    }
       
    
    @Override public void activate(ServerAPI a) {
        String id=getIdent();
        String type = _api.getProperty("channel."+id+".type", "");
        int retr = _api.getIntProperty("channel."+id+".retry", 0);
        long rtime = Long.parseLong(_api.getProperty("channel."+id+".retry.time", "30")) * 60 * 1000; 
        
        if ("NMEA".equals(type)) {
            String port = _api.getProperty("channel."+id+".port", "/dev/ttyS0");
            int baud = _api.getIntProperty("channel."+id+".baud", 9600);
            _comm = new SerialComm(_api, id, port, baud, retr, rtime ); 
        }
        else if ("TCPNMEA".equals(type)) {
            String host = _api.getProperty("channel."+id+".host", "localhost");
            int port = _api.getIntProperty("channel."+id+".port", 1000);
            _comm = new SerialComm(_api, id, host, port, retr, rtime ); 
        }
        else return;
        _comm.activate( ()->receiveLoop(), ()->{} );
    }
 
 
    public void deActivate() {}
 

    @Override public String getShortDescr()
       { return "nm"+_chno; }
       
        
    
    
    protected void _close()
    {
       try { 
          if (_rder != null) _rder.close(); 
          if (_comm != null) _comm.deActivate(); 
          Thread.sleep(500);
       } catch (Exception e) {}
    }
    

    /**
     * Receive packet. 
     * FIXME: There is an almost identical function in GpsPosition.java
     */
    private void receivePacket (String p)
    {
        if (p.charAt(0) != '$')
            return;

        /* Checksum (optional) */
        int i, checksum = 0;
        for (i=1; i < p.length() && p.charAt(i) !='*'; i++) 
            checksum ^= p.charAt(i);
        if (p.charAt(i) == '*') {
            int c_checksum = Integer.parseInt(p.substring(i+1, p.length()), 16);
            if (c_checksum != checksum) 
               return;
        } 
        String[] tok = p.split(",");
        if ( "$PKNDS".equals(tok[0]))
            do_pknds(tok); 
             
        else if ("$PNIGPS".equals(tok[0]))
            do_pnigps(tok);
        
        else if ("$PRAVE".equals(tok[0]))
            do_prave(tok);    

    }
    
    
    
    private void do_pknds(String[] tok)
    {
        /* dPMR radios */
        /* TBD */
    }
    
    
    
    private void do_pnigps(String[] tok)
    {
        /* AVL radios: $PNIGPS,555550,00,,9999.6155,N,09999.9243,E,,,048.4,,,,,,*08 
           555550=Radio ID, 9999.6155=Latitude, 09999.9243=Longitude */
        /* TBD */
    }
    
    
    
    private void do_prave(String[] tok)
    {
        /* http://ravtrack.com/GPStracking/the-prave-message-format/ */
        /* TBD */
    }
    
    
    
    
    protected void receiveLoop() throws Exception
    {    
        _rder = new BufferedReader(new InputStreamReader(_comm.getInputStream()));

        while (_comm.running()) 
        {
            String inp = _rder.readLine(); 
            if (inp != null) 
                receivePacket(inp);
            else {   
                _api.log().info("InetChannel", chId()+"Disconnected from device");
                break; 
            }              
        }
    }
    

}

