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
import java.util.regex.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;
import uk.me.jstott.jcoord.*;

/**
 * Parse and interpret APRS datastreams.
 */
public class AprsParser implements AprsChannel.Receiver
{ 

    /* Standard APRS position report format */
    private static Pattern _stdPat = Pattern.compile
       ("((\\d|\\s)+\\.(\\d|\\s)+)([NS])(\\S)((\\d|\\s)+\\.(\\d|\\s)+)([EW])(\\S)\\s*(.*)");

    /* Weather report format */
    private static Pattern _wxPat = Pattern.compile
       ("(\\d\\d\\d|...)/(\\d\\d\\d|...)g(\\d\\d\\d|...)t(\\d\\d\\d|...).*");
                
    private static DateFormat _dtgFormat = new SimpleDateFormat("ddhhmm");
    private static DateFormat _hmsFormat = new SimpleDateFormat("hhmmss");
    
    // FIXME: These are also defined in HttpServer.java
    public static Calendar utcTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.getDefault());
    public static Calendar localTime = Calendar.getInstance();
    
       
    // FIXME: Handle ambiguity in latitude ?
     
    private ServerAPI _api   = null;
    private MessageProcessor _msg;
    private List<AprsHandler> _subscribers = new LinkedList<AprsHandler>();

    
    public AprsParser(ServerAPI a, MessageProcessor msg) 
    {
        _api = a;
        _msg = msg;
        _hmsFormat.setTimeZone(TimeZone.getTimeZone("GMT")); 
    }  
    
    
    
    public void subscribe(AprsHandler subscriber)
      { _subscribers.add(subscriber); }
      
    public void unsubscribe(AprsHandler subscriber)
      { _subscribers.remove(subscriber); }
    
    
    
    /**
     * Receive APRS packet. 
     * Duplicate packets are only parsed wrt. path (infrastructure analysis)
     */
    public void receivePacket(AprsPacket p, boolean duplicate)
    {
        if (_api.getDB() == null)
           return; 
                
        if (p.report == null || p.report.length() < 1)
            return;
          
        Station station = _api.getDB().getStation(p.from, null);
        if (station == null)
            station = _api.getDB().newStation(p.from); 
        station.setSource(p.source);
        
        if (!duplicate) try {    
        switch(p.type)
        {
            case '>':
               /* Status report */
               parseStatusReport(p, station);
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
               /* Object report */
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
               
            default: 
        }
        }catch (NumberFormatException e)
          { System.out.println("*** WARNING: Cannot parse number in input. Report string probably malformed"); }
        
        // _api.getAprsHandler().handlePacket(p.source, rtime, p.from, p.to, p.via, p.report);
        for (AprsHandler h:_subscribers)
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
            Station to = _api.getDB().getStation(pp[i], null);
            if ( to != null) {
                n++; 
                if (!skip) { 
                   skip=false;
                   _api.getDB().getRoutes().addEdge(from.getIdent(), to.getIdent(), !duplicate); 
                }
                if (n>1 && i<=tindex) 
                   to.setWideDigi(true); /* Digi is WIDE */
                from = to;
            }
            else if (!pp[i].matches("(WIDE|TRACE|NOR|SAR).*"))
               skip = true;
        }
        
        /* Has packet been gated through APRS-IS?
         * The last node in path is igate
         */
        if ((plen >= 2) && pp[plen-2].matches("qA.")) 
        {
           if (tindex == -1) {
               Station to = _api.getDB().getStation(pp[plen-1], null);
               if (to != null) {
                   _api.getDB().getRoutes().addEdge(s.getIdent(), to.getIdent(), !duplicate);  
                   to.setIgate(true);         
                   /* Igate direct (has not been digipeated) */
               }  
           }
           else {
               Station last = null;
               if (pp[tindex].matches("(WIDE|TRACE|NOR|SAR).*") && tindex > 0)
                  last = _api.getDB().getStation(pp[tindex-1], null);
               Station x = _api.getDB().getStation(pp[plen-1], null);
               if (last != null && x != null) {
                  _api.getDB().getRoutes().addEdge(last.getIdent(), x.getIdent(), !duplicate);
                  x.setIgate(true);
                  /* Path from last digi to igate */
               }
           }
       }
    } 
    
    
    
    /**
     * Parse APRS message.
     */
    private void parseMessage(AprsPacket p, Station station)
    {
        String msgid = null;
        String msg = p.report;
        
        String recipient = msg.substring(1,9).trim();
        int i = msg.lastIndexOf('{');
        if (i >= 0) 
           msgid = msg.substring(i+1, msg.length());
        
        String content = msg.substring(11, (i>=0 ? i : msg.length()));
        if (_msg != null)
           _msg.incomingMessage(station, recipient, content, msgid);  
        for (AprsHandler h:_subscribers)
            h.handleMessage(p.source, new Date(), station.getIdent(), recipient, content);
    }


    /**
     * Parse APRS status report.
     */
    private void parseStatusReport(AprsPacket p, Station station)
    {
        String msg = p.report; 
        Date d = null;
        msg = msg.substring(1);
        if (msg.matches("[0-9]{6}[hz/].*")) {
           d = parseTimestamp(msg, false);
           msg = msg.substring(7);
        }
        // _api.getAprsHandler().handleStatus(src, d, msg);
        for (AprsHandler h:_subscribers)
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
        AprsPoint x = _api.getDB().getItem(ident+'@'+station.getIdent(), null);      
        AprsObject obj;
        if (x == null)
            obj = _api.getDB().newObject(station, ident);
        else 
            obj = (AprsObject) x;
             
        if (op=='*') {
           parseStdAprs(p, msg.substring(10), obj, true, "");
           _api.getDB().deactivateSimilarObjects(ident, station);
        }
        else {
           obj.kill();
           System.out.println("   OBJECT KILL: id="+ident+", op="+op);
        }
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
        AprsPoint x = _api.getDB().getItem(ident+'@'+station.getIdent(), null);      
        AprsObject obj;
        if (x == null)
            obj = _api.getDB().newObject(station, ident);
        else 
            obj = (AprsObject) x;
             
        if (op=='!') {
           parseStdAprs(p, msg.substring(i), obj, false, "");
           _api.getDB().deactivateSimilarObjects(ident, station);
        }
        else {
           obj.kill();
           System.out.println("   ITEM KILL: id="+ident+", op="+op);
        }
    }
    
    
    
    
    /**
     * Parse mic-e data.
     * Based on http://www.aprs-is.net/javAPRS/mice_parser.htm
     */
    private void parseMicE (AprsPacket p, AprsPoint station) 
    {
            String toField = p.to;
            String msg = p.report;
            int j, k;
            Double pos_lat, pos_long; 
            AprsHandler.PosData pd = new AprsHandler.PosData();

            if (toField.length() < 6 || msg.length() < 9) 
                return;
            
            boolean isCustom = ((toField.charAt(0) >= 'A'
                         && toField.charAt(0) <= 'K') || (toField.charAt(1) >= 'A'
                         && toField.charAt(1) <= 'K') || (toField.charAt(2) >= 'A'
                         && toField.charAt(2) <= 'K'));
                         
            for (j = 0; j < 3; j++)
            {
                  if (isCustom)
                  {
                        if (toField.charAt(j) < '0'
                                || toField.charAt(j) > 'L'
                                || (toField.charAt(j) > '9'
                                        && toField.charAt(j) < 'A'))
                                return;
                  }
                  else
                  {
                        if (toField.charAt(j) < '0'
                                || toField.charAt(j) > 'Z'
                                || (toField.charAt(j) > '9'
                                        && toField.charAt(j) < 'L')
                                || (toField.charAt(j) > 'L'
                                        && toField.charAt(j) < 'P'))
                                return;
                  }
            }
            for (;j < 6; j++)
            {
                 if (toField.charAt(j) < '0'
                         || toField.charAt(j) > 'Z'
                         || (toField.charAt(j) > '9'
                                 && toField.charAt(j) < 'L')
                         || (toField.charAt(j) > 'L'
                                 && toField.charAt(j) < 'P'))
                         return;
            }
            if (toField.length() > 6)
            {
                  if (toField.charAt(6) != '-'
                          || toField.charAt(7) < '0'
                          || toField.charAt(7) > '9')
                          return;
                  if (toField.length() == 9)
                  {
                          if (toField.charAt(8) < '0'
                                  || toField.charAt(8) > '9')
                                  return;
                  }
            }
            // Parse the "TO" field
            int c = cnvtDest(toField.charAt(0));
            int mes = 0; 
            if ((c & 0x10) != 0) mes = 0x08; // Set the custom flag
            if (c >= 0x10) mes += 0x04;
            int d = (c & 0xf) * 10;  // Degrees
            c = cnvtDest(toField.charAt(1));
            if (c >= 0x10) mes += 0x02;
            d += (c & 0xf);
            c = cnvtDest(toField.charAt(2));
            if (c >= 0x10) mes++;
            int m = (c & 0xf) * 10;  // Minutes
            c = cnvtDest(toField.charAt(3));
            boolean north = (c >= 0x20);
            m += (c & 0xf);
            c = cnvtDest(toField.charAt(4));
            boolean hund = (c >= 0x20);
            int s = (c & 0xf) * 10;  // Hundredths of minutes
            c = cnvtDest(toField.charAt(5));
            boolean west = (c >= 0x20);
            s += (c & 0xf);

            pos_lat = d + m/60.0 + s/6000.0;
            if (!north) pos_lat *= -1.0;
                    
	    pd.symbol = msg.charAt(7);
	    pd.symtab = msg.charAt(8);
               
            // Parse the longitude
            d = msg.charAt(1)-28;
            m = msg.charAt(2)-28;
            s = msg.charAt(3)-28;
                    
            if (d < 0 || d > 99
                    || m < 0 || m > 99
                    || s < 0 || s > 99)
                    return;

            // Adjust the degrees value
            if (hund) d += 100;
            if (d >= 190) d -= 190;
            else if (d >= 180) d -= 80;
                    
            // Adjust minutes 0-9 to proper spot
            if (m >= 60) m -= 60;
                    
            pos_long = d + m/60.0 + s/6000.0;
            if (west) pos_long *= -1.0;

            pd.pos = new LatLng(pos_lat, pos_long);
                    
            // Parse the Speed/Course (s/d)
            m = msg.charAt(5)-28;  // DC+28
            if (m < 0 || m > 97) return;
            s = msg.charAt(4)-28;
            if (s < 0 || s > 99) return;
            s = (s*10) + (m/10);  //Speed (Knots)
            d = msg.charAt(6)-28;
            if (d < 0 || d > 99) return;
            d = ((m%10)*100) + d;  // Course
            // Specification decoding method
            if (s>=800) s -= 800;
            if (d>=400) d -= 400;
            pd.course = d;
            
            pd.speed = (int) Math.round(s * 1.852);          // Km / h   
            pd.altitude = -1; 
            String comment = null;
            if (msg.length() > 9)
            {  
               char typecode = msg.charAt(9);
               j = msg.indexOf('}', 9);
               if (j >= 9 + 3) {
                  pd.altitude = (int)Math.round(((((((msg.charAt(j-3)-33)*91) 
                                               + (msg.charAt(j-2)-33))*91) 
                                               + (msg.charAt(j-1)-33))-10000));
                  if (msg.length() > j) 
                      comment = msg.substring(j+1); 
               }
               else 
                  comment = msg.substring(10);
                  
               if (typecode==' ');
               if (typecode==']' || typecode=='>') {
                  if (comment.length() > 0 && comment.charAt(comment.length() -1) == '=')
                     comment = comment.substring(0, comment.length()-1);
               }   
               else if (typecode=='`' || typecode == '\'') {
                 if (comment.length() < 2);
                 else if (typecode=='`' &&  comment.charAt(comment.length() -1)=='_')
                    comment = comment.substring(0, comment.length()-1);
                 else
                    comment = comment.substring(0, comment.length()-2);
               }
               else if (pd.altitude == -1)
                    comment = typecode+comment;
            }     
            if (comment != null){
                comment = comment.trim();   
                if (comment.length() == 0)
                   comment = null;
            }     
            
            
            station.update(new Date(), pd, comment, p.via);   
            for (AprsHandler h:_subscribers)
               h.handlePosReport(p.source, station.getIdent(), new Date(), pd, comment, p.report );
            return;
    }




    private int cnvtDest (int inchar)
    {
            int c = inchar - 0x30;           // Adjust all to be 0 based
            if (c == 0x1c) c = 0x0a;         // Change L to be a space digit
            if (c > 0x10 && c <= 0x1b) --c;  // A-K need to be decremented
            
            // Space is converted to 0
            // as javAPRS does not support ambiguity
            if ((c & 0x0f) == 0x0a) c &= 0xf0;
            return c;
    }
    
    
    
     
    /**
     * Parse timestamp.
     * 'Compression' of timestamp is experimental. It is not part of 
     * the standard APRS protocol
     */
    private static Date parseTimestamp(String data, boolean compressed)
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
            System.out.println("*** WARNING: Timestamp format problem: "+dstr+tst);
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
      
    
    
    
    private AprsHandler.PosData parseCompressedPos(String data)
    {
          AprsHandler.PosData pd = new AprsHandler.PosData();
          double latDeg, lngDeg;
          pd.symtab = data.charAt(0);
          pd.symbol = data.charAt(9);
          byte[] y = data.substring(1,5).getBytes();
          byte[] x = data.substring(5,9).getBytes();
          byte[] csT = data.substring(10,13).getBytes();
          latDeg = 90.0 - (((double)(y[0]-33))*753571 + ((double)(y[1]-33))*8281 + 
                           ((double)(y[2]-33))*91 + ((double) y[3])-33) / 380926;
          lngDeg = -180 + (((double)(x[0]-33))*753571 + ((double)(x[1]-33))*8281 + 
                           ((double)(x[2]-33))*91 + ((double) x[3])-33) / 190463;
          
          if (((csT[2]-33) & 0x18) == 0x10) 
          {
             /* Altitude */
             pd.altitude = Math.round( Math.pow(1.002, (csT[0]-33)*91 + (csT[1]-33)));
             pd.altitude *= 0.3048;
          }
          else if (csT[0] >= 0+33 && csT[0] <= 89+33) 
          { 
             /* Course/speed */
             pd.course = (csT[0]-33) * 4;
             pd.speed = (int) Math.round(( Math.pow(1.08, csT[1]-33) - 1) * 1.852 );
          } 
          pd.pos = new LatLng(latDeg, lngDeg);
          return pd;
    }
    
    

    private void parseExtraReport(Source src, String data, AprsPoint station)
    {
       Date time = new Date();
       time = parseTimestamp(data.substring(0), true);
       AprsHandler.PosData pd = parseCompressedPos(data.substring(3));
       if (AprsChannel._dupCheck.checkTS(station.getIdent(), time))
            return;
            
       station.update(time, pd, "", "(EXT)" );        
       // _api.getAprsHandler().handlePosReport(src, station.getIdent(), time, pd, "", "(EXT)" );
       for (AprsHandler h:_subscribers)
           h.handlePosReport(src, station.getIdent(), time, pd, "", "(EXT)" );
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
         
         double latDeg, lngDeg;
         String comment;
         
         /*
          * Now, extract position info and comment
          */     
         AprsHandler.PosData pd;
         Matcher m = _stdPat.matcher(data);
         if (m.matches())
         {
             pd = new AprsHandler.PosData();
             
             String lat     = m.group(1);
             char   latNS   = m.group(4).charAt(0);
             pd.symtab  = m.group(5).charAt(0);
             String lng     = m.group(6);
             char   lngEW   = m.group(9).charAt(0);
             pd.symbol  = m.group(10).charAt(0);
             comment = m.group(11);
             
             if (!lat.matches("[0-9\\s]{4}.[0-9\\s]{2}") || !lng.matches("[0-9\\s]{5}.[0-9\\s]{2}")) {
                /* ERROR: couldn't understand Lat/Long field */
                 return; 
             }
             
             if (lat.charAt(6) == ' ') {
                 pd.ambiguity = 1; 
                 if (lat.charAt(2) == ' ')
                   pd.ambiguity = 4; 
                 else if (lat.charAt(3) == ' ')
                   pd.ambiguity = 3; 
                 else if (lat.charAt(5) == ' ')
                   pd.ambiguity = 2;
             } 
             lat = lat.replaceFirst(" ", "5");
             lng = lng.replaceFirst(" ", "5");
             lat = lat.replaceAll(" ", "0");
             lng = lng.replaceAll(" ", "0");
             
             latDeg = Integer.parseInt(lat.substring(0,2)) + Double.parseDouble(lat.substring(2,7))/60;
             if (latNS == 'S')
                latDeg *= -1;
             lngDeg = Integer.parseInt(lng.substring(0,3)) + Double.parseDouble(lng.substring(3,8))/60;
             if (lngEW == 'W')
               lngDeg *= -1;
             
             if (latDeg < -90 || latDeg > 90 || lngDeg < -180 || lngDeg > 180)
                /* ERROR: LatLong coordinates out of range */
                return; 
             pd.pos = new LatLng(latDeg, lngDeg);  
          }
          
          
          else if (data.matches("[\\\\/][\\x21-\\x7f]{12}.*"))
           /* Parse compressed position report */
          {
              pd = parseCompressedPos(data);
              comment = data.substring(13);
          }
          else 
             /* ERROR: couldnt understand data field */ 
              return;        
          
          if (pd.symbol == '_')
              comment = parseWX(comment);
              
          /* Get course and speed */    
          else if (comment.length() >= 7 && comment.substring(0,7).matches("[0-9]{3}/[0-9]{3}"))
          {
              pd.course = Integer.parseInt(comment.substring(0,3));
              pd.speed  = (int) Math.round( Integer.parseInt(comment.substring(4,7))* 1.852);
              comment = comment.substring(7);
              
              /* Ignore additional Bearing/NRQ fields */
              if (comment.length() >= 8 && comment.substring(0,8).matches("/[0-9]{3}/[0-9]{3}"))
                 comment = comment.substring(8);
              else if (comment.length() >= 8 && comment.substring(0,8).matches("/(\\.\\.\\./\\.\\.\\.)"))
                 /* Ignore */ ;
          } 
          else if (comment.length() >= 7 && comment.substring(0,7).matches("/(\\.\\.\\./\\.\\.\\.)"))
              /* ignore */ ;
          else if (comment.length() >= 7 && comment.substring(0,7).matches("PHG[0-9]{4}"))
          {
             int power = (comment.charAt(3)-'0');
             power = power * power; 
             int gain = (comment.charAt(5)-'0');
             int ht = (int) Math.round(10 * Math.pow(2, (comment.charAt(4)-'0')));
             int r = (int) Math.round(Math.sqrt(2*ht*Math.sqrt((power/10)*(gain/2))));
             int rKm = (int) Math.round(r*1.609344);
             
             String dir;
             switch (comment.charAt(6))
             {
                case '1': dir = " NE";
                case '2': dir = " E";
                case '3': dir = " SE";
                case '4': dir = " S";
                case '5': dir = " SW";
                case '6': dir = " W";
                case '7': dir = " NW";
                case '8': dir = " N";
                default : dir = "";
             }
             comment = comment.substring(7,comment.length()) + " ("+power+" watt, "+gain+" dB"+dir+
                   (rKm>0 ? " => "+rKm+" km" : "") + ")"; 
          }
          else if (comment.length() >= 7 && comment.substring(0,7).matches("RNG[0-9]{4}"))
          {
             int r = Integer.parseInt(comment.substring(3,7));
             int rKm = (int) Math.round(r*1.609344);
             comment = comment.substring(7, comment.length()) + " ("+rKm+" km omni)";
          }
          
          
          /* Altitude */
          if (comment.length() >= 9 && comment.substring(0,9).matches("/A=[0-9]{6}"))
          {
              pd.altitude = Long.parseLong(comment.substring(3,9));
              pd.altitude *= 0.3048;
              comment = comment.substring(9, comment.length());
          }
          
          /* Extra posreports (experimental) 
           * Format: "/#" + compressed timestamp + compressed report */
          while (comment.length() >= 18 && comment.matches("(/\\#.{16})+.*"))
          {
              parseExtraReport(p.source, comment.substring(2,18), station); 
              comment = comment.substring(18, comment.length());
          }
          
          
          if (comment.length() > 0 && comment.charAt(0) == '/') 
             comment = comment.substring(1);  
          comment = comment.trim();
          if (comment.length() < 1 || comment.equals(" "))
             comment = null;
             
           station.update(time, pd, comment, pathinfo );              
           for (AprsHandler h:_subscribers)
              h.handlePosReport(p.source, station.getIdent(), time, pd, comment, pathinfo ); 
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
                   (speed>0 ? String.format(", Vind=%.1f m/s %s", speed, dir) : "");
        }
        return data;
    }
    
}
