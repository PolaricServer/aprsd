
package no.polaric.aprsd.http;
import no.polaric.aprsd.*;

import org.simpleframework.http.core.Container;
import org.simpleframework.transport.connect.Connection;
import org.simpleframework.transport.connect.SocketConnection;
import org.simpleframework.http.*;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.io.PrintStream;

import uk.me.jstott.jcoord.*;
import java.util.*;
import java.io.*;
import java.text.*;
import com.mindprod.base64.Base64;
import java.util.concurrent.locks.*; 


public abstract class HttpServer implements Container
{
   protected  StationDB  _db;
   protected  int        _utmzone;
   protected  char       _utmlatzone;
   private    String     _timezone;
   protected  String     _serverurl, _wfiledir;
   private    String     _icon, _adminuser, _updateusers;
   protected  boolean    _infraonly;
   protected  SarMode    _sarmode = null;
           
   public static final String _encoding = "UTF-8";


   DateFormat df = new SimpleDateFormat("dd MMM. HH:mm",
           new DateFormatSymbols(new Locale("no")));
   DateFormat tf = new SimpleDateFormat("HH:mm",
           new DateFormatSymbols(new Locale("no")));
           
   
   public static Calendar utcTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.getDefault());
   public static Calendar localTime = Calendar.getInstance();
   
   
   public HttpServer(StationDB db, int port, Properties config) throws IOException
   {
      _db=db; 
      _utmzone     = Integer.parseInt(config.getProperty("map.utm.zone", "33").trim());
      _utmlatzone  = config.getProperty("map.utm.latzone", "W").charAt(0);
      _icon        = config.getProperty("map.icon.default", "sym.gif").trim();
      _wfiledir    = config.getProperty("map.web.dir", "/aprsd").trim();
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
      
      /* Configure this web container */
      Connection connection = new SocketConnection(this);
      SocketAddress address = new InetSocketAddress(port);
      connection.connect(address);
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
   protected final String getAuthUser(Request req)
   {
         String auth = req.getValue("authorization");
         if (auth==null)
            auth = req.getValue("Authorization");
         if (auth != null) {
           Base64 b64 = new Base64();
           byte[] dauth = b64.decode(auth.substring(6));
           return (new String(dauth)).split(":")[0];
         }
         return null;
   }
   


   protected final boolean authorizedForUpdate(Request req)
   {
       String user = getAuthUser(req);

       if (user == null)
          user="_NOLOGIN_";
       return ( user.matches(_adminuser) ||
                user.matches(_updateusers) );
   }
   

   protected final boolean authorizedForAdmin(Request req)
   {
       String user = getAuthUser(req);
       return (user != null && user.matches(_adminuser)); 
   } 
   
   
   
   protected int _requests = 0, _reqNo = 0;

   /** 
    * Generic HTTP serve method. Dispatches to other methods based on uri
    */
   public void handle (Request req, Response resp)
   {
       int reqNo;
       synchronized (this) {
         _requests++; _reqNo++;
         reqNo = _reqNo;
       }
       
       try {
         String type = "text/html"; 
         /* FIXME: Consider moving getOutputStream to service methods */
         OutputStream os = resp.getOutputStream();
         PrintWriter out = new PrintWriter(new OutputStreamWriter(os, _encoding));
         String uri = req.getTarget().replaceAll("\\?.*", ""); 
         System.out.println("target="+uri);
         
         /* Determine the View Filter. Currently, there are just two
          * and they are hardcoded. The idea is to use a dictionary of
          * View filters and look them up by name. 
          * FIXME: Consider moving these to service methods.
          */
         String filtid = _infraonly ? "infra" : req.getParameter("filter");
         ViewFilter vfilt = ViewFilter.getFilter(filtid);
          
         /* FIXME: Move some of this to service methods? Before content
          * or use full buffering 
          */
         long time = System.currentTimeMillis();
         resp.set("Server", "Polaric Server 1.0+dev");
         resp.setDate("Date", time);
         resp.setDate("Last-Modified", time); 
         
         
         if ("/admin".equals(uri))
             type = _serveAdmin(req, out);   
         else if ("/search".equals(uri))
             type = _serveSearch(req, out, vfilt);
         else if ("/station".equals(uri))
             type = _serveStation(req, out);
         else if ("/findstation".equals(uri))
             type = serveFindItem(req, out);
         else if ("/history".equals(uri))
             type = _serveStationHistory(req, out);    
         else if ("/trailpoint".equals(uri))
             type = _serveTrailPoint(req, out);     
         else if ("/mapdata".equals(uri))
             type = serveMapData(req, out, vfilt, filtid);
         else if ("/addobject".equals(uri))
             type = _serveAddObject(req, out);
         else if ("/deleteobject".equals(uri))
             type = _serveDeleteObject(req, out);
         else if ("/resetinfo".equals(uri))
             type = _serveResetInfo(req, out);    
         else if ("/sarmode".equals(uri))
             type = _serveSarMode(req, out);        
         else {
             out.println("<html><body>Unknown service: "+uri+"</body></html>");
             resp.setCode(404); 
             resp.setText("Not found");
         }
             
         resp.set("Content-Type", type);
         out.close();
       }
       catch (Throwable e) 
          { System.out.println("*** HTTP REQ exception: "+e.getMessage());
            e.printStackTrace(System.out); } 
       finally {
         synchronized(this) { _requests--; } }
   }

   /* Stubs for the server methods that are implemented in Scala. 
    */
   protected abstract String _serveAdmin(Request req, PrintWriter out);
   protected abstract String _serveStation(Request req, PrintWriter out);
   protected abstract String _serveAddObject (Request req, PrintWriter out);
   protected abstract String _serveDeleteObject (Request req, PrintWriter out);
   protected abstract String _serveResetInfo (Request req, PrintWriter out);
   protected abstract String _serveSearch (Request req, PrintWriter out, ViewFilter vf);
   protected abstract String _serveStationHistory (Request req, PrintWriter out);
   protected abstract String _serveSarMode (Request req, PrintWriter out);
   protected abstract String _serveTrailPoint (Request req, PrintWriter out);

    
   protected String fixText(String t)
   {
        t = t.replaceAll("&amp;", "##amp;"); 
        t = t.replaceAll("&lt;", "##lt;");
        t = t.replaceAll("&gt;", "##gt;");   
        t = t.replaceAll("&quot;", "##amp;");
        t = t.replaceAll("&", "&amp;");   
        t = t.replaceAll("<", "&lt;");
        t = t.replaceAll(">", "&gt;");
        t = t.replaceAll("\"", "&quot;");
        t = t.replaceAll("\\p{Cntrl}", "?");
        t = t.replaceAll("##amp;", "&amp;"); 
        t = t.replaceAll("##lt;", "&lt;");
        t = t.replaceAll("##gt;", "&gt;");   
        t = t.replaceAll("##quot;", "&amp;"); 
        return t; 
   }
   
   
   
   /**
    * Look up a station and return id, x and y coordinates (separated by commas).
    * If not found, return nothing.
    */
   public String serveFindItem(Request req, PrintWriter out)
       throws IOException
   { 
       
       String ident = req.getParameter("id").toUpperCase();
       AprsPoint s = _db.getItem(ident);
       if (s==null) {
          int i = ident.lastIndexOf('-');
          if (i > -1)    
             ident = ident.substring(0, i);
          List<AprsPoint> l = _db.getAll(ident);
          if (l.size() > 0)
              s = l.get(0);
       }
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
   

  protected long _sessions = 0;
  private synchronized long getSession(Request req)
      throws IOException
  {
     String s_str  = req.getParameter("clientses");
     if (s_str != null && s_str.matches("[0-9]+")) {
        long s_id = Long.parseLong(s_str);
        if (s_id > 0)
           return s_id;
     }
     _sessions = (_sessions +1) % 2000000000;
     return _sessions;       
  }
  
   

  private int _seq = 0;
   
   /**
    * Produces XML (Ka-map overlay spec.) for plotting station/symbols/labels on map.   
    */
   public String serveMapData(Request req, PrintWriter out, 
          ViewFilter vfilt, String filt) throws IOException
   {  
        UTMRef uleft = null, lright = null;
        Form parms = req.getForm();
        if (parms.get("x1") != null) {
          long x1 = Long.parseLong( parms.get("x1") );
          long x2 = Long.parseLong( parms.get("x2") );
          long x3 = Long.parseLong( parms.get("x3") );    
          long x4 = Long.parseLong( parms.get("x4") );
          uleft = new UTMRef((double) x1, (double) x2, _utmlatzone, _utmzone); 
          lright = new UTMRef((double) x3, (double) x4, _utmlatzone, _utmzone);
        }
        long scale = 0;
        if (parms.get("scale") != null)
           scale = Long.parseLong(parms.get("scale"));
        
        /* Sequence number at the time of request */
        int seq  = 0;
        synchronized (this) {
          _seq = (_seq+1) % 32000;
          seq = _seq;
        }
        long client = getSession(req);
        boolean showSarInfo = (getAuthUser(req) != null || _sarmode == null);
        
        /* If requested, wait for a state change (see Notifier.java) */
        if (parms.get("wait") != null) 
            if (! Station.waitChange(uleft, lright, client) ) {
                out.println("<overlay cancel=\"true\"/>");             
                out.flush();
                return "text/xml; charset=utf-8";
            }
                
        /* XML header with meta information */           
        out.println("<overlay seq=\""+_seq+"\"" +
            (filt==null ? ""  : " view=\"" + filt + "\"") + ">");
        out.println("<meta name=\"utmzone\" value=\""+ _utmzone + "\"/>");
        out.println("<meta name=\"login\" value=\""+ getAuthUser(req) + "\"/>");
        out.println("<meta name=\"adminuser\" value=\""+ authorizedForAdmin(req) + "\"/>");
        out.println("<meta name=\"updateuser\" value=\""+ authorizedForUpdate(req) + "\"/>");
        out.println("<meta name=\"clientses\" value=\""+ client + "\"/>");    
        out.println("<meta name=\"sarmode\" value=\""+ (_sarmode!=null ? "true" : "false")+"\"/>");    
        
        /* Output signs. A sign is not an APRS object
         * just a small icon and a title. It may be a better idea to do this
         * in map-layers instead?
         */
        int i=0;
        for (Signs.Item s: Signs.getList())
        {
            UTMRef ref = toUTM(s.getPosition()); 
            if (ref == null || !s.visible(scale) || !s.isInside(uleft, lright))
                continue;
            String title = s.getDescr() == null ? "" : "title=\"" + fixText(s.getDescr()) + "\"";
            String icon = _wfiledir +"/icons/"+ s.getIcon();    
           
            out.println("<point id=\"__sign" + (i++) + "\" x=\""
                         + (int) Math.round(ref.getEasting()) + "\" y=\"" + (int) Math.round(ref.getNorthing())+ "\" " 
                         + title+">");
            out.println("   <icon src=\""+icon+"\"  w=\"22\" h=\"22\" ></icon>");     
            out.println("</point>");    
        }        
        
        /* Output APRS objects */
        for (AprsPoint s: _db.search(uleft, lright)) 
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
               synchronized(s) {
                  ref = toUTM(s.getPosition()); 
                  if (ref == null) continue; 
                  
                  String title = s.getDescr() == null ? "" 
                             : "title=\"[" + fixText(s.getIdent()) + "] " + fixText(s.getDescr()) + "\"";
                  String icon = _wfiledir + "/icons/"+ (s.getIcon() != null ? s.getIcon() : _icon);    
                
                  out.println("<point id=\""+fixText(s.getIdent())+"\" x=\""
                               + (int) Math.round(ref.getEasting()) + "\" y=\"" + (int) Math.round(ref.getNorthing())+ "\" " 
                               + title + (s.isChanging() ? " redraw=\"true\"" : "") +
                               ((s instanceof AprsObject) && Main.ownobjects.hasObject(s.getIdent().replaceFirst("@.*",""))  ? " own=\"true\"":"") +">");
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
                     out.println("       "+fixText(s.getDisplayId(showSarInfo)));
                     out.println("   </label>"); 
                  }
                  if (s instanceof Station)
                     printTrailXml(out, (Station) s, uleft, lright);
               } /* synchronized(s) */
               
               if (vfilt.showPath(s) && s.isInfra())
                  printPathXml(out, (Station) s, uleft, lright);              
               out.println("</point>");
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
            Station p = (Station)_db.getItem(it.next());
            if (p==null || !p.isInside(uleft, lright) || p.expired())
                continue;
            Reference x = p.getPosition();
            UTMRef itx = toUTM(x);
            RouteInfo.Edge e = _db.getRoutes().getEdge(s.getIdent(), p.getIdent());
            if (itx != null) { 
               out.print("<linestring stroke="+
                   (e.primary ? "\"2\"" : "\"1\"")  + " opacity=\"1.0\" color=\""  +
                   (e.primary ? "B00\">" : "00A\">"));
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
   private void printTrailXml(PrintWriter out, Station s, UTMRef uleft, UTMRef lright)
   {
       History h = s.getHistory();
       if (h.isEmpty())
          return; 
          
       String[] tcolor = s.getTrailColor(); 
       out.println("   <linestring stroke=\"2\" opacity=\"1.0\" color=\""+ tcolor[0] +"\" color2=\""+ tcolor[1] +"\">");
       
       boolean first = true;
       Reference x = s.getPosition(); 
       int state = 1;
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
            
          itx = toUTM(it.getPosition());
          if (itx == null)
             System.out.println("*** DEBUG: trail point is null (printTrailXml)");
          else if (it.isInside(uleft, lright, 0.7, 0.7))
             state = 2;
          else
             if (state == 2) {
                state = 3; 
                break;
             }    
       }
       if (itx != null & state < 3)
           out.println(", "+ (int) Math.round(itx.getEasting())+ " " + (int) Math.round(itx.getNorthing()));
       out.println("   </linestring>");
   }
   
}
