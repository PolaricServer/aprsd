/* 
 * Copyright (C) 2016-2025 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import java.math.BigInteger;


/**
 * Parse and interpret APRS datastreams.
 */
public class AprsUtil
{ 

   /* Standard APRS position report format */
   protected static Pattern _stdPat = Pattern.compile
       ("((\\d|\\s)+[\\.\\,](\\d|\\s)+)([NS])(\\S)((\\d|\\s)+[\\.\\,](\\d|\\s)+)([EW])(\\S)\\s*(.*)");

    /* Weather report format */
   protected static Pattern _wxPat = Pattern.compile
       ("(\\d\\d\\d|...)/(\\d\\d\\d|...)g(\\d\\d\\d|...)t(\\d\\d\\d|...).*");
                
   /* Telemetry in comment format */
   protected static Pattern _telPat = Pattern.compile
       (".*\\|([\\x21-\\x7f]{4,14})\\|.*");
    
    
   protected static DateFormat _dtgFormat = new SimpleDateFormat("ddhhmm");
   protected static DateFormat _hmsFormat = new SimpleDateFormat("hhmmss");
    
   // FIXME: These are also defined in HttpServer.java
   protected static Calendar utcTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.getDefault());
   protected static Calendar localTime = Calendar.getInstance();
    
       
   // FIXME: Handle ambiguity in latitude ?
     
   protected static ServerAPI _api   = null;
    
    
    
   /* Consider Moving this to AprsdPacket */ 
   public static String[] getQcode(AprsPacket p) {
      String[] vias = p.via.split(",(\\s)*");
      for (int i=0; i<vias.length; i++) 
         if (vias[i].charAt(0) == 'q') {
            String[] ret = new String[2];
            ret[0] = vias[i]; 
            if (i+1 < vias.length)
               ret[1] = vias[i+1];
            return ret; 
         }
      return null;
   }
    
    
    
   /* Consider moving this to AprsPacket */
   public static Point getPos(AprsPacket p) 
   {
      ReportHandler.PosData pd = 
         switch(p.type) {
            case '!', '=' -> _parseStd(p, false);
            case '@', '/' -> _parseStd(p, true);
            case '\'', '`' ->  parseMicEPos(p, null);
            default -> null;
         };
      if (pd==null)
         return null;
      return new Point(pd.pos);
   }
   
   
   
   private static ReportHandler.PosData _parseStd(AprsPacket p, boolean ts) 
   {
      String data = p.report;
      if (ts) 
         data = data.substring(8);
      else
         data = data.substring(1);
         
      if (data.matches("[\\\\/0-9A-Z][\\x20-\\x7f]{12}.*"))
         return parseCompressedPos(data);
      else
         return parseStdPos(data, null); 
   }
   
   
   
    
   protected static ReportHandler.PosData parseMicEPos(AprsPacket p, String[] cm) 
   {
        String toField = p.to;
        String msg = p.report;
        int j, k;
        Double pos_lat, pos_long; 
        ReportHandler.PosData pd = new ReportHandler.PosData();

        if (toField.length() < 6 || msg.length() < 9) 
            return null;
        
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
                            return null;
              }
              else
              {
                    if (toField.charAt(j) < '0'
                            || toField.charAt(j) > 'Z'
                            || (toField.charAt(j) > '9'
                                    && toField.charAt(j) < 'L')
                            || (toField.charAt(j) > 'L'
                                    && toField.charAt(j) < 'P'))
                            return null;
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
                     return null;
        }
        if (toField.length() > 6)
        {
              if (toField.charAt(6) != '-'
                      || toField.charAt(7) < '0'
                      || toField.charAt(7) > '9')
                      return null;
              if (toField.length() == 9)
              {
                      if (toField.charAt(8) < '0'
                              || toField.charAt(8) > '9')
                              return null;
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
                
        if (d < 0 || d > 199
                || m < 0 || m > 120
                || s < 0 || s > 120)
                return null;

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
        if (m < 0 || m > 97) return null;
        s = msg.charAt(4)-28;
        if (s < 0 || s > 99) return null;
        s = (s*10) + (m/10);  //Speed (Knots)
        d = msg.charAt(6)-28;
        if (d < 0 || d > 99) return null;
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
        if (cm != null) 
            cm[0] = comment;
        return pd;
   }
   
   
    


   protected static int cnvtDest (int inchar)
   {
         int c = inchar - 0x30;           // Adjust all to be 0 based
         if (c == 0x1c) c = 0x0a;         // Change L to be a space digit
         if (c > 0x10 && c <= 0x1b) --c;  // A-K need to be decremented
            
         // Space is converted to 0
         // as javAPRS does not support ambiguity
         if ((c & 0x0f) == 0x0a) c &= 0xf0;
         return c;
   }
    
    
        
   protected static long base91Decode(byte c0, byte c1, byte c2, byte c3)
   {
        if (c0<33) c0=33; 
        if (c1<33) c1=33;
        if (c2<33) c2=33;
        if (c3<33) c3=33;   
        return (c0-33) * 753571 + (c1-33) * 8281 + (c2-33) * 91 + (c3-33);
   }
    

    
    protected static ReportHandler.PosData parseCompressedPos(String data)
    {
          ReportHandler.PosData pd = new ReportHandler.PosData();
          double latDeg, lngDeg;
          pd.symtab = data.charAt(0);
          pd.symbol = data.charAt(9);
          byte[] y = data.substring(1,5).getBytes();
          byte[] x = data.substring(5,9).getBytes();
          byte[] csT = data.substring(10,13).getBytes();
          
          latDeg = 90.0 - ((double) base91Decode(y[0], y[1], y[2], y[3])) / 380926; 
          lngDeg = -180 + ((double) base91Decode(x[0], x[1], x[2], x[3])) / 190463;
     
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
    
 
     
    
    protected static ReportHandler.PosData parseStdPos(String data, Matcher m)
    {
      if (m==null) {   
         m = _stdPat.matcher(data);
         if (!m.matches())
            return null;
      }
      
      ReportHandler.PosData pd = new ReportHandler.PosData();
      double latDeg, lngDeg;
      String lat     = m.group(1);
      char   latNS   = m.group(4).charAt(0);
      pd.symtab  = m.group(5).charAt(0);
      String lng     = m.group(6);
      char   lngEW   = m.group(9).charAt(0);
      pd.symbol  = m.group(10).charAt(0);
      String comment = m.group(11);   // FIXME: Move out
      
      if (!lat.matches("[0-9\\s]{4}.[0-9\\s]{2}") || !lng.matches("[0-9\\s]{5}.[0-9\\s]{2}")) {
         /* ERROR: couldn't understand Lat/Long field */
         _api.log().debug("AprsParser", "Could not parse lat/long field: "+lat+" "+lng);
          return null; 
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
      lat = lat.replaceAll(",", ".");
      lng = lng.replaceAll(",", ".");
      
      latDeg = Integer.parseInt(lat.substring(0,2)) + Double.parseDouble(lat.substring(2,7))/60;
      if (latNS == 'S')
         latDeg *= -1;
      lngDeg = Integer.parseInt(lng.substring(0,3)) + Double.parseDouble(lng.substring(3,8))/60;
      if (lngEW == 'W')
        lngDeg *= -1;
      
      if (latDeg < -90 || latDeg > 90 || lngDeg < -180 || lngDeg > 180)
         /* ERROR: LatLong coordinates out of range */
         return null; 
      pd.pos = new LatLng(latDeg, lngDeg);  
      return pd; 
   }
    
   
   protected static int b64to12bit(String s) {
      com.mindprod.base64.Base64 b64 = new com.mindprod.base64.Base64();
      byte[] bytes = b64.decode(s+"AA");
      return (new BigInteger(bytes)).intValue() >> 12;
   }
    
    
   protected static int b64to18bit(String s) {
        com.mindprod.base64.Base64 b64 = new com.mindprod.base64.Base64();
        byte[] bytes = b64.decode(s+"A");
        int x =  (new BigInteger(bytes)).intValue() >> 6;
        
        if ((bytes[1] & 0x02) > 0) {
            /* Negative number - turn it to a 32 bit negative number */
            bytes[0] = (byte) 0xff;
            bytes[1] |= 0xfc;
        } 
        return x;
   }
    
    
}
