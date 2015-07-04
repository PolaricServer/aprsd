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
 * Implementation of the KISS protocol. For devices in KISS compatible mode.
 */
 
public class Kiss
{
   protected static final byte FTYPE_UI  = (byte) 0x03; 
   protected static final byte PID_APRS  = (byte) 0xF0;
   protected static final byte FLAG_LAST = (byte) 0x01;
   protected static final byte FLAG_DIGI = (byte) 0x80;
   protected static final byte ASCII_SPC = (byte) 0x20;
   protected static final byte FEND  = (byte) 0xC0;
   protected static final byte FESC  = (byte) 0xDB;
   protected static final byte TFEND = (byte) 0xDC;
   protected static final byte TFESC = (byte) 0xDD;
   
   protected InputStream _istream; 
   protected OutputStream _ostream;
  
   
   /** Address field. */
   protected static class Addr {
      String addr;
      byte flags; 
      public boolean last() 
        { return (flags & FLAG_LAST) != 0; }
      public boolean digipeated()
        { return (flags & FLAG_DIGI) != 0; }
      public Addr(String a, byte f)
        { addr=a; flags=f; }
   }
 
 
   /** Exception to signal the end of a frame. */
   protected static class Frame_End extends Throwable
   {}
 
   /** Exception to signal a timeout. */
   protected static class Timeout extends Throwable
   {}
    
    
   public Kiss(InputStream is, OutputStream os)
   {
       _istream = is; 
       _ostream = os;
   }
   

    /**
     * Send packet. 
     */ 
    public synchronized void sendPacket(AprsPacket p)
       throws IOException
    {
         /* Start of frame. KISS command = data */
         sendFend(); 
         sendByte((byte) 0); 
       
         /* AX.25 UI Header */
         String[] digis = new String[0]; 
         if (p.via.length() > 0) 
             digis = p.via.split(","); 

         encodeAddr(p.to, false);
         encodeAddr(p.from, digis.length==0);
         int n = 0;
         for (String d : digis)
            encodeAddr(d, ++n >= digis.length); 
         sendByte(FTYPE_UI); 
         sendByte(PID_APRS);

         /* Messsage */
         for (byte b : p.report.getBytes())
             sendByte(b);
        
         /* End of frame */
         sendFend();
    }
   
   
    
    /**
     * Receive packet. 
     */
    public AprsPacket receivePacket() throws Timeout
    {
        AprsPacket p = new AprsPacket();
        p.report = p.via = ""; 
        boolean complete = false;
     
        while (true) {
           try {
               if (receiveByte() != 0)  
                  continue;
               Addr a = decodeAddr();
               p.to = a.addr; 
               a = decodeAddr();
               p.from = a.addr;
               while (!a.last()) {
                  a = decodeAddr();
                  p.via = p.via + a.addr + (a.digipeated() ? "*" : "");
                  if (!a.last()) 
                     p.via += ",";
               } 

               if (receiveByte() == FTYPE_UI && receiveByte() == PID_APRS)
                  complete = true;
               while (true)
                  p.report += (char) receiveByte(); 
           
           }
           catch (Frame_End e) {
              if (complete) 
                 return p;        
           }
           catch (Exception e) { }
        }
    }
    

   
   /**
    * Encode AX25 address field (callsign).
    */
    private void encodeAddr(String addr, boolean last) throws IOException
    {
        byte ssid = 0;
        if (addr.contains("-")) {
            ssid = (byte) Integer.parseInt(addr.substring(addr.indexOf('-')+1));
            addr = addr.substring(0,addr.indexOf('-')); 
        }
        
        byte[] b = addr.getBytes();
        byte flags = (last ? FLAG_LAST : 0);
        int i=6;
        for (byte c : b) {
           i--;
           sendByte((byte) (c << 1));
        }
        for (int j=0; j<i; j++)
           sendByte((byte) (ASCII_SPC << 1));
        sendByte((byte)(((ssid & 0x0F) << 1) | (flags & 0x81) | 0x60) );
    }


   /**
    * Decode AX25 address field (callsign).
    */
    private Addr decodeAddr() throws IOException, Frame_End, Timeout
    {
        byte x;
        String result = "";
        for (int i=0; i<6; i++)
        {
           x = (byte)((receiveByte() & 0xfe) >>> 1);
           if (x != ASCII_SPC)
              result += (char) x;  
        }
        x = receiveByte();
        byte ssid = (byte) ((x & 0x1E) >>> 1); 
        byte flags = (byte) (x & 0x81);
        return new Addr(result + (ssid>0 ? "-"+ssid : ""), flags );
    }
    
    

   /**
    * Send byte according to the SLIP/KISS protocol.
    */
    private void sendByte(byte x) throws IOException
    {
       if (x==FEND)
          { _ostream.write(FESC); _ostream.write(TFEND); }
       else if (x==FESC)
          { _ostream.write(FESC); _ostream.write(TFESC); }
       else
           _ostream.write(x);
    }
    
    
    
   /**
    * Open or close frame according to the SLIP/KISS protocol.
    * i.e. a FEND character is sent..
    */
    private void sendFend() throws IOException
       { _ostream.write(FEND); }
    
    
   /**
    * Receive byte according to the SLIP/KISS protocol.
    * Throws an exception Frame_End when receiving a FEND. 
    */
    private byte receiveByte() throws IOException, Frame_End, Timeout
    {
       boolean escaped = true;
       while (true) {
          int x = _istream.read();
          if (x == -1)
             throw new Timeout();    
          if ((byte) x == FEND)
             throw new Frame_End();
          
          if ((byte) x == FESC) 
             escaped = true;
          else  {
             if (escaped) {
                if ((byte) x==TFEND) 
                   return FEND; 
                else if ((byte) x==TFESC)
                   return FESC;
                else
                   escaped = false;
             } 
             return (byte) x;  
         }
      }   
   }
       
}

