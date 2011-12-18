 
/* 
 * Copyright (C) 2011 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import java.util.*;
import java.util.regex.*;
import java.io.*;
import se.raek.charset.*;
import java.text.*;


/**
 * Channel for sending/receiving APRS data. 
 */
public abstract class Channel
{
     private static final long HRD_TIMEOUT = 1000 * 60 * 40; /* 40 minutes */
     protected LinkedHashMap<String, Heard> _heard = new LinkedHashMap();
           
     protected static class Heard {
         public Date time; 
         public String path;
         public Heard(Date t, String p)
           { time = t; path = p;}
     }
     
     private void removeOldHeardEntries()
     {
          Iterator<Heard> it = _heard.values().iterator();
          Date now = new Date();
          while (it.hasNext()) {
              Date x = it.next().time;
              if (now.getTime() > x.getTime() + HRD_TIMEOUT)
                 it.remove();
              else
                 return;
          }
     }


    /**
     *  APRS packet.
     */ 
    public static class Packet implements Cloneable {
        public String from, to, msgto, via, via_orig, report; 
        public char type; 
        public boolean thirdparty = false; 
        Channel source;
        @Override public Packet clone() 
            { try { return (Packet) super.clone();}
              catch (Exception e) {return null; } 
            }
    }
    
    
    /**
     * Interface for receivers of APRS packets.
     */
    public interface Receiver {
        public void receivePacket(Channel.Packet p, boolean dup);
    }
    
    
    private static Pattern _ppat = Pattern.compile
       ("([\\w\\-]+)>([\\w\\-]+)(((,[\\w\\-]+\\*?))*):(.*)");

    
    private DateFormat df = new SimpleDateFormat("dd MMM yyyy HH:mm:ss",
           new DateFormatSymbols(new Locale("no")));
    
    
    /* 
     * Perhaps we should use a list of receivers in a later version, but for now
     * two is sufficient: A parser and an igate.
     */
    private   Receiver     _r1 = null; 
    private   Receiver     _r2 = null;
    protected PrintWriter  _out = null; 
    
    public static DupCheck  _dupCheck = new DupCheck();
    public static final String _rx_encoding = "X-UTF-8_with_cp-850_fallback";
    public static final String _tx_encoding = "UTF-8";



    public abstract String getShortDescr(); 
    
    
    /**
      * Returns true if call is heard.
      */
    public boolean heard(String call)
     {
         removeOldHeardEntries();
         return _heard.containsKey(call);
     }
           
           
           
    /**
     * Return last heard digipeater path for a callsign
     */
    public String heardPath(String call)
     {
         Heard x = _heard.get(call);
         if (x==null)
            return null;
         else
            return x.path;
     }
     
     
     
    /**
     * Generate a reverse path for a callsign based on last heard digi path.
     */
    public static String getReversePath(String path)
    {
       String result = "";
       Stack<String> st = new Stack<String>();
       String[] p = path.split(",");
       boolean dflag = false;
       for(String x : p) {       
          if (x.length() < 1)
             break;
          if (x.charAt(x.length()-1) == '*') {
             x = x.substring(0, x.length()-1);
             dflag = true;
          }
          else if (dflag) 
             break;
          if (!x.matches("(WIDE|TRACE|NOR|SAR|TCPIP|TCPXX|NOGATE|RFONLY|NO_TX).*")) 
             st.push(x);   
       }
       if (dflag) 
          while (!st.empty())
            result += (st.pop() + ",");
       return result.length() == 0 ? "" : result.substring(0,result.length()-1);
    }
    
    
    
   /**
     * Number of stations heard.
     */     
    public int nHeard()
       { return _heard.keySet().size(); }
       
       
    
    public PrintWriter getWriter()
       { return _out; }


    
    public abstract void sendPacket(Channel.Packet p);
    
    
    
    /**
     * Configure receivers. 
     * Each channel can have up to two receivers (subscribers). 
     */
    public void setReceivers(Receiver r1, Receiver r2)
       { _r1 = r1; _r2 = r2; }
           
           
    
    /**
     * Convert text string to packet structure. 
     */
    protected Packet string2packet(String packet)
    {
        if (packet == null || packet.length() < 10)
           return null;
        Matcher m = _ppat.matcher(packet);
        if (m.matches())
        {
            Packet p = new Packet();
            p.from = m.group(1).trim().toUpperCase();
            p.to   = m.group(2).trim().toUpperCase();
            p.via  = m.group(3);
            p.report  = m.group(m.groupCount());
            if (p.report.length() == 0)
               return null;
            p.type = p.report.charAt(0);
            
            if (p.type == '}') {
             /* Special treatment for third-party type. 
              * Strip off type character and apply this function recursively
              * on the wrapped message. 
              */
               p = string2packet(p.report.substring(1, p.report.length()));
               if (p != null) 
                   p.thirdparty = true; 
               else
                   return null;
               
            }
            else if (p.type == ':') 
              /* Special treatment for message type.
               * Extract recipient id
               */
                p.msgto = p.report.substring(1,9).trim();


            /* Remove first comma in path */
            if (p.via != null) 
                  p.via = p.via.trim();
            while (p.via != null && p.via.length() > 0 && p.via.charAt(0) == ',')
                  p.via = p.via.substring(1);

            return p;
        }
        return null;
    }



    /**
     * Register a station as heard (or not) based on its packet.
     */
    protected abstract void regHeard(Packet p);
    
           
           
           
    /**
     * Process incoming packet. 
     * To be called from subclass. Parses packet, updates heard table, checks for
     * duplicates and if all is ok, deliver packet to receivers.
     */
    protected void receivePacket(String packet, boolean dup)
    {             
       if (packet == null || packet.length() < 1)
          return;
       System.out.println(df.format(new Date()) + " ["+getShortDescr()+"] "+packet);    
       Packet p = string2packet(packet);
       if (p == null)
          return;  
       p.source = this;
       dup = _dupCheck.checkPacket(p.from, p.to, p.report);
       if (!dup) 
          /* Register heard, only for first instance of packet, not duplicates */
          regHeard(p);
       
       if (_r1 != null) 
           _r1.receivePacket(p, dup);
       if (_r2 != null)
           _r2.receivePacket(p, dup);
       
    }
    
    public String toString() {return "Channel"; }
    
}

