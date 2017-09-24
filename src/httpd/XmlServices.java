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
 
package no.polaric.aprsd.http;
import no.polaric.aprsd.*;
import no.polaric.aprsd.filter.*;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.io.PrintStream;

import uk.me.jstott.jcoord.*;
import java.util.*;
import java.io.*;
import java.text.*;
import com.mindprod.base64.Base64;
import java.util.concurrent.locks.*; 
import spark.Request;
import spark.Response;


public class XmlServices extends ServerBase
{
   private String _adminuser, _updateusers;
   private int _seq = 0;
   
   public XmlServices(ServerAPI api) throws IOException
   {
      super(api);
      
      // FIXME: move to another place? 
      int trailage = api.getIntProperty("map.trail.maxAge", 15);
      Trail.setMaxAge(trailage * 60 * 1000); 
      int trailpause = api.getIntProperty("map.trail.maxPause", 10);
      Trail.setMaxPause(trailpause * 60 * 1000);
      int trailage_ext = api.getIntProperty("map.trail.maxAge.extended", 30);
      Trail.setMaxAge_Ext(trailage_ext * 60 * 1000); 
      int trailpause_ext = api.getIntProperty("map.trail.maxPause.extended", 20);
      Trail.setMaxPause_Ext(trailpause_ext * 60 * 1000);
   }


   
   /**
    * Look up a station/object and return id, x and y coordinates (separated by commas).
    * If not found, return nothing.
    */
   public String handle_finditem(Request req, Response res)
       throws IOException
   { 
       CharArrayWriter buf = new CharArrayWriter();
       PrintWriter out = new PrintWriter(buf); 

       String ident = req.queryParams("id").toUpperCase();
       TrackerPoint s = _api.getDB().getItem(ident, null);
       if (s==null) {
          int i = ident.lastIndexOf('-');
          if (i > -1)    
             ident = ident.substring(0, i);
          List<TrackerPoint> l = _api.getDB().getAllPrefix(ident);
          if (l.size() > 0)
              s = l.get(0);
       }
       if (s!=null && !s.expired() && s.getPosition() != null) {
          LatLng xpos = s.getPosition().toLatLng(); 
          out.println(s.getIdent()+"," + roundDeg(xpos.getLng()) + "," + roundDeg(xpos.getLat()));   
       }
       res.type("text/csv; charset=utf-8");
       out.close();
       return buf.toString(); 
   }
   
   
}
