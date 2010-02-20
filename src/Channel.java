 
/* 
 * Copyright (C) 2009 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
package aprs;
import java.util.*;
import java.util.regex.*;
import java.io.*;


public abstract class Channel
{
     private static final long HRD_TIMEOUT = 1000 * 60 * 40; /* 40 minutes */
     private LinkedHashMap<String, Date> _heard = new LinkedHashMap();
    
     
     private void removeOldHeardEntries()
     {
          Iterator<Date> it = _heard.values().iterator();
          Date now = new Date();
          while (it.hasNext()) {
              Date x = it.next();
              if (now.getTime() > x.getTime() + HRD_TIMEOUT)
                 it.remove();
              else
                 return;
          }
     }


     
    public static class Packet implements Cloneable {
        public String from, to, msgto, via, report; 
        public char type; 
        public boolean thirdparty = false; 
        Channel source;
        @Override public Packet clone() 
            { try { return (Packet) super.clone();}
              catch (Exception e) {return null; } 
            }
    }
    
    
    
    public interface Receiver {
        public void receivePacket(Channel.Packet p);
    }
    
    
    private static Pattern _ppat = Pattern.compile
       ("([\\w\\-]+)>([\\w\\-]+)(((,[\\w\\-]+\\*?))*):(.*)");


    /* 
     * Perhaps we should use a list of receivers in a later version, but for now
     * two is sufficient: A parser and an igate.
     */
    private   Receiver     _r1 = null; 
    private   Receiver     _r2 = null;
    protected PrintWriter  _out = null; 
    
    private static DupCheck  _dupCheck = new DupCheck();
    public static final String _encoding = "cp865";
    

    /**
      * Returns true if call is heard.
      */
    public boolean heard(String call)
     {
         removeOldHeardEntries();
         return _heard.containsKey(call);
     }
         
   /**
     * Number of stations heard.
     */     
    public int nHeard()
       { return _heard.keySet().size(); }
       
    
    public PrintWriter getWriter()
       { return _out; }

    
    public abstract void sendPacket(Channel.Packet p);
    
    
    public void setReceivers(Receiver r1, Receiver r2)
       { _r1 = r1; _r2 = r2; }
           
    
    private Packet string2packet(String packet)
    {
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
               p = string2packet(p.report.substring(1));
               if (p != null)
                  p.thirdparty = true; 
            }
            else if (p.type == ':') 
               /* Special treatment for message type.
               * Extract recipient id
               */
                p.msgto = p.report.substring(1,9).trim();

            /* Remove first comma in path */
            else if (p.via != null && p.via.length() > 0)
               p.via = p.via.substring(1).trim();
            return p;
        }
        return null;
    }
    
    
    
    protected void receivePacket(String packet)
    {
       Packet p = string2packet(packet);
       if (p == null)
          return;
       p.source = this;
       _heard.put(p.from, new Date());

       if (_dupCheck.checkPacket(p.from, p.to, p.report))
          System.out.println("*** DUPLICATE PACKET - Ignored");
       else {
          if (_r1 != null) 
             _r1.receivePacket(p);
          if (_r2 != null)
             _r2.receivePacket(p);
       }
    }
    
}

