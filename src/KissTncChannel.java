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
 * TNC channel. For devices in KISS compatible mode.
 */
 
public class KissTncChannel extends TncChannel
{
   protected static final byte FTYPE_UI = 0; // HUSK  RIKTIGE VERDIER HER
   protected static final byte PID_APRS = 0;
   protected static final byte FLAG_LAST = (byte) 0x01;
   protected static final byte ASCII_SPC = (byte) 0x20;
   protected static final byte FEND  = (byte) 0xC0;
   protected static final byte FESC  = (byte) 0xDB;
   protected static final byte TFEND = (byte) 0xDC;
   protected static final byte TFESC = (byte) 0xDD;
   
   private InputStream _istream; 
   private OutputStream _ostream;
   private boolean _close = false; 
    
   protected static class Addr {
      String addr;
      boolean last;
      public Addr(String a, boolean l)
        { addr=a; last=l; }
   }
 
   protected static class Frame_End extends Throwable
   {}
   
    
    
    public KissTncChannel(ServerAPI api) 
    {
       super(api);
       Properties config = api.getConfig();
    }
    
    
    
    /**
     * Send packet on RF. 
     */ 
    public synchronized void sendPacket(Packet p)
    {
        try {
           /* Start of frame. KISS command = data */
           sendFend(); 
           sendByte(0); 
       
           /* AX.25 UI Header */
           String[] digis = p.via.split(",");
           encodeAddr(p.to, false);
           encodeAddr(p.from, digis.length>0);
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
        catch (IOException e)
           { System.out.println("*** Error: KissTncChannel.sendPacket: "+e); }
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
        
        while (!_close) 
        {
            Packet p = _receivePacket();
            receivePacket(p, false);
            Thread.yield();
        }
    }
    
    
    
    private Packet _receivePacket()
    {
        Packet p = new Packet();
        boolean complete = false;
        while (true) {
           try {
               receiveByte();
               Addr a = decodeAddr();
               p.from = a.addr; 
               a = decodeAddr();
               p.to = a.addr;
               while (!a.last) {
                  a = decodeAddr();
                  p.via += a.addr;
                  if (!a.last)
                     p.via += ",";
               } 

               if (receiveByte() == FTYPE_UI && receiveByte() == PID_APRS)
                  complete = true;
               while (true)
                  p.report += (char) receiveByte(); // CONVERT TO UNICODE?
           
           }
           catch (Frame_End e) {
              if (complete)
                return p;
           }
           catch (IOException e) { /* ERRROR */ }
        }
    }
    
   
   
   /**
    * Encode AX25 address field (callsign).
    */
    private void encodeAddr(String addr, boolean last) throws IOException
    {
        byte ssid = 0;
        if (addr.charAt(addr.length()-2) == '-') {
            ssid = (byte) addr.charAt(addr.length()-1);
            addr = addr.substring(0,addr.length()-2); 
        }
        byte[] b = addr.getBytes();
        byte flags = (last ? FLAG_LAST : 0);
        int i=6;
        for (byte c : b) {
           i--;
           sendByte(c << 1);
        }
        for (int j=0; j<i; j++)
           sendByte(ASCII_SPC << 1);
        sendByte(((ssid & 0x0F) << 1) | (flags & 0x81) | 0x60 );
    }



   /**
    * Decode AX25 address field (callsign).
    */
    private Addr decodeAddr() throws IOException, Frame_End
    {
        byte x;
        String result = "";
        
        for (int i=0; i<6; i++)
        {
           x = receiveByte();
           x >>= 1;
           if (x != ASCII_SPC)
              result += (char) x;  
        }
        
        x = receiveByte();
        int ssid = (x & 0x1E) >> 1; 
        int flags = x & 0x81 & FLAG_LAST;
        return new Addr(result + (ssid>0 ? "-"+ssid : ""), flags!=0 );
    }
    
    
    
   /**
    * Send byte according to the SLIP/KISS protocol.
    */
    private void sendByte(int x) throws IOException
    {
       if (x==FEND)
          { _ostream.write(FESC); _ostream.write(TFEND); }
       else if (x==FESC)
          { _ostream.write(FESC); _ostream.write(TFESC); }
       else
           _ostream.write((byte) x);
    }
    
    
   /**
    * Open or close frame according to the SLIP/KISS protocol.
    * i.e. a FEND character is sent..
    */
    private void sendFend() throws IOException
       {_ostream.write(FEND); }
    
    
   /**
    * Receive byte according to the SLIP/KISS protocol.
    * Throws an exception Frame_End when receiving a FEND. 
    */
    private byte receiveByte() throws IOException, Frame_End
    {
       boolean escaped = true;
       while (true) {
          byte x = (byte) _istream.read();
          if (x == FEND)
             throw new Frame_End();
          else if (x == FESC) 
             escaped = true; 
          else if (escaped)
             if (x==TFEND) 
                return FEND; 
             else if (x==TFESC)
                return FESC;
             else
                escaped = false;
         else
            return x;
      }
   }
       
}

