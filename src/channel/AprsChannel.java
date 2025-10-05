/* 
 * Copyright (C) 2016-2025 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import java.util.*;
import java.util.regex.*;
import java.io.*;
import java.text.*;
import java.lang.reflect.Constructor; 


/**
 * Channel for sending/receiving APRS data. 
 */
public abstract class AprsChannel extends Channel 
{
     private static final long HRD_TIMEOUT = 1000 * 60 * 40; /* 40 minutes */
     private static boolean _logPackets = false; 
     
     protected AprsServerConfig _conf; 
     protected LinkedHashMap<String, Heard> _heard = new LinkedHashMap<String, Heard>();
    
     /* Statistics */
     protected long _heardPackets, _duplicates, _sent;        

     protected static boolean canSend;

     

     /**
      * Information about APRS packet heard on the channel. 
      */ 
     protected static class Heard {
         public Date time; 
         public String path;
         public Heard(Date t, String p)
           { time = t; path = p;}
     }
     
     
     
     public static void init(AprsServerConfig conf) {
        AprsFilter.init(conf); 
        _logPackets = conf.getBoolProperty("channel.logpackets", true);
        canSend = true;
        String myCall = conf.getProperty("default.mycall", "NOCALL").toUpperCase();
        if ("NOCALL".equals(myCall))
            canSend = false;
     }

     
     public void resetCounters() {
       _heardPackets = _duplicates = _sent = 0;
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
     * Interface for receivers of APRS packets.
     * Receivers can be AprsParser and Igate. See also Database Plugin
     */
    public interface Receiver {
        /** Receive an APRS packet. 
         * @param p AprsPacket content.
         * @param dup Set to true to indicate that this packet is a duplicate.
         */
        public void receivePacket(AprsPacket p, boolean dup);
    }


    
    private DateFormat df = new SimpleDateFormat("dd MMM yyyy HH:mm:ss",
           new DateFormatSymbols(new Locale("no")));
           
           
    /* 
     * Perhaps we should use a list of receivers in a later version, but for now
     * two is sufficient: A parser and an igate.
     */
    transient protected List<Receiver> _rcv = new LinkedList<Receiver>(); 
    transient protected PrintWriter  _out = null; 
    protected String _rfilter = null;
    protected Pattern _rfilterPattern = null;
    private AprsChannel _inRouter = null; 

    public static DupCheck  _dupCheck = new DupCheck();
    public static final String _rx_encoding = "UTF-8"; 
                  /* was "X-UTF-8_with_ISO-8859-1_fallback"; */
    public static final String _tx_encoding = "UTF-8";

 
 
    public DupCheck getDupCheck() {
      return _dupCheck;
    }
 
    protected String chId() {
       return "["+getIdent()+"] "; 
    }

    
    /* Return true if this is a RF channel */
    public boolean isRf() {
        return false; 
    }
   
    public void setInRouter(AprsChannel ir) {
      _inRouter = ir;
      /* If in router, we use the router's duplicate checker */
      if (ir != null)
         _dupCheck = ir.getDupCheck();
      else
         _dupCheck = new DupCheck();
    }
    
    public boolean isInRouter() {
      return _inRouter != null;
    }
    
    
    /**
     * Set receive filter with regex pattern. The pattern is compiled once for performance.
     */
    protected void setReceiveFilter(String filter) {
        _rfilter = filter;
        if (filter != null && !filter.isEmpty()) {
            try {
                _rfilterPattern = Pattern.compile(filter);
            } catch (PatternSyntaxException e) {
                _conf.log().warn("AprsChannel", "Invalid regex pattern in rfilter: " + filter);
                _rfilterPattern = null;
            }
        } else {
            _rfilterPattern = null;
        }
    }
    
    
    /**
      * Returns true if callsign is heard on the channel.
      */
    public boolean heard(String call)
     {
         removeOldHeardEntries();
         return _heard.containsKey(call);
     }
         
  
    /**
     * Returns the path (digipeater) of the last heard packet from the given callsign.
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
     * Reverse the order of the elements of the path. 
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
          if (!x.matches("(WIDE|TRACE|NOR|SAR|(TCP[A-Z0-9]{2})|NOGATE|RFONLY|NO_TX).*")) 
             st.push(x);   
       }
       if (dflag) 
          while (!st.empty())
            result += (st.pop() + ",");
       return result.length() == 0 ? "" : result.substring(0,result.length()-1);
    }    
    
    
    
   /**
    * Return a string that presents a packet as a third party report. 
    */
    public static String thirdPartyReport(AprsPacket p)
      { return thirdPartyReport(p, null); }
      

      
    /**
     * Return a string that presents a packet as a third party report. 
     * @param p The packet
     * @param path Digi-path to be used in the thirdparty report. If null, we will 
     *     use the path of the original packet. 
     */
    public static String thirdPartyReport(AprsPacket p, String path)
    { 
       if (path == null) 
          path = ((p.via_orig != null && p.via_orig.length() > 0) ? ","+p.via_orig : "");
       else 
          path = ","+path; 
       return "}" + p.from + ">" + p.to + path + ":" + p.report + "\r";
    }
       
       
     
   /**
     * Number of stations heard.
     */     
    public int nHeard()
       { return _heard.keySet().size(); }
       
    public long nHeardPackets()
       { return _heardPackets; }

    public long nDuplicates()
       { return _duplicates; }

    public long nSentPackets()
       { return _sent; }
       
    public PrintWriter getWriter()
       { return _out; }

    
    public abstract boolean sendPacket(AprsPacket p);
    
    
    /**
     * Configure receivers. 
     */
    public void addReceiver(Receiver r)
       { if (r != null) _rcv.add(r); }
    
    public void removeReceiver(Receiver r)
       { if (r != null) _rcv.remove(r); }   
    
    

    
    
    /**
     * Do some preliminary parsing of report part of the packet. 
     * Return null if report is invalid. 
     */
    protected AprsPacket checkReport(AprsPacket p) 
    {   
         p.report = p.report.replace('\uffff', ' ');
         if (p.report.length() <= 1)
            return null;
         p.type = p.report.charAt(0); 
         
         if (p.report.charAt(p.report.length()-1) == '\r')
            p.report = p.report.substring(0, p.report.length()-1);
         if (p.type == '}') {
            /* Special treatment for third-party type. 
             * Strip off type character and apply this function recursively
             * on the wrapped message. 
             */
             var pfrom=p.from; 
             var pto=p.to;
             var pvia=p.via;
             
             p = AprsPacket.fromString(p.report.substring(1, p.report.length()));
             if (p != null) {
                p.type = p.report.charAt(0);
                p.thirdparty = true; 
                p.from_orig = pfrom; 
                p.to_orig = pto;
                p.via_orig = pvia;
             }
             else
                return null;
               
          }
          else if (p.type == ':' || p.type == ';') {
             /* 
              * Special treatment for message type or object
              * Extract recipient/object id
              */
            p.msgto = p.report.substring(1,10).trim();
            if (p.msgto.length() > 0 && p.msgto.charAt(p.msgto.length()-1)==':')
               p.msgto = p.msgto.substring(0, p.msgto.length()-1);
          }
         return p;
    }
    
    

    protected abstract void regHeard(AprsPacket p);
    
    
    
    /**
     * Process incoming packet. 
     * To be called from subclass. Parses packet, updates heard table, checks for
     * duplicates and if all is ok, deliver packet to receivers. It also applies
     * an optional receive filter. 
     * @param packet String representation of packet. 
     * @param dup True if packet is known to be a duplicate.
     * @return true if accepted
     */
    protected boolean receivePacket(String packet, boolean dup)
    { 
       if (packet == null || packet.length() < 1)
          return false; 
       AprsPacket p = AprsPacket.fromString(packet);

       return receivePacket(p, dup);
    }
    
    
    
    /**
     * Process incoming packet. 
     * To be called from subclass. Parses packet, updates heard table, checks for
     * duplicates and if all is ok, deliver packet to receivers.
     * @param p Pre-parsed packet.
     * @param dup True if packet is known to be a duplicate.
     * @return true if accepted.
     */
    protected boolean receivePacket(AprsPacket p, boolean dup)
    {      
       if (p == null)
          return false; 
      
       p = checkReport(p); 
       if (p==null)
          return false;
       
       if (_rfilterPattern != null && !_rfilterPattern.matcher(p.toString()).matches())
          return false; 
          
       p.source = this;
       if (_logPackets)
          _conf.log().log(null, chId()+p);
       _heardPackets++;
       dup = _dupCheck.checkPacket(p.from, p.to, p.report);
       if (!dup) 
          /* Register heard, only for first instance of packet, not duplicates */
          regHeard(p);
       else
          _duplicates++;
          
       /* Pass the packet to registered receivers: Aprs-parser, igate, etc.. */
       for (Receiver r: _rcv)
           r.receivePacket(p, dup);
       return !dup;
    }
    

    
}

