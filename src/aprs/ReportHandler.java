/* 
 * Copyright (C) 2014-2025 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
 
 
package no.polaric.aprsd;
import no.polaric.aprsd.point.*;
import java.util.*;


/**
 * Handle APRS packets and reports. To be implemented by
 * default handler and/or plugins. 
 * FIXME: Consider using a more flexible asynchronous event handler pattern. 
 */
 
public interface ReportHandler
{
    /**
     * Information typically associated with an APRS position report.
     */
    public static class PosData {
       public LatLng pos;
       public int ambiguity  = 0; 
       public int course = -1;
       public int speed = -1;
       public char symbol, symtab;
       public long altitude = -1; 
       
       public PosData () {}
       public PosData (LatLng p, char sym, char stab)
          {pos=p; symbol=sym; symtab=stab; }
       public PosData (LatLng p, int crs, int sp, char sym, char stab)
          {pos=p; course=crs; speed=sp; symbol=sym; symtab=stab; }   
    }

   
   
    /**
     * Handle APRS position report.
     * @param s Source channel.
     * @param sender Callsign of sender.
     * @param ts Timestamp
     * @param newpos The updated position
     * @param descr Comment field
     * @param path Path (digipeaters) of containing packet. 
     */
    public void handlePosReport(Source s, String sender, Date ts, PosData newpos,  
            String descr, String path);
    
    
    /**
     * Handle APRS status report.
     * @param s Source channel.
     * @param ts Timestamp.
     * @param msg Status message.
     */
    public void handleStatus(Source s, Date ts, String msg);
       
       
    /**
     * Handle APRS message.
     * @param s Source channel.
     * @param ts Timestamp
     * @param src Callsign of sender.
     * @param dest Callsign of destination.
     * @param msg Message text.
     */
    public void handleMessage(Source s, Date ts, String src, String dest, String msg);
    
    
    
    /**
     * Handle raw APRS packet. This is called in addition to specific reports to allow
     * handlers to log packets for instance. 
     * @param p Packet
     */
    public void handlePacket(AprsPacket p);


    /**
     * Dummy Aprs handler class. Does nothing. 
     */
    public class Dummy implements ReportHandler 
    {
       public void handlePosReport(Source s, String sender, Date ts, PosData newpos,  
               String descr, String pathinfo) {};
       public void handleStatus(Source s, Date ts, String msg) {};                               
       public void handleMessage(Source s, Date ts, String src, String dest, String msg) {}
       public void handlePacket(AprsPacket p) {}
    }     
}
 
