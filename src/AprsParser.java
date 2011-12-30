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
public class AprsParser implements Channel.Receiver
{ 

    /* Part of station class instead? */
    private static class PosData {
       public LatLng pos;
       public int course = -1;
       public int speed = -1;
       public char symbol, symtab;
       public long altitude = -1; 
    }
    
    
    /* Standard APRS position report format */
    private static Pattern _stdPat = Pattern.compile
       ("(\\d+\\.\\d+)([NS])(\\S)(\\d+\\.\\d+)([EW])(\\S)\\s*(.*)");

    /* Weather report format */
    private static Pattern _wxPat = Pattern.compile
       ("(\\d\\d\\d|...)/(\\d\\d\\d|...)g(\\d\\d\\d|...)t(\\d\\d\\d|...).*");
                
    private static DateFormat _dtgFormat = new SimpleDateFormat("ddHHmm");
    private static DateFormat _dhmsFormat = new SimpleDateFormat("dd HH:mm:ss");
    
    // FIXME: These are also defined in HttpServer.java
    public static Calendar utcTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.getDefault());
    public static Calendar localTime = Calendar.getInstance();
    
       
    // FIXME: Handle ambiguity in latitude ?
     
    private StationDB _db   = null;
    private MessageProcessor _msg;
 

    public AprsParser(StationDB db, MessageProcessor msg) 
    {
        _db = db;
        _msg = msg;
        _dhmsFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    }  
       
       
    /**
     * Receive APRS packet. 
     * Duplicate packets are only parsed wrt. path (infrastructure analysis)
     */
    public void receivePacket(Channel.Packet p, boolean duplicate)
    {
        if (_db == null)
           return; 
                
        if (p.report == null || p.report.length() < 1)
            return;
          
        Station station = _db.getStation(p.from);
        if (station == null)
            station = _db.newStation(p.from); 
   
        if (!duplicate) try {    
        switch(p.type)
        {
            case '>':
               /* Status report */
               parseStatusReport(p.report, station);
               break;
               
            case '!': 
            case '=':
               /* Real time position report */ 
               parseStdAprs(p.report, station, false, p.via);
               break;
              
            case '@':
            case '/': 
               /* Timestamped position report */   
               parseStdAprs(p.report, station, true, p.via);
               break;
               
            case ';': 
               /* Object report */
               parseObjectReport(p.report, station);
               
            case '\'':
            case '`': 
               /* Mic-E report */
               parseMicE(p.to, p.report, station, p.via); 
               break;
               
            case ':':
               /* Message */
               parseMessage(p.report, station);
               break;
               
            default: 
        }
        }catch (NumberFormatException e)
          { System.out.println("*** WARNING: Cannot parse number in input. Report string probably malformed"); }
        
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
            Station to = _db.getStation(pp[i]);
            if ( to != null) {
                n++; 
                if (!skip) { 
                   skip=false;
                   _db.getRoutes().addEdge(from.getIdent(), to.getIdent(), !duplicate); 
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
               Station to = _db.getStation(pp[plen-1]);
               if (to != null) {
                   _db.getRoutes().addEdge(s.getIdent(), to.getIdent(), !duplicate);  
                   to.setIgate(true);         
                   /* Igate direct (has not been digipeated) */
               }  
           }
           else {
               Station last = null;
               if (pp[tindex].matches("(WIDE|TRACE|NOR|SAR).*") && tindex > 0)
                  last = _db.getStation(pp[tindex-1]);
               Station x = _db.getStation(pp[plen-1]);
               if (last != null && x != null) {
                  _db.getRoutes().addEdge(last.getIdent(), x.getIdent(), !duplicate);
                  x.setIgate(true);
                  /* Path from last digi to igate */
               }
           }
       }
    } 
    
    
    
    /**
     * Parse APRS message.
     */
    private void parseMessage(String msg, Station station)
    {
        String msgid = null;
        String recipient = msg.substring(1,9).trim();
        int i = msg.lastIndexOf('{');
        if (i >= 0) 
           msgid = msg.substring(i+1, msg.length());
        
        String content = msg.substring(11, (i>=0 ? i : msg.length()));
        if (_msg != null)
           _msg.incomingMessage(station, recipient, content, msgid);
    }


    /**
     * Parse APRS status report.
     */
    private void parseStatusReport(String msg, Station station)
    {
        Date d = null;
        msg = msg.substring(1);
        if (msg.matches("[0-9]{6}[hz/].*")) {
           d = parseTimestamp(msg, false);
           msg = msg.substring(7);
        }
        station.setStatus(d, msg);
    }
    
    
    
    /**
     * Parse APRS object report.
     */
    private void parseObjectReport(String msg, Station station)
    {
        String ident = msg.substring(1,10).trim();
        char op = msg.charAt(10);
        
        AprsPoint x = _db.getItem(ident+'@'+station.getIdent());      
        AprsObject obj;
        if (x == null)
            obj = _db.newObject(station, ident);
        else 
            obj = (AprsObject) x;
             
        if (op=='*') {
           parseStdAprs(msg.substring(10), obj, true, "");
           _db.deactivateSimilarObjects(ident, station);
        }
        else 
           obj.kill();
    }
    
    
    
    
    /**
     * Parse mic-e data.
     * Based on http://www.aprs-is.net/javAPRS/mice_parser.htm
     */
    private void parseMicE (String toField, String msg, AprsPoint station, String pathinfo) 
    {
            int j, k;
            Double pos_lat, pos_long; 


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
                    
	    char symbol = msg.charAt(7);
	    char altsym = msg.charAt(8);
               
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

            LatLng pos = new LatLng(pos_lat, pos_long);
                    
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
            
            int speed = (int) Math.round(s * 1.852);          // Km / h   
            int altitude = -1; 
            String comment = null;
            if (msg.length() > 9)
            {  
               char typecode = msg.charAt(9);
               j = msg.indexOf('}', 9);
               if (j >= 9 + 3) {
                  altitude = (int)Math.round(((((((msg.charAt(j-3)-33)*91) 
                                               + (msg.charAt(j-2)-33))*91) 
                                               + (msg.charAt(j-1)-33))-10000)*3.28084);
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
               else if (altitude == -1)
                    comment = typecode+comment;
            }     
            if (comment != null){
                comment = comment.trim();   
                if (comment.length() == 0)
                   comment = null;
            }          
            station.update(new Date(), pos, d, speed, altitude, comment, symbol, altsym, pathinfo);  
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
             day = ts.get(Calendar.DAY_OF_MONTH);
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
        
         /* Try to figure out the month and the year of the timestamp. 
          * if timestamp day number is higher than todays number, the timestamp 
          * is probably from previous month. If it is january 1st, adjust year
          * accordingly
          */
         ts.set(Calendar.YEAR, now.get(Calendar.YEAR));
         ts.set(Calendar.MONTH, now.get(Calendar.MONTH)); 
         if (day > now.get(Calendar.DAY_OF_MONTH))
         {      
             ts.add(Calendar.MONTH, -1);
             if (now.get(Calendar.MONTH) == Calendar.JANUARY) 
                ts.add(Calendar.YEAR, -1);
         }
         ts.set(Calendar.DAY_OF_MONTH, day);
         if (ts.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) 
             && hour > now.get(Calendar.HOUR_OF_DAY)+2) 
            ts.add(Calendar.DAY_OF_MONTH, -1);
            
         ts.set(Calendar.HOUR_OF_DAY, hour);
         ts.set(Calendar.MINUTE, min);
         ts.set(Calendar.SECOND, sec);
             
         return ts.getTime();
    }
    
    
    
    private PosData parseCompressedPos(String data)
    {
          PosData pd = new PosData();
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
    
    

    private void parseExtraReport(String data, AprsPoint station)
    {
       Date time = new Date();
       time = parseTimestamp(data.substring(0), true);
       PosData pd = parseCompressedPos(data.substring(3));
       if (Channel._dupCheck.checkTS(station.getIdent(), time))
            return;
       
       station.update(time, pd.pos, pd.course, pd.speed, (int) pd.altitude, "", pd.symbol, pd.symtab, "(EXT)" );
    }
    
    
    
    /** 
     * Parse standard position data.
     */
    private void parseStdAprs(String data, AprsPoint station, boolean timestamp, String pathinfo)
    {
           Date time = new Date();
          
           if (timestamp) {
              if (data.substring(1).matches("[0-9]{6}h.*")) 
                  time = parseTimestamp(data.substring(1), false);   
              data = data.substring(8);
              /* A duplicate check on timestamp itself */
              if (Channel._dupCheck.checkTS(station.getIdent(), time))
                 return;
           }
           else
              data = data.substring(1);
           
           double latDeg, lngDeg;
           String comment;
           
           /*
            * Now, extract position info and comment
            */     
           PosData pd;
           Matcher m = _stdPat.matcher(data);
           if (m.matches())
           {
               pd = new PosData();
               String lat     = m.group(1);
               char   latNS   = m.group(2).charAt(0);
               pd.symtab  = m.group(3).charAt(0);
               String lng     = m.group(4);
               char   lngEW   = m.group(5).charAt(0);
               pd.symbol  = m.group(6).charAt(0);
               comment = m.group(7);
    
               if (lat.length() < 7 || lng.length() < 8)
                   /* ERROR: couldnt understand data field */ 
                   return;        

               latDeg = Integer.parseInt(lat.substring(0,2)) + Double.parseDouble(lat.substring(2,7))/60;
               if (latNS == 'S')
                  latDeg *= -1;
               lngDeg = Integer.parseInt(lng.substring(0,3)) + Double.parseDouble(lng.substring(3,8))/60;
               if (lngEW == 'W')
                 lngDeg *= -1;
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
                parseExtraReport(comment.substring(2,18), station); 
                comment = comment.substring(18, comment.length());
            }
            
            
            if (comment.length() > 0 && comment.charAt(0) == '/') 
               comment = comment.substring(1);  
            comment = comment.trim();
            if (comment.length() < 1 || comment.equals(" "))
               comment = null;
            station.update(time, pd.pos, pd.course, pd.speed, (int) pd.altitude, comment, pd.symbol, pd.symtab, pathinfo );      
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
