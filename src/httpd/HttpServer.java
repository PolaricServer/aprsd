
package no.polaric.aprsd.http;
import no.polaric.aprsd.*;
import nanohttpd.*;
import uk.me.jstott.jcoord.*;
import java.util.*;
import java.io.*;
import java.text.*;
import com.mindprod.base64.Base64;
import java.util.concurrent.locks.*; 


public abstract class HttpServer extends NanoHTTPD
{
   protected StationDB  _db;
   protected int        _utmzone;
   private String       _timezone;
   protected String     _serverurl;
   private   String     _icon, _icondir, _adminuser, _updateusers;
   protected   boolean    _infraonly;

           
   public static final String _encoding = "UTF-8";
   public static final int _buffer_size = 4096;

   DateFormat df = new SimpleDateFormat("dd MMM. HH:mm",
           new DateFormatSymbols(new Locale("no")));
   DateFormat tf = new SimpleDateFormat("HH:mm",
           new DateFormatSymbols(new Locale("no")));
           
   
   public static Calendar utcTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.getDefault());
   public static Calendar localTime = Calendar.getInstance();
   
   
   public HttpServer(StationDB db, int port, Properties config) throws IOException
   {
      super(port); 
      _db=db; 
      _utmzone     = Integer.parseInt(config.getProperty("map.utm.zone", "33").trim());
      _icon        = config.getProperty("map.icon.default", "sym.gif").trim();
      _icondir     = config.getProperty("map.icon.dir", "icons").trim();
      _infraonly   = config.getProperty("map.infraonly", "false").trim().matches("true|yes");
      _adminuser   = config.getProperty("user.admin","admin").trim();
      _updateusers = config.getProperty("user.update", "").trim();
      _serverurl   = config.getProperty("server.url", "/srv").trim();
      _timezone    = config.getProperty("timezone", "").trim();
         
      int trailage= Integer.parseInt(config.getProperty("map.trail.maxAge", "15").trim());
      History.setMaxAge(trailage * 60 * 1000); 
      int trailpause= Integer.parseInt(config.getProperty("map.trail.maxPause", "10").trim());
      History.setMaxPause(trailpause * 60 * 1000);
      int trailage_ext= Integer.parseInt(config.getProperty("map.trail.maxAge.extended", "30").trim());
      History.setMaxAge_Ext(trailage_ext * 60 * 1000); 
      int trailpause_ext= Integer.parseInt(config.getProperty("map.trail.maxPause.extended", "20").trim());
      History.setMaxPause_Ext(trailpause_ext * 60 * 1000);
            
      TimeZone.setDefault(null);
      if (_timezone.length() > 1) {
          TimeZone z = TimeZone.getTimeZone(_timezone);
          localTime.setTimeZone(z);
          df.setTimeZone(z);
          tf.setTimeZone(z);
      }
   }



   /**
    * Convert reference to UTM projection with our chosen zone.
    * Return null if it cannot be converted to our zone (too far away).
    */
   private UTMRef toUTM(Reference ref)
   {
        try { return ref.toLatLng().toUTMRef(_utmzone); }
        catch (Exception e)
           { System.out.println("*** Kan ikke konvertere til UTM"+_utmzone+" : "+ref);
             return null; }
   }
   
   
   
   /**
    * Get name of user identified using basic authentication.
    * (we assume that there is a front end webserver which already 
    * did the authentication).  
    */ 
   private final String getAuthUser(Properties header)
   {
         String auth = header.getProperty("authorization", null);
         if (auth==null)
            auth = header.getProperty("Authorization", null);
         if (auth != null) {
           Base64 b64 = new Base64();
           byte[] dauth = b64.decode(auth.substring(6));
           return (new String(dauth)).split(":")[0];
         }
         return null;
   }
   


   protected final boolean authorizedForUpdate(Properties header)
   {
       String user = getAuthUser(header);

       if (user == null)
          user="_NOLOGIN_";
       return ( user.matches(_adminuser) ||
                user.matches(_updateusers) );
   }
   

   protected final boolean authorizedForAdmin(Properties header)
   {
       String user = getAuthUser(header);
       return (user != null && user.matches(_adminuser)); 
   } 
   
   
   
   protected int _requests = 0, _reqNo = 0;

   /** 
    * Generic HTTP serve method. Dispatches to other methods based on uri
    */
   public Response serve( String uri, String method, Properties header, Properties parms )
   {
       int reqNo;
       synchronized (this) {
         _requests++; _reqNo++;
         reqNo = _reqNo;
       }
       
       try {
         String type; 
         ByteArrayOutputStream os = new ByteArrayOutputStream(_buffer_size);
         PrintWriter out = new PrintWriter(new OutputStreamWriter(os, _encoding));
         
         /* Determine the View Filter. Currently, there are just two
          * and they are hardcoded. The idea is to use a dictionary of
          * View filters and look them up by name
          */
         String filtid = _infraonly ? "infra" : parms.getProperty("filter");
         ViewFilter vfilt = ViewFilter.getFilter(filtid);
       //  System.out.println("HTTP REQ ["+reqNo+"]: "+uri+", "+parms);
         if ("/admin".equals(uri))
             type = _serveAdmin(header, parms, out);   
         else if ("/status".equals(uri))
             type = _serveStatus(header, parms, out, vfilt);
         else if ("/station".equals(uri))
             type = _serveStation(header, parms, out);
         else if ("/findstation".equals(uri))
             type = serveFindStation(header, parms, out);
         else if ("/history".equals(uri))
             type = _serveStationHistory(header, parms, out);    
         else if ("/mapdata".equals(uri))
             type = serveMapData(header, parms, out, vfilt, filtid);
         else if ("/addobject".equals(uri))
             type = _serveAddObject(header, parms, out);
         else if ("/deleteobject".equals(uri))
             type = _serveDeleteObject(header, parms, out);
         else if ("/resetinfo".equals(uri))
             type = _serveResetInfo(header, parms, out);    
         else 
             return serveFile( uri, header, new File("."), true );
       
         InputStream is = new ByteArrayInputStream(os.toByteArray());   // FIXME: this copies buffer content?       
         Response res = new Response(HTTP_OK, type, is);
         res.addHeader("Content-length", "" + is.available()); 
         return res; 
       }
       catch (Exception e) 
          { System.out.println("*** HTTP REQ exception: "+e.getMessage()); 
            return null;} 
       finally { _requests--; }
   }

   protected abstract String _serveAdmin(Properties header, Properties parms, PrintWriter out);
   protected abstract String _serveStation(Properties header, Properties parms, PrintWriter out);
   protected abstract String _serveAddObject (Properties header, Properties parms, PrintWriter out);
   protected abstract String _serveDeleteObject (Properties header, Properties parms, PrintWriter out);
   protected abstract String _serveResetInfo (Properties header, Properties parms, PrintWriter out);
   protected abstract String _serveStatus (Properties header, Properties parms, PrintWriter out, ViewFilter vf);
   protected abstract String _serveStationHistory (Properties header, Properties parms, PrintWriter out);


    
   protected String fixText(String t)
   {
        t = t.replaceAll("&", "&amp;");   
        t = t.replaceAll("<", "&lt;");
        t = t.replaceAll(">", "&gt;");
        t = t.replaceAll("\"", "&quot;");
        t = t.replaceAll("\\p{Cntrl}", "?");
        return t; 
   }
   
   
   
   /**
    * Look up a station and return id, x and y coordinates (separated by commas)
    * If not found, return nothing.
    */
   public String serveFindStation(Properties header, Properties parms, PrintWriter out)
   { 
       Station s = _db.getStation(parms.getProperty("id").toUpperCase());
       if (s!=null && !s.expired() && s.getPosition() != null) {
          UTMRef xpos = toUTM(s.getPosition()); 
          out.println(s.getIdent()+","+ (long) Math.round(xpos.getEasting()) + "," + (long) Math.round(xpos.getNorthing()));   
       }
       out.flush();
       return "text/csv; charset=utf-8";
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
   



  private int _seq = 0;
   
   /**
    * Produces XML (Ka-map overlay spec.) for plotting station/symbols/labels on map.   
    */
   public String serveMapData(Properties header, Properties parms, PrintWriter out, 
          ViewFilter vfilt, String filt)
   {  
        UTMRef uleft = null, lright = null;
        if (parms.getProperty("x1") != null) {
          long x1 = Long.parseLong( parms.getProperty("x1") );
          long x2 = Long.parseLong( parms.getProperty("x2") );
          long x3 = Long.parseLong( parms.getProperty("x3") );    
          long x4 = Long.parseLong( parms.getProperty("x4") );
          uleft = new UTMRef((double) x1, (double) x2, 'W', _utmzone); /* FIXME: Lat zone */
          lright = new UTMRef((double) x3, (double) x4, 'W', _utmzone);
        }
        
        /* Sequence number at the time of request */
        long seq  = 0;
        synchronized (this) {
          _seq = (_seq+1) % 32000;
          seq = _seq;
        }
        
        /* If requested, wait for a state change (see Notifier.java) */
        if (parms.getProperty("wait") != null) 
            Station.waitChange(uleft, lright);
                
        /* XML header with meta information */           
        out.println("<overlay seq=\""+_seq+"\"" +
            (filt==null ? ""  : " view=\"" + filt + "\"") + ">");
        out.println("<meta name=\"utmzone\" value=\""+ _utmzone + "\"/>");
        out.println("<meta name=\"login\" value=\""+ getAuthUser(header) + "\"/>");
        out.println("<meta name=\"adminuser\" value=\""+ _adminuser.equals(getAuthUser(header)) + "\"/>");
        out.println("<meta name=\"updateuser\" value=\""+ authorizedForUpdate(header) + "\"/>");
            
            
        /* Output signs. A sign is not an APRS object
         * just a small icon and a title. This will probably be removed. 
         */
        int i=0;
        for (Signs.Item s: Signs.getList())
        {
            UTMRef ref = toUTM(s.pos); 
            if (ref == null) continue;
            String title = s.text == null ? "" : "title=\"" + fixText(s.text) + "\"";
            String icon = "srv/icons/"+ s.icon;    
           
            out.println("<point id=\"__sign" + (i++) + "\" x=\""
                         + (int) Math.round(ref.getEasting()) + "\" y=\"" + (int) Math.round(ref.getNorthing())+ "\" " 
                         + title+">");
            out.println("   <icon src=\""+icon+"\"  w=\"22\" h=\"22\" ></icon>");     
            out.println("</point>");    
        }        
        
        /* Output APRS objects */
        for (AprsPoint s: _db.search(uleft, lright)) 
        {
            synchronized (s) 
            {
               if (s.getPosition() == null)
                   continue; 
               if (!vfilt.useObject(s))
                   continue;
                   
               UTMRef ref = toUTM(s.getPosition()); 
               if (ref == null) continue; 
            
               if (!s.visible()) 
                   out.println("<delete id=\""+fixText(s.getIdent())+"\"/>");
               else {
                  String title = s.getDescr() == null ? "" 
                             : "title=\"[" + fixText(s.getIdent()) + "] " + fixText(s.getDescr()) + "\"";
                  String icon = "srv/icons/"+ (s.getIcon() != null ? s.getIcon() : _icon);    
                
                  out.println("<point id=\""+fixText(s.getIdent())+"\" x=\""
                               + (int) Math.round(ref.getEasting()) + "\" y=\"" + (int) Math.round(ref.getNorthing())+ "\" " 
                               + title + (s.isChanging() ? " redraw=\"true\"" : "") +
                               ((s instanceof AprsObject) && Main.ownobjects.hasObject(s.getIdent())  ? " own=\"true\"":"") +">");
                  out.println("   <icon src=\""+icon+"\"  w=\"22\" h=\"22\" ></icon>");     
        
                  if (vfilt.showIdent(s)) {
                     String style = "lobject";
                     if (s instanceof Station)
                        style = (!(((Station) s).getHistory().isEmpty()) ? "lmoving" : "lstill");
                     if (s.isEmergency())
                        style += " lflash";
                        
                     if (vfilt instanceof ViewFilter.Infra) {
                        if (s instanceof Station && ((Station)s).isIgate())
                           style += " igate";
                        if (s instanceof Station && ((Station)s).isWideDigi())
                           style += " wdigi";
                     }
                                                   
                     out.println("   <label style=\""+style+"\">");
                     out.println("       "+fixText(s.getDisplayId()));
                     out.println("   </label>"); 
                  }
                  
                  if (s instanceof Station)
                     printTrailXml(out, (Station) s);
                  if (vfilt.showPath(s) && s.isInfra())
                     printPathXml(out, (Station) s, uleft, lright);
                  
                  
                  out.println("</point>");
               }
            }
            /* Allow other threads to run */ 
            Thread.currentThread ().yield ();
        }        
        out.println("</overlay>");
        out.flush();
        return "text/xml; charset=utf-8";
   }

     
   /**
    * Display a message path between nodes. 
    */
   private void printPathXml(PrintWriter out, Station s, UTMRef uleft, UTMRef lright)
   {
       UTMRef ity = toUTM(s.getPosition());
       Set<String> from = s.getTrafficTo();
       if (from == null || from.isEmpty())
           return;
       Iterator<String> it = from.iterator();    
       while (it.hasNext()) 
       {
            AprsPoint p = _db.getItem(it.next());
            if (p==null || !p.isInside(uleft, lright))
                continue;
            Reference x = p.getPosition();
            UTMRef itx = toUTM(x);
            if (itx != null) { 
               out.print("<linestring stroke=\"1\" opacity=\"1.0\" color=\"009\">");
               out.print((int) Math.round(itx.getEasting())+ " " + (int) Math.round(itx.getNorthing()));
               out.print(", ");
               out.print((int) Math.round(ity.getEasting())+ " " + (int) Math.round(ity.getNorthing()));
               out.println("</linestring>");
            }
       }
   }
   
   
   /** 
    * Print a history trail of a moving station as a XML linestring object. 
    */
   private void printTrailXml(PrintWriter out, Station s)
   {
       History h = s.getHistory();
       if (h.isEmpty())
          return; 
          
       String[] tcolor = s.getTrailColor(); 
       out.println("   <linestring stroke=\"2\" opacity=\"1.0\" color=\""+ tcolor[0] +"\" color2=\""+ tcolor[1] +"\">");
       
       boolean first = true;
       Reference x = s.getPosition(); 
       UTMRef itx = toUTM(x);  
       for (History.Item it : h) 
       {
          if (itx != null) {       
              if (!first) 
                  out.print(", "); 
              else
                  first = false;   
              out.println((int) Math.round(itx.getEasting())+ " " + (int) Math.round(itx.getNorthing()));
          }
          itx = toUTM(it.pos);
       }
       if (itx != null)
           out.println(", "+ (int) Math.round(itx.getEasting())+ " " + (int) Math.round(itx.getNorthing()));
       out.println("   </linestring>");
   }
   
}
