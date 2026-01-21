/* 
 * Copyright (C) 2016-2026 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
 
package no.polaric.aprsd.aprs;
import no.polaric.aprsd.*;
import no.polaric.aprsd.channel.*;
import no.polaric.aprsd.point.*;
import no.polaric.aprsd.util.*;
import java.util.regex.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;
import java.math.BigInteger;
import java.util.function.*;



/**
 * Parse and interpret APRS datastreams.
 */
public class AprsParser extends AprsUtil implements AprsChannel.Receiver
{ 

    protected static Predicate<String> _viaPat = Pattern.compile
       ("(WIDE|TRACE|NOR|SAR).*").asPredicate();
    
    protected static Predicate<String> _comprPos = Pattern.compile
       ("[\\\\/0-9A-Z][\\x20-\\x7f]{12}.*").asPredicate();
       
    protected static Predicate<String> _csPat = Pattern.compile
       ("[0-9]{3}/[0-9]{3}").asPredicate();
       
    protected static Predicate<String> _dotdotdotPat = Pattern.compile
       ("/(\\.\\.\\./\\.\\.\\.)").asPredicate();
       
    protected static Predicate<String> _phgPat = Pattern.compile
       ("PHG[0-9]{4}").asPredicate();
       
    protected static Predicate<String> _rngPat = Pattern.compile
       ("RNG[0-9]{4}").asPredicate();
       
    protected static Predicate<String> _altitudePat = Pattern.compile  
       ("/A=[0-9]{6}").asPredicate();
       
    /* DAO conversion factor: 1/100th arcminute to degrees 
     * 1 arcminute = 1/60 degree, so 1/100th arcminute = 1/(60*100) = 1/6000 degree */
    private static final double DAO_PRECISION_FACTOR = 6000.0;
    
    /* Base-91 encoding constants for DAO */
    private static final int BASE_91_MIN = 33;     // Minimum ASCII value for base-91 (!)
    private static final int BASE_91_MAX = 123;    // Maximum ASCII value for base-91 ({)
    private static final int BASE_91_OFFSET = 33;  // Base-91 offset for decoding
    private static final int DAO_CENTER_VALUE = 45; // Center value representing zero offset
       
    private MessageProcessor _msg;
    private List<ReportHandler> _subscribers = new LinkedList<ReportHandler>();
    private String _key;
    private AesGcmSivEncryption _encr;


    
    public AprsParser(AprsServerConfig a, MessageProcessor msg) 
    {
        _conf = a;
        _msg = msg;
        _hmsFormat.setTimeZone(TimeZone.getTimeZone("GMT")); 
        _key = a.getProperty("message.auth.key", "NOKEY");
        _encr = new AesGcmSivEncryption(_key, Main.SALT_APRSPOS);
    }  
    
    
    
    public void subscribe(ReportHandler subscriber)
      { _subscribers.add(subscriber); }
      
    public void unsubscribe(ReportHandler subscriber)
      { _subscribers.remove(subscriber); }
    
    
    
    /**
     * Receive APRS packet. 
     * Duplicate packets are only parsed wrt. path (infrastructure analysis)
     */
    public void receivePacket(AprsPacket p, boolean duplicate)
    {
        if (_conf.getDB() == null)
           return; 
                
        if (p.report == null || p.report.length() < 1)
            return;
          
        Station station = _conf.getDB().getStation(p.from, null);
        if (station == null)
            station = _conf.getDB().newStation(p.from); 
        if (station == null)
            return;
        station.setSource(p.source);
        station.setTag("APRS");
        station.setTag(p.source.getTag());
         
        if (!duplicate) try {    
        switch(p.type)
        {
            case '>':
               /* Status report */
               parseStatusReport(p, station, false);
               break;
               
            case '!': 
            case '=':
               /* Real time position report */ 
               parseStdAprs(p, p.report, station, false, p.via);
               break;
              
            case '@':
            case '/': 
               /* Timestamped position report */   
               parseStdAprs(p, p.report, station, true, p.via);
               break;
               
            case ';': 
               /* Object report */
               parseObjectReport(p, station);
               break;
               
            case ')': 
               /* Item report */
               parseItemReport(p, station);
               break;
               
            case '\'':
            case '`': 
               /* Mic-E report */
               parseMicE(p, station); 
               break;
               
            case ':':
               /* Message */
               parseMessage(p, station);
               break;
               
            case 'T':  
               /* Telemetry (old format - deprecated) */
               parseOldTelemetry(p, station);
               break;
               
            case '{':
               /* User defined / experimental packet */
               parseUserdefined(p, station);
               break;

            default: 
               /* If the first character is not recognized as a valid APRS frame type, 
                * The frame may be regarded as a status beacon. 
                */
               if ("ID".equals(p.to) || "BEACON".equals(p.to))
               {
                  station.setTag("BEACON");
                  parseStatusReport(p, station, true);
               }
        }
        } catch (NumberFormatException e) { 
            _conf.log().debug("AprsParser", "Cannot parse number in input. Report string probably malformed"); 
            _conf.log().debug("AprsParser", p.toString());
          }
          catch (IllegalArgumentException e) {
            _conf.log().warn("AprsParser", "Illegal Argument: "+e.getMessage());
          }
          
          
        
        for (ReportHandler h:_subscribers)
            h.handlePacket(p);
        parsePath(station, p.via, duplicate);   
    }

    
       
   /**
    * Parse path to get info about infrastructure.
    */    
    private void parsePath(Station s, String path, boolean duplicate)
    {
        if (path == null)
           return;
        String[] pp = path.split(",");
        int plen = pp.length;
        int tindex = -1, i=0;
        for (String x: pp) {
           if (x.matches(".*\\*")) { 
              tindex=i; 
              pp[i] = x.substring(0, x.length()-1); 
              break; 
           }
           i++;
        }
        Station from = s;
        boolean skip = false;
        int n = 0;
        for (i=0; i<=tindex; i++) {
            Station to = _conf.getDB().getStation(pp[i], null);
            if ( to != null) {
                n++; 
                if (!skip) { 
                   skip=false;
                   _conf.getDB().getRoutes().addEdge(from.getIdent(), to.getIdent(), !duplicate); 
                }
                if (n>1 && i<=tindex) 
                   to.setWideDigi(true); /* Digi is WIDE */
                from = to;
            }
            else if (!_viaPat.test(pp[i]))  // (WIDE|TRACE|NOR|SAR).*
               skip = true;
        }
        
        /* Has packet been gated through APRS-IS?
         * The last node in path is igate
         */
        if ((plen >= 2) && pp[plen-2].matches("qA.")) 
        {
           if (tindex == -1) {
               Station to = _conf.getDB().getStation(pp[plen-1], null);
               if (to != null) {
                    if (_conf.getDB().getRoutes() !=null)
                        _conf.getDB().getRoutes().addEdge(s.getIdent(), to.getIdent(), !duplicate);  
                    to.setIgate(true);         
                    /* Igate direct (has not been digipeated) */
               }  
           }
           else {
               Station last = null;
               if (_viaPat.test(pp[tindex]) && tindex > 0) // (WIDE|TRACE|NOR|SAR).*
                  last = _conf.getDB().getStation(pp[tindex-1], null);
               Station x = _conf.getDB().getStation(pp[plen-1], null);
               if (last != null && x != null) {
                  if (_conf.getDB().getRoutes() != null)
                    _conf.getDB().getRoutes().addEdge(last.getIdent(), x.getIdent(), !duplicate);
                  x.setIgate(true);
                  /* Path from last digi to igate */
               }
           }
       }
    } 
    
    
    private void parseUserdefined(AprsPacket p, Station station)
    {
        String msg = p.report;
        if (msg.matches("\\{\\{\\:.+") )
            parseEncrypted(p, station);
    }
    


    private void parseEncrypted(AprsPacket p, Station station)
    {
        String ciphertext = p.report.substring(3);
        String text = _encr.decryptB91(ciphertext, station.getIdent(), null);
        if (text == null)
            _conf.log().info("AprsParser", "Cannot decrypt/authenticate packet: "+station.getIdent());
        else {
            AprsPacket pp = p.clone(); 
            pp.report = text;
            pp.type = text.charAt(0);
            receivePacket(pp, false);
        }
    }



    /**
     * Parse APRS message.
     */
    private void parseMessage(AprsPacket p, Station station)
    {
        String msgid = null;
        String msg = p.report;
        
        String recipient = msg.substring(1,10).trim();
        int i = msg.lastIndexOf('{');
        if (i >= 0) 
           msgid = msg.substring(i+1, msg.length());
        
        if (i>msg.length() || (i>0 && i<11)) {
            _conf.log().debug("AprsParser", "Message format problem: '"+msg+"'");
            return;
        }
        String content = msg.substring(11, (i>=0 ? i : msg.length()));
        
        if (msg.charAt(10) != ':') {
            int pos = msg.indexOf(':',3);
            if (pos > 11 || pos < 0) {
                _conf.log().debug("AprsParser", "Message without dest: '"+msg+"'");
                return; 
            }
            recipient = msg.substring(1,pos).trim();
            content = msg.substring(pos+1, (i>=0 ? i : msg.length()));
        }
        
        /* If sender==recipient and no msg id: This is telemetry metadata */
        if (msgid==null && station.getIdent().equals(recipient))
           parseMetadata(station, content);
        
        if (_msg != null)
           _msg.incomingMessage(station, p.to, recipient, content, msgid);
        for (ReportHandler h:_subscribers)
            h.handleMessage(p.source, new Date(), station.getIdent(), recipient, content);
    }

    

    /**
     * Parse APRS status report.
     */
    private void parseStatusReport(AprsPacket p, Station station, boolean includefirst)
    {
        String msg = p.report; 
        Date d = null;
        if (!includefirst)
            msg = msg.substring(1);
        if (msg.matches("[0-9]{6}[hz/].*")) {
           d = parseTimestamp(msg, false);
           msg = msg.substring(7);
        }

        for (ReportHandler h:_subscribers)
           h.handleStatus(p.source, d, msg);
        station.setStatus(d, msg);
    }
    
    
    
    /**
     * Parse APRS object report.
     */
    private void parseObjectReport(AprsPacket p, Station station)
    {
        String msg = p.report; 
        String ident = msg.substring(1,10).trim();
        char op = msg.charAt(10);
        
        station.setPathInfo(p.via); 
        TrackerPoint x = _conf.getDB().getItem(ident+'@'+station.getIdent(), null);      
        AprsObject obj;
        if (x == null) {
            obj = _conf.getDB().newObject(station, ident);
            obj.setTag("APRS");
            obj.setTag(p.source.getTag());
            obj.autoTag();
        }
        else if (x instanceof AprsObject)
            obj = (AprsObject) x;
        else {
           _conf.log().warn("AprsParser", "Object "+x+" is not an APRS object");
           return;
        }
             
        if (op=='*') {
           parseStdAprs(p, msg.substring(10), obj, true, "");
           _conf.getDB().deactivateSimilarObjects(ident, station);
        }
        else 
           obj.kill();
    }
    
    
    
    
     /**
     * Parse APRS item report.
     */
    private void parseItemReport(AprsPacket p, Station station)
    {
        String msg = p.report;
        int i = msg.indexOf('!');
        if (i == -1 || i > 11)
           i = msg.indexOf('_');  
        if (i==-1) return;
           
        String ident = msg.substring(1, i).trim();
        char op = msg.charAt(i);
        
        station.setPathInfo(p.via); 
        TrackerPoint x = _conf.getDB().getItem(ident+'@'+station.getIdent(), null);      
        AprsObject obj;
        if (x == null)
            obj = _conf.getDB().newObject(station, ident);
        else if (x instanceof AprsObject)
            obj = (AprsObject) x;
        else{
           _conf.log().warn("AprsParser", "Object "+x+" is not an APRS item");
           return;
        }
             
        if (op=='!') {
           parseStdAprs(p, msg.substring(i), obj, false, "");
           _conf.getDB().deactivateSimilarObjects(ident, station);
        }
        else 
           obj.kill();
    }
    
    

   
   /**
    * Parse mic-e data.
    * Based on http://www.aprs-is.net/javAPRS/mice_parser.htm
    */
    private void parseMicE (AprsPacket p, AprsPoint station) 
    {
        String cm[] = new String[1];
        ReportHandler.PosData pd = parseMicEPos(p, cm);
        if (pd == null)
            return;
        
        String comment = cm[0];
        comment = parseComment(comment, new Date(), station, pd, p);
        if (comment != null){
            comment = comment.trim();   
            if (comment.length() == 0)
               comment = null;
        }     
        station.update(new Date(), pd, comment, p.via);
        station.autoTag();
        for (ReportHandler h:_subscribers)
           h.handlePosReport(p.source, station.getIdent(), new Date(), pd, comment, p.report );
    }


    
     
    /**
     * Parse timestamp.
     * 'Compression' of timestamp is experimental. It is not part of 
     * the standard APRS protocol
     */
    private Date parseTimestamp(String data, boolean compressed)
    { 
         Calendar ts;
         int day,hour,min,sec;
         char tst = (compressed ? 0 : data.charAt(6));
         String dstr = data.substring(0, (compressed ? 3:6) ); 
         Calendar now = (Calendar) localTime.clone();    
         now.setTimeInMillis((new Date()).getTime());
     
                                   
         /* Default timezone is zulu */
         ts = (Calendar) utcTime.clone();
         ts.setTimeInMillis( now.getTimeInMillis()) ;
              
         if (compressed || tst == 'h') {
             /* Parse time in HHMMSS format */
             day = now.get(Calendar.DAY_OF_MONTH);
             if (compressed) {
                hour = dstr.charAt(0) - '0';
                min = dstr.charAt(1) - '0';
                sec = dstr.charAt(2) - '0';
             }
             else {
                hour = Integer.parseInt(dstr.substring(0,2));
                min  = Integer.parseInt(dstr.substring(2,4));
                sec  = Integer.parseInt(dstr.substring(4,6));
             } 
         }
         else {
            /* Parse time in DDHHMM format */
            if (tst == 'z') {
               if (dstr.equals("111111")) 
                   return null; /* Timeless timestamp, ugh! */
            }
            else {
               ts = (Calendar) localTime.clone(); 
               _dtgFormat.setCalendar(ts);
            }
            day  = Integer.parseInt(dstr.substring(0,2));
            hour = Integer.parseInt(dstr.substring(2,4));
            min  = Integer.parseInt(dstr.substring(4,6));
            sec  = 0; 
         }
         /* Sanity check */
         if (day<1||day>31||hour>24||min>59||sec>59) { 
            _conf.log().debug("AprsParser", "Invalid timestamp: "+dstr+tst+" - using current time");
            return new Date();
         }

     
         /* Set fields of the timestamp. Try to figure out the month and the year. 
          * if timestamp hour number is higher than actual time, the timestamp 
          * is probably from previous day. If timestamp day number is higher than
          * todays day number it is probably the previous month.
          */
     
         ts.set(Calendar.SECOND, sec);
         ts.set(Calendar.MINUTE, min);
         ts.set(Calendar.HOUR_OF_DAY, hour);
         ts.set(Calendar.DAY_OF_MONTH, day);
         ts.set(Calendar.MONTH, now.get(Calendar.MONTH));  
     
         if (  ts.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) 
              && hour > now.get(Calendar.HOUR_OF_DAY) + 2) 
             ts.add(Calendar.DAY_OF_MONTH, -1); 
      
         if (day > now.get(Calendar.DAY_OF_MONTH))
             ts.add(Calendar.MONTH, -1);
     
         ts.set(Calendar.YEAR, now.get(Calendar.YEAR)); 
         return ts.getTime();
    }
      

    
    
    private static int base91Decode(byte c0, byte c1)
    {
        return (int) base91Decode((byte)0, (byte)0, c0, c1);
    }
    
    


    
    

    private void parseExtraReport(Source src, String data, AprsPoint station)
    {
       Date time = new Date();
       time = parseTimestamp(data.substring(0), true);
       ReportHandler.PosData pd = parseCompressedPos(data.substring(3));
       
       if (AprsChannel._dupCheck.checkTS(station.getIdent(), time))
            return;
            
       _conf.log().debug("AprsParser", "Extra report accepted: "+time);
       station.update(time, pd, "", "(EXT)" );        
       for (ReportHandler h:_subscribers)
           h.handlePosReport(src, station.getIdent(), time, pd, "", "(EXT)" );
    }
    
    
    
    
    private String parseExtraReport2(Source src, String data, AprsPoint station,  ReportHandler.PosData pd, Date ts)
    {
        while (data.length() >= 8) {
            if (!data.matches("([A-Za-z0-9\\+/]{8})+.*"))
                break;
            int ts_delta  = b64to12bit(data.substring(0, 2));
            int lat_delta = b64to18bit(data.substring(2, 5));
            int lng_delta = b64to18bit(data.substring(5, 8));
            
            long tsx = ( ts.getTime() / 1000 + ts_delta) * 1000;
            
            LatLng pp = (LatLng) pd.pos;
            double lat = pp.getLat() + ((double)lat_delta / 100000); 
            double lng = pp.getLng() + ((double)lng_delta / 100000);
            data = data.substring(8);

            
            Date time = new Date(tsx);
            if (AprsChannel._dupCheck.checkTS(station.getIdent(), time)) {
                _conf.log().debug("AprsParser", "Extra report type 2 DUPLICATE: "+time);
                continue;
            }
            
            _conf.log().debug("AprsParser", "Extra report type 2 accepted: "+time);
            ReportHandler.PosData pdx = new ReportHandler.PosData(); 
            pdx.pos = new LatLng(lat, lng);
            pdx.symbol = pd.symbol; 
            pdx.symtab = pd.symtab;
            pdx.course = pd.speed = -1; 
            pdx.altitude = -1; 
            station.update(time, pdx, "", "(EXT)" );        
            for (ReportHandler h:_subscribers)
                h.handlePosReport(src, station.getIdent(), time, pdx, "", "(EXT)" );
        }
        return data;
    }
    
    

    
    
    /** 
     * Parse standard position data.
     */
    private void parseStdAprs(AprsPacket p, String data, AprsPoint station, boolean timestamp, String pathinfo)
    {
         Date time = p.time;
         if (timestamp) {
            if (data.substring(1).matches("[0-9]{6}h.*")) 
                time = parseTimestamp(data.substring(1), false);   
            data = data.substring(8);
            
            /* A duplicate check on timestamp itself */
            if (AprsChannel._dupCheck.checkTS(station.getIdent(), time)) 
                return; 
         }
         else
            data = data.substring(1);
         
         String comment = "";
         
         /*
          * Now, extract position info and comment
          */     
         ReportHandler.PosData pd = null;
         Matcher m = _stdPat.matcher(data);
         if (m.matches())
         {
            pd = parseStdPos(data, m, p); 
            comment = m.group(11);
         }
         else if (_comprPos.test(data))  
           /* Parse compressed position report */
         {
            pd = parseCompressedPos(data);
            comment = data.substring(13);
         }
         if (pd==null)
            return;
         if (pd.symbol == '_')
            comment = parseWX(comment);
              
         comment = parseComment(comment, time, station, pd, p); 

         if (comment.length() > 0 && comment.charAt(0) == '/') 
            comment = comment.substring(1);  
         comment = comment.trim();
         if (comment.length() < 1 || comment.equals(" "))
            comment = null;
             
         station.autoTag();
         station.update(time, pd, comment, pathinfo );           
         for (ReportHandler h:_subscribers)
            h.handlePosReport(p.source, station.getIdent(), time, pd, comment, pathinfo ); 
    }
    
      
    

    /** 
     * Parse comment field 
     */
    private String parseComment(String comment, Date ts, AprsPoint station, ReportHandler.PosData pd, AprsPacket p)
    {        
        if (comment==null)
           return null;
        while( true ) {
           /* Course and speed: nnn/nnn */
           if (comment.length() >= 7 && _csPat.test(comment.substring(0,7))) {
              pd.course = Integer.parseInt(comment.substring(0,3));
              pd.speed  = (int) Math.round( Integer.parseInt(comment.substring(4,7))* 1.852);
              comment = comment.substring(7);
              
              /* Ignore additional Bearing/NRQ fields */
              if (comment.length() >= 8 && _csPat.test(comment.substring(0,8)))
                 comment = comment.substring(8);
              else if (comment.length() >= 8 && _dotdotdotPat.test(comment.substring(0,8)))
                 /* Ignore */ ;
           } 
           
           /* .../... */
           else if (comment.length() >= 7 && _dotdotdotPat.test(comment.substring(0,7)))
              /* Ignore */
              ;
           
           /* PHGnnnn */
           else if (comment.length() >= 7 && _phgPat.test(comment.substring(0,7))) {
             int power = (comment.charAt(3)-'0');
             power = power * power; 
             int gain = (comment.charAt(5)-'0');
             int ht = (int) Math.round(10 * Math.pow(2, (comment.charAt(4)-'0')));
             int r = (int) Math.round(Math.sqrt(2*ht*Math.sqrt((power/10)*(gain/2))));
             int rKm = (int) Math.round(r*1.609344);
             
             String dir = switch (comment.charAt(6))
             {
                case '1' -> " NE";
                case '2' -> " E";
                case '3' -> " SE";
                case '4' -> " S";
                case '5' -> " SW";
                case '6' -> " W";
                case '7' -> " NW";
                case '8' -> " N";
                default  -> "";
             };
             comment = comment.substring(7,comment.length()) + " ("+power+" watt, "+gain+" dB"+dir+
                   (rKm>0 ? " => "+rKm+" km" : "") + ")"; 
           }
           
           /* RNGnnnn */
           else if (comment.length() >= 7 && _rngPat.test(comment.substring(0,7))) {
             int r = Integer.parseInt(comment.substring(3,7));
             int rKm = (int) Math.round(r*1.609344);
             comment = comment.substring(7, comment.length()) + " ("+rKm+" km omni)";
           }
          
           /* Altitude /A=nnnn */
           else if (comment.length() >= 9 && _altitudePat.test(comment.substring(0,9))) {
              pd.altitude = Long.parseLong(comment.substring(3,9));
              pd.altitude *= 0.3048;
              comment = comment.substring(9, comment.length());
           }
 
           /* Extra posreports (experimental) 
            * Format: "/#" + compressed timestamp + compressed report */
           else if (comment.length() >= 18 && comment.matches("(/\\#.{16})+.*")) {
              parseExtraReport(p.source, comment.substring(2,18), station); 
              comment = comment.substring(18, comment.length());
           }
           else if (comment.matches("/\\*([A-Za-z0-9\\+/]{8})+.*")) {
              comment = parseExtraReport2(p.source, comment.substring(2), station, pd, ts); 
           }
           else break;
        }
        
        /* Telemetry */
        Matcher m = _telPat.matcher(comment);
        if (m.matches()) {
           if (station instanceof Station)
              parseTelemetry((Station) station, ts, m.group(1)); 
           comment = comment.substring(0, m.start(1)-1) + " (telemetry) " + comment.substring(m.end(1)+1);
        }
        /* DAO parsing for extra precision */
        comment = parseDAO(comment, pd);
        return comment;
    }
    
    
    /**
     * Parse DAO (Datum and Added Precision) extension.
     * !DAO! - is fixed length (5 characters) anywhere in the position comment
     *   D - is the datum identifier (base-91)
     *   A - is the added Latitude precision (base-91)
     *   O - is the added Longitude precision (base-91)
     * 
     * The DAO extension adds 1/100th of a minute precision to lat/long.
     * Base-91 characters represent values 0-90 (ASCII 33-123).
     * 
     * @param comment The comment string to parse
     * @param pd Position data to update with extra precision
     * @return The comment with DAO extension removed
     */
    private String parseDAO(String comment, ReportHandler.PosData pd) 
    {
        if (comment == null || comment.length() < 5 || pd == null || pd.pos == null)
            return comment;
            
        // Search for !DAO! pattern anywhere in the comment
        int idx = comment.indexOf('!');
        while (idx >= 0 && idx + 4 < comment.length()) {
            if (comment.charAt(idx + 4) == '!') {
                // Found potential DAO pattern
                char d = comment.charAt(idx + 1); // Datum character (currently not used, reserved for future datum support)
                char a = comment.charAt(idx + 2);
                char o = comment.charAt(idx + 3);
                
                // Validate that characters are in valid base-91 range
                if (d >= BASE_91_MIN && d <= BASE_91_MAX && 
                    a >= BASE_91_MIN && a <= BASE_91_MAX && 
                    o >= BASE_91_MIN && o <= BASE_91_MAX) {
                    // Decode base-91 values (0-90)
                    int latOffset = a - BASE_91_OFFSET;
                    int lngOffset = o - BASE_91_OFFSET;
                    
                    // Adjust for centered encoding: 0-90 represents -45 to +45
                    // where 45 is center (no offset)
                    double latAdjust = (latOffset - DAO_CENTER_VALUE) / DAO_PRECISION_FACTOR;
                    double lngAdjust = (lngOffset - DAO_CENTER_VALUE) / DAO_PRECISION_FACTOR;
                    
                    // Get current position
                    LatLng currentPos = (LatLng) pd.pos;
                    double newLat = currentPos.getLat() + latAdjust;
                    double newLng = currentPos.getLng() + lngAdjust;
                    
                    // Update position with refined coordinates
                    pd.pos = new LatLng(newLat, newLng);
                    
                    // Remove DAO extension from comment
                    comment = comment.substring(0, idx) + comment.substring(idx + 5);
                    
                    // DAO processed, exit loop
                    break;
                }
            }
            // Search for next '!' character
            idx = comment.indexOf('!', idx + 1);
        }
        
        return comment;
    }
    
    
    /**
     * Parse telemetry (old style).
     */
    private void parseOldTelemetry(AprsPacket p, Station st)
    {
        float[]  aresult = new float[Telemetry.ANALOG_CHANNELS];
        boolean[] bits = new boolean[Telemetry.BINARY_CHANNELS];
                
        String[] data = p.report.split(",\\s*");
        if (data.length < 7 || data[0].length() < 5)
           return; 
        
        String sseq = data[0].substring(2);
        if ("MIC".equals(sseq))
            return;
        int seq = Integer.parseInt(sseq);    
        for (int i=0; i < Telemetry.ANALOG_CHANNELS; i++)
           if (data[i+1].length() > 0)
              aresult[i] = Float.parseFloat(data[i+1]);
           else 
              aresult[i] = 0;
        
        for (int i=0; i < Telemetry.BINARY_CHANNELS && i < data[6].length(); i++)
           bits[i] = (data[6].charAt(i) == '1'); 

        st.getTelemetry().addData(seq, new Date(), aresult, bits);  
        st.setTag("APRS.telemetry");
    }
     
    
    
    
    /**
     * Parse compressed telemetry.
     */
    private void parseTelemetry(Station st, Date ts, String data)
    {  
        float[]  aresult = new float[Telemetry.ANALOG_CHANNELS];
        int    bresult = 0;
        byte[] bdata   = data.getBytes();

        int seq = (int) base91Decode(bdata[0], bdata[1]);         
        for (int i=1; i <= Telemetry.ANALOG_CHANNELS && data.length()-2 >= i*2; i++) 
            aresult[i-1] = (float) base91Decode(bdata[i*2], bdata[i*2+1]);
        if (data.length() == 14) 
            bresult = (int) base91Decode(bdata[12], bdata[13]);
      
        boolean[] bits = new boolean[Telemetry.BINARY_CHANNELS];
        for (int i = 0; i < Telemetry.BINARY_CHANNELS; i++) 
            bits[Telemetry.BINARY_CHANNELS - 1 - i] = (1 << i & bresult) != 0;
          
        st.getTelemetry().addData(seq, ts, aresult, bits);  
        st.setTag("APRS.telemetry");
    }
    
    
    
    /**
     * Parse telemetry metadata.
     */
    private void parseMetadata(Station st, String data)
    {
        Telemetry t = st.getTelemetry();
        if (data.length() < 5) {
           _conf.log().debug("AprsParser", "Metadata format not recognized: "+data); 
           return;
        }
        String d = data.substring(5);
        
        if (data.matches("PARM.*")) 
           t.setParm( d.split(",\\s*") );
           
        else if (data.matches("UNIT.*"))
           t.setUnit( d.split(",\\s*") );
           
        else if (data.matches("EQNS.*")) {
           // Default is not to change the raw value. Is this correct? 
           float[] nd = {0,1,0,0,1,0,0,1,0,0,1,0,0,1,0};
           int i = 0;
           for (String x : d.split(",\\s*")) {
               if (x.length() > 0)
                  nd[i] = Float.parseFloat(x);
               if (++i >= 15)
                  break;
           }
           t.setEqns(nd);
        }
        else if (data.matches("BITS.*")) {
           String[] bb = d.split(",\\s*"); 
           boolean[] bits = new boolean[8];
           for (int i=0; i<8&&i<bb[0].length(); i++)
              bits[i] = (bb[0].charAt(i) == '1');
           t.setBits(bits);
           if (bb.length > 1)
              t.setDescr(bb[1]);
        }
        else
          _conf.log().debug("AprsParser", "Metadata format not recognized: "+data);
    }
    
    
    
    
    /**
     * Parse WX report.
     * Currently this is very limited; we just return 
     * a summary text containing temperature, wind speed and direction.
     */
    private String parseWX(String data)
    {
        Matcher m = _wxPat.matcher(data);
        if (m.matches()) 
        { 
            int direction = -1;
            double speed = -1, gust = -1, tempf = -100000; 
            
            // FIXME: use float 
            
            if (m.group(1).matches("\\d\\d\\d"))
                direction = Integer.parseInt(m.group(1));
            if (m.group(2).matches("\\d\\d\\d"))
                speed = Integer.parseInt(m.group(2)) * 0.44704;
            if (m.group(3).matches("\\d\\d\\d"))
                gust = Integer.parseInt(m.group(3)) * 0.44704;
            if (m.group(4).matches("\\d\\d\\d")) {
               int temp = Integer.parseInt(m.group(4)); 
               tempf = ((temp - 32) * 5) / 9;
            }

            String dir = ""; 
            if (direction >= 337 || direction < 22)
                dir = "N"; 
            else if (direction >= 22 && direction < 67)
                dir = "NE"; 
            else if (direction >= 67 && direction < 112)
                dir = "E"; 
            else if (direction >= 112 && direction < 157)
                dir = "SE"; 
            else if (direction >= 157 && direction < 202)
                dir = "S";          
            else if (direction >= 202 && direction < 247)
                dir = "SW";     
            else if (direction >= 247 && direction < 292)
                dir = "W";     
            else if (direction >= 292 && direction < 337)
                dir = "NW"; 
                
            return (tempf > -100000 ? String.format("Temp = %.1f\u00b0C", tempf) : "") + 
                   (speed>0 ? String.format(", Wind=%.1f m/s %s", speed, dir) : "");
        }
        return data;
    }
    
}
