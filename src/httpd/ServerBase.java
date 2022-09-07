/* 
 * Copyright (C) 2016 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
 
package no.polaric.aprsd.http;
import no.polaric.aprsd.*;
import no.polaric.aprsd.filter.*;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.io.PrintStream;

import uk.me.jstott.jcoord.*;
import java.util.*;
import java.util.function.*;
import java.io.*;
import java.text.*;
import com.mindprod.base64.Base64;
import java.util.concurrent.locks.*; 
import spark.*;
import com.fasterxml.jackson.databind.*;


// FIXME: Move some of this to a ServerConfig object. Move out the XML stuff (remove it later). 

public abstract class ServerBase 
{
   protected  ServerAPI  _api;
   private    String     _timezone;
   protected  String     _wfiledir;
   private    String     _adminuser, _updateusers;
   protected  String     _icon;    
   
   public static final String _encoding = "UTF-8";

   static DateFormat df = new SimpleDateFormat("dd MMM. HH:mm",
            new DateFormatSymbols(new Locale("no")));
   static DateFormat tf = new SimpleDateFormat("HH:mm",
            new DateFormatSymbols(new Locale("no")));
   static DateFormat xf = new SimpleDateFormat("yyyyMMddHHmmss",
            new DateFormatSymbols(new Locale("no")));       
   static DateFormat isodf = 
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
            
   public static Calendar utcTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.getDefault());
   public static Calendar localTime = Calendar.getInstance();
   
   
      /* Jackson JSON mapper */ 
   protected final static ObjectMapper mapper = new ObjectMapper();
   
   
   public static String toJson(Object obj) 
       { return serializeJson(obj); }
 
 
    public static Object fromJson(String text, Class cls) 
       { return deserializeJson(text, cls); }
 
 
    public static String serializeJson(Object obj) {
        try {
            mapper.setDateFormat(isodf);
            return mapper.writeValueAsString(obj);
        }
        catch (Exception e) {
            return null;
        }
    }
   
   
    public static Object deserializeJson(String text, Class cls) {
        try {
            return mapper.readValue(text, cls);
        }
        catch (Exception e) {
            return null;
        }
    }
    
    
    public static String cleanPath(String txt) { 
        if (txt==null)
            return "";
        return txt.replaceAll("((WIDE|TRACE|SAR|NOR)[0-9]*(\\-[0-9]+)?\\*?,?)|(qA.),?", "")
           .replaceAll("\\*", "").replaceAll(",+|(, )+", ", ");
    }
    
    
   
   /* TTL is in minutes */
   public void systemNotification(String user, String txt, int ttl) {
        _api.getWebserver().notifyUser(user, 
            new ServerAPI.Notification("system", "system", txt, new Date(), ttl) );  
   }
   
   
   
   
   public ServerBase(ServerAPI api) 
   {
      _api=api; 
      _wfiledir    = api.getProperty("map.web.dir", "aprsd");
      _icon        = api.getProperty("map.icon.default", "sym.gif");
      _timezone    = api.getProperty("timezone", "");
      _adminuser   = api.getProperty("user.admin", "admin");
      _updateusers = api.getProperty("user.update", "");
                
      TimeZone.setDefault(null);
      if (_timezone.length() > 1) {
          TimeZone z = TimeZone.getTimeZone(_timezone);
          localTime.setTimeZone(z);
          df.setTimeZone(z);
          tf.setTimeZone(z);
      }
   }
   
   
   protected ServerAPI getApi() 
       { return _api; }
   
   
   protected static double roundDeg(double x)
       { return ((double) Math.round(x*100000)) / 100000; 
       }
  


   /**
    * Convert reference to UTM projection with our chosen zone.
    * Return null if it cannot be converted to our zone (too far away).
    */
   protected UTMRef toUTM(Reference ref, int utmz)
   {
        try { return ref.toLatLng().toUTMRef(utmz); }
        catch (Exception e)
           { _api.log().warn("ServerBase", "Cannot convert to UTM"+ utmz+ " : "+ref);
             return null; }
   }
   
   
       
    /*
     * Log, notify admin and other server about change of alias 
     */
    protected void notifyAlias(String ident, String alias, Request req) {
        var uid = getAuthInfo(req).userid; 
        if (alias==null)
            alias = "NULL";
        if (_api.getRemoteCtl() != null)
            _api.getRemoteCtl().sendRequestAll("ALIAS", ident+" "+alias, null);
        _api.log().info("SystemApi", 
            "ALIAS: '"+alias+"' for '"+ident+"' by user '"+uid+"'");    
        _api.getWebserver().notifyUser(uid, new ServerAPI.Notification
            ("system", "system", "Alias: '"+alias+ "' for '"+ident+"' set by user '"+uid+"'", new Date(), 10) );     
    }
    

    /*
     * Log, notify admin and other server about change of icon 
     */
    protected void notifyIcon(String ident, String icon, Request req) {
        var uid = getAuthInfo(req).userid;
        if (icon==null)
            icon="NULL";
        if (_api.getRemoteCtl() != null)
            _api.getRemoteCtl().sendRequestAll("ICON", ident+" "+icon, null);
        _api.log().info("SystemApi", 
            "ICON: '"+icon+"' for '"+ident+"' by user '"+uid+"'");    
        _api.getWebserver().notifyUser(uid, new ServerAPI.Notification
            ("system", "system", "Icon: '"+icon+ "' for '"+ident+"' set by user '"+uid+"'", new Date(), 10) );     
    } 
   
    /*
     * Log, notify admin and other server about change of tag 
     */
    protected void notifyTag(String ident, String tag, Request req) {
        var uid = getAuthInfo(req).userid; 
        if (tag==null)
            return;
        if (_api.getRemoteCtl() != null)
            _api.getRemoteCtl().sendRequestAll("TAG", ident+" "+tag, null);
        _api.log().info("SystemApi", 
            "TAG: '"+tag+"' for '"+ident+"' by user '"+uid+"'");    
        if (!"RMANAGED".equals(tag))
            _api.getWebserver().notifyUser(uid, new ServerAPI.Notification
                ("system", "system", "Tag: '"+tag+ "' for '"+ident+"' set by user '"+uid+"'", new Date(), 10) );     
    }
    
    
    protected void notifyRmTag(String ident, String tag, Request req) {
        var uid = getAuthInfo(req).userid; 
        if (tag==null)
            return;
        if (_api.getRemoteCtl() != null)
            _api.getRemoteCtl().sendRequestAll("RMTAG", ident+" "+tag, null);
        _api.log().info("SystemApi", 
            "RMTAG: '"+tag+"' for '"+ident+"' by user '"+uid+"'");    
        if (!"RMANAGED".equals(tag))
            _api.getWebserver().notifyUser(uid, new ServerAPI.Notification
                ("system", "system", "Tag: '"+tag+ "' for '"+ident+"' removed by user '"+uid+"'", new Date(), 10) );     
    }
    
    
   /**
    * Get info about logged-in user and authorization 
    * @return AuthInfo. 
    */
   protected final AuthInfo getAuthInfo(Request req)
      { return WebServer.getAuthInfo(req); }

   

   /**
    * Sanitize text input that can be used in HTML output. 
    */
   protected String fixText(String t)
   {  
        if (t==null)
           return t; 
        StringBuilder sb = new StringBuilder(t);
        int length = t.length();
        for (int i=0; i<length; i++) {
           switch (sb.charAt(i)) {
              case '&': 
                   if (sb.substring(i+1).matches("(amp|lt|gt|quot);.*")) 
                      i = sb.indexOf(";", i)+1;
                   else {
                      sb.insert(i+1,"amp;");
                      i+= 4;
                      length += 4;
                   }
                   break;
                
              case '<': 
                   sb.setCharAt(i,'&');
                   sb.insert(++i, "lt;");
                   length += 3;
                   break;
              case '>': 
                   sb.setCharAt(i,'&');
                   sb.insert(++i, "gt;");
                   length += 3;
                   break;
              case '"':
                   sb.setCharAt(i,'&');
                   sb.insert(++i, "quot;");
                   length += 5;
                   break;
           }        
           if (sb.charAt(i)<' ')
              sb.setCharAt(i, '?');
        }
        return sb.toString(); 
   }
   
  

   protected String showDMstring(double ll)
   {
       int deg = (int) Math.floor(ll);
       double minx = ll - deg;
       if (ll < 0 && minx != 0.0) 
          minx = 1 - minx;
       
       double mins = ((double) Math.round( minx * 60 * 100)) / 100;
       return ""+deg+"\u00B0 "+mins+"'";
   }

   
   
   protected String ll2dmString(LatLng llref)
      { return showDMstring(llref.getLatitude())+"N, "+showDMstring(llref.getLongitude())+"E"; }
   
   

   
   
   
   protected String metaTag(String name, String val) 
      { return "<meta name=\""+name+"\" value=\""+val+"\"/>"; }
   

 
}
