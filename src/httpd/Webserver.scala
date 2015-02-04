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
 
import java.util._
import java.io._
import uk.me.jstott.jcoord._
import scala.xml._
import scala.collection.JavaConversions._
import no.polaric.aprsd._
import org.simpleframework.http.core.Container
import org.simpleframework.transport.connect.Connection
import org.simpleframework.transport.connect.SocketConnection
import org.simpleframework.http._
import com.teamunify.i18n._


   
package no.polaric.aprsd.http 
{

  class Webserver 
      ( val api: ServerAPI) extends XmlServer(api) with ServerUtils
  {


   val _time = new Date();



   /** 
    * Admin interface. 
    * To be developed further...
    */
   def handle_admin(req : Request, res: Response) =
   {   
       val out = getWriter(res);
       val cmd = req.getParameter("cmd")
       val head = <meta http-equiv="refresh" content="60" />


       def action(req : Request): NodeSeq =
          if (!authorizedForUpdate(req))
              <h3>{I.tr("You are not authorized for such operations")}</h3>
          else if ("gc".equals(cmd)) {
              _api.getDB().garbageCollect()
              <h3>GC, ok</h3>
          }
          else if ("clearobj".equals(cmd)) {
              _api.getDB().getOwnObjects().clear()
              <h3>{I.tr("Clear own objects, ok")}</h3>
          }
          else if ("clearlinks".equals(cmd)) {
              _api.getDB().getRoutes().clear()
              <h3>{I.tr("Clear link information, ok")}</h3>    
          }
          else if ("info".equals(cmd))    
              <h3>Status info for Polaric APRSD</h3>
              <fieldset>
              { simpleLabel("items", "leftlab", I.tr("Server run since:"), TXT(""+_time)) ++
                simpleLabel("items", "leftlab", I.tr("Server version:"), TXT(""+_api.getVersion())) ++ 
                simpleLabel("items", "leftlab", I.tr("Number of APRS items:"), TXT(""+_api.getDB().nItems())) ++
                simpleLabel("items", "leftlab", I.tr("Number of connections:"), TXT(""+_api.getDB().getRoutes().nItems())) ++
                simpleLabel("items", "leftlab", I.tr("Own objects:"), TXT(""+_api.getDB().getOwnObjects().nItems())) ++   
                simpleLabel("items", "leftlab", I.tr("Number of HTTP clients:"), TXT(""+(_api.getHttps().getClients()-1))) ++  
                simpleLabel("items", "leftlab", I.tr("Number of HTTP requests:"), TXT(""+(_api.getHttps().getReq()))) }    
              { simpleLabel("freemem", "leftlab", I.tr("Used memory:"), 
                  TXT( Math.round(StationDBImp.usedMemory()/1000)+" KBytes")) }   
                            
              {  var i=0;
                 for (x <- PluginManager.getPlugins()) yield {
                    i+=1;
                    simpleLabel("", "leftlab", if (i<=1) I.tr("Plugin module")+": " else "", 
                         TXT(x.getDescr())); 
                 }
              }    
              <br/>
         
              { var i = 0; 
                for ( x:String <- _api.getChanManager().getKeys()) yield
                 {  val ch = api.getChanManager().get(x); 
                    i += 1;
                    simpleLabel("chan_"+ch.getIdent(), 
                     "leftlab", "Kanal '"+ch.getIdent()+"' ("+ch.getShortDescr()+"):", 
                            TXT(ch.toString()+", Rx=" + ch.nHeardPackets() + ", Tx=" + ch.nSentPackets())) } 
              }

              
              { simpleLabel("igate", "leftlab", "Igate: ", 
                   if (_api.getIgate()==null) TXT("---") else TXT(""+_api.getIgate())) }   
              { if (_api.getRemoteCtl() != null && !_api.getRemoteCtl().isEmpty())  
                   simpleLabel("rctl", "leftlab", I.tr("Remote control")+": ", TXT(""+_api.getRemoteCtl())) else null; }     
                   
              </fieldset>  
              <input type="submit" onclick="top.window.close()" id="cancel" value="Avbryt"/>
          else
              <h3>{I.tr("Unknown command")}</h3>
             
        printHtml (res, htmlBody(req, head, action(req)));    
   }
   
   
   def symChoice = 
            <select id="symChoice" class="symChoice"
             onchange="var x=event.target.value;document.getElementById('osymtab').value=x[0];document.getElementById('osym').value=x[1];">
               <option value="/c" style="background-image:url(../aprsd/icons/orient.png)"> {I.tr("Post")} </option>
               <option value="\m" style="background-image:url(../aprsd/icons/sign.png)"> {I.tr("Sign")} </option>
               <option value="\." style="background-image:url(../aprsd/icons/sym00.png)"> {I.tr("Cross")} </option>
               <option value="\n" style="background-image:url(../aprsd/icons/sym07.png)"> {I.tr("Triangle")} </option>
               <option value="/+" style="background-image:url(../aprsd/icons/sym02.png)"> {I.tr("Cross")} </option>
               <option value="/o" style="background-image:url(../aprsd/icons/eoc.png)"> {I.tr("OPS/EOS")} </option>
               <option value="/r" style="background-image:url(../aprsd/icons/radio.png)"> {I.tr("Radio station")} </option>
            </select>
            ;
   
   
   
    /**
    * set own position
    */          
   def handle_setownpos(req : Request, res: Response) =
   {
        val pos = getCoord(req)
        val prefix = <h2> {I.tr("Set your own position")} </h2>
        val p = api.getOwnPos()
       
        /* Fields to be filled in */
        def fields(req : Request): NodeSeq =
            <xml:group>
            {
              label("osymtab", "lleftlab", I.tr("Symbol")+":", 
                    I.tr("APRS symbol table and symbol. Fill in or chose from the list at the right side.")) ++
              textInput("osymtab", 1, 1, ""+p.getSymtab()) ++
              textInput("osym", 1, 1, ""+p.getSymbol()) ++
              symChoice ++
              br ++
              label("utmz", "lleftlab", I.tr("Pos (UTM)")+":", I.tr("Position in UTM format.")) ++
              {  if (pos==null)
                    utmForm('W', 34)
                 else
                    showUTM(pos)
              }
           }
           </xml:group>
           ;  
             
        /* Action. To be executed when user hits 'submit' button */
        def action(req : Request): NodeSeq = {
               if (!authorizedForAdmin(req))
                  <h3> {I.tr("You are not authorized for admin operations")} </h3>
               else {
                  val osymtab = req.getParameter("osymtab")
                  val osym  = req.getParameter("osym")
                  System.out.println("*** SET OWN POS by user '"+getAuthUser(req)+"'")
                  p.updatePosition(new Date(), pos, 
                      if (osymtab==null) '/' else osymtab(0), if (osym==null) 'c' else osym(0))
                  
                 <h2> {I.tr("Position registerred")} </h2>
                 <p>pos={ showUTM(pos) }</p>
              }
        };
            
        printHtml (res, htmlBody (req, null, htmlForm(req, prefix, fields, IF_AUTH(action))))
    }   
     
   
   
   
   
   def handle_sarmode(req : Request, res: Response) =
   {
       val prefix = <h2> {I.tr("Search and rescue mode")} </h2>
       var filter = req.getParameter("sar_prefix")
       var reason = req.getParameter("sar_reason")
       var hidesar = req.getParameter("sar_hidesar")
       val on = req.getParameter("sar_on")
       
       def fields(req : Request): NodeSeq =          
           <xml:group>
           <p> {I.tr("Certain types of info only visible for authorized users.")} </p>
           <label for="sar_on" class="lleftlab"> {I.tr("SAR mode")+":"} </label>
           { checkBox("sar_on", api.getSar() !=null, TXT(I.tr("activated"))) }  
           <br/>
           <label for="sar_hidesar" class="lleftlab"> {I.tr("Alias/icon")+":"} </label>
           { checkBox("sar_hidesar", api.getSar()==null || api.getSar().isAliasHidden(), TXT(I.tr("hidden"))) }  
           <br/>
           
           <label for="sar_prefix" class="lleftlab"> {I.tr("Hide prefix")+":"} </label>
           { textInput("sar_prefix", 25, 50,
               if ( api.getSar()==null) "" else api.getSar().getFilter() ) }
           <br/>
           
           
           <label for="sar_reason" class="lleftlab"> {I.tr("Description")+":"} </label>
           { if (api.getSar() == null)
                textInput("sar_reason", 25, 50, "")
             else 
                <xml:group>
                <label id="sar_reason">{ api.getSar().getReason() }
                <em> { "("+api.getSar().getUser()+")" } </em></label>
                <br/>
                { simpleLabel("sar_date", "lleftlab", I.tr("Activated")+":", TXT(""+api.getSar().getTime())) }
                </xml:group>
           }
           </xml:group>     
              
              
       def action(req : Request): NodeSeq = 
       {
          AprsPoint.abortWaiters(true);
          if (on != null && "true".equals(on) ) {
               val hide = (hidesar != null && "true".equals(hidesar) ) 
               val filt = if ("".equals(filter)) "NONE" else filter;
               
               api.setSar(reason, getAuthUser(req), filter, hide);
               
               reason = if (!hide) "[NOHIDE] "+reason 
                        else reason;
               if (api.getRemoteCtl() != null)
                   api.getRemoteCtl().sendRequestAll("SAR "+getAuthUser(req)+" "+filt+" "+reason, null);
               
               
               <h3> {I.tr("Activated")} </h3>
               <p>{reason}</p>
          }
          else {   
               api.clearSar();
               if (api.getRemoteCtl() != null)
                  api.getRemoteCtl().sendRequestAll("SAR OFF", null);
               <h3> {I.tr("Ended")} </h3>
          } 
       }
            
       
       printHtml (res, htmlBody (req, null, htmlForm(req, prefix, fields, IF_AUTH(action) )))
   }
   
   
   
   
   def handle_sarurl(req : Request, res: Response) =
   {
       val url = req.getParameter("url")
       
       def action(req : Request): NodeSeq = 
       {
          if (!authorizedForUpdate(req) && api.getSarUrl() != null )  
              <h3>{ I.tr("SAR URL not available or you are not authorized")} </h3>
          else {
              val sarurl = api.getSarUrl().create(url)
              <h1> { I.tr("Short URL for search and rescue")} </h1>
              <h2><a class="sarurl" href={sarurl}>{sarurl}</a></h2>
              <h1> { I.tr("Key")+":" }</h1>
              <h2 class="sarurl">{SarUrl.getKey(sarurl)}</h2>
              <p> { I.tr("Valid 1 day from now")} </p>
          }
       }   
       printHtml (res, htmlBody(req, null, action(req)));        
   }
   
   
   
   /**
    * Delete APRS object.
    */          

   def handle_deleteobject(req : Request, res: Response) =
   {
       var id = req.getParameter("objid")
       id = if (id != null) id.replaceFirst("@.*", "") else null
       val prefix = <h2> { I.tr("Remove object")} </h2>
       
       def fields(req : Request): NodeSeq =
           <xml:group>
           <label for="objid" class="lleftlab">Objekt ID:</label>
           { textInput("objid", 9, 9, 
                if (id==null) "" else id.replaceFirst("@.*", "") ) }
           </xml:group>
           ;
      
      
       def action(req : Request): NodeSeq =
          if (id == null) {
              <h3> {I.tr("Error")+":"} </h3>
              <p> {I.tr("'objid' must be given as parameter")} </p>;
          }
          else {
              if (_api.getDB().getOwnObjects().delete(id)) {
                  System.out.println("*** DELETE OBJECT: '"+id+"' by user '"+getAuthUser(req)+"'")
                  <h3> {I.tr("Object removed!")} </h3>
              }
              else
                  <h3> {I.tr("Couldn't find object")+": "+id} </h3>
          }  
          ;
          
       printHtml (res, htmlBody (req, null, htmlForm(req, prefix, fields, IF_AUTH(action) )))
   }          




   def handle_resetinfo(req : Request, res : Response) =
   {
       val id = req.getParameter("objid")
       val prefix = <h2> {I.tr("Reset info about station/object")} </h2>
       
       def fields(req : Request): NodeSeq =
           <label for="objid" class="lleftlab">Objekt ID:</label>
           <input id="objid" name="objid" type="text" size="9" maxlength="9"
              value={if (id==null) "" else id} />;
           ;
      
       def action(req : Request): NodeSeq =
          if (id == null) {
              <h3> {I.tr("Error")+":"} </h3>
              <p> {I.tr("'objid' must be given as parameter")} </p>;
          }
          else {
             val x = _api.getDB().getItem(id, null);
             if (x != null)
                x.reset();
             <h3> {I.tr("Info about object is reset!")} </h3>
          } 
          ;
          
       printHtml (res, htmlBody (req, null, htmlForm(req, prefix, fields, IF_AUTH(action))))
   }          



             
   /**
    * add or edit APRS object.
    */          
   def handle_addobject(req : Request, res : Response) =
   {
        val pos = getCoord(req)
        val id = req.getParameter("objid")
        val prefix = <h2> {I.tr("Add object")} </h2>
        
        /* Fields to be filled in */
        def fields(req : Request): NodeSeq =
            <xml:group>
            {
              label("objid", "lleftlab", I.tr("Object ID")+":", I.tr("Identifier for object")) ++
              textInput("objid", 9, 9, "") ++
              br ++
              label("osymtab", "lleftlab", I.tr("Symbol")+":", 
                     I.tr("APRS symbol table and symbol. Fill in or chose from the list at the right side.")) ++
              textInput("osymtab", 1, 1, "/") ++
              textInput("osym", 1, 1, "c") ++
              symChoice ++
              br ++
              label("descr", "lleftlab", I.tr("Description")+":", "") ++
              textInput("descr", 30, 40, "") ++
              br ++
              label("utmz", "lleftlab", I.tr("Pos (UTM)")+":", I.tr("Object's position")) ++
              {  if (pos==null)
                   utmForm('W', 34)
                 else
                   showUTM(pos)
              }
            }
            <br/>
            <div>
            {
              label("perm", "lleftlab", I.tr("Settings")+":", "") ++
              checkBox("perm", false, TXT(I.tr("Timeless (Permanent)")))
            }
            </div>
            </xml:group>
            ;
            

            
            
        /* Action. To be executed when user hits 'submit' button */
        def action(request : Request): NodeSeq =
            if (id == null || !id.matches("[a-zA-Z0-9_].*\\w*")) {
               <h2> { I.trf("Invalid id {0}",id)} </h2>
               <p>  { I.tr("please give 'objid' as a parameter. This must start with a letter/number")} </p>;
            }
            else {
               val osymtab = req.getParameter("osymtab")
               val osym  = req.getParameter("osym")
               val otxt = req.getParameter("descr")
               val perm = req.getParameter("perm")
               
               System.out.println("*** SET OBJECT: '"+id+"' by user '"+getAuthUser(request)+"'")
               if ( _api.getDB().getOwnObjects().add(id, 
                      new AprsHandler.PosData( pos,
                         if (osym==null) 'c' else osym(0) ,
                         if (osymtab==null) '/' else osymtab(0)),
                      if (otxt==null) "" else otxt,
                      "true".equals(perm) ))
                  
                  <h2> {I.tr("Objekt updated")} </h2>
                  <p>ident={"'"+id+"'"}<br/>pos={showUTM(pos) }</p>;
               else
                  <h2> {I.tr("Cannot update")} </h2>
                  <p>  {I.trf("Object '{0}' is already added by someone else", id)} </p>
            }
            ;
            
        printHtml (res, htmlBody (req, null, htmlForm(req, prefix, fields, IF_AUTH(action))))
    }


    
    def _directionIcon(direction:Int, fprefix: String): NodeSeq = 
        direction match {
          case x if !(22 until 337 contains x) =>
              <div><img src= {fprefix+"/dicons/dN.png"}/> N</div>
          case x if (22 until 67 contains x) =>
              <div><img src= {fprefix+"/dicons/dNE.png"}/> NE</div>
          case x if (67 until 112 contains x) =>
              <div><img src= {fprefix+"/dicons/dE.png"}/> E</div>
          case x if (112 until 157 contains x) =>
              <div><img src= {fprefix+"/dicons/dSE.png"}/> SE</div>
          case x if (157 until 202 contains x) =>
              <div><img src= {fprefix+"/dicons/dS.png"}/> S</div>
          case x if (202 until 247 contains x) =>
              <div><img src= {fprefix+"/dicons/dSW.png"}/> SW</div>
          case x if (247 until 292 contains x) =>
              <div><img src= {fprefix+"/dicons/dW.png"}/> W</div>
          case x if (292 until 337 contains x) =>
              <div><img src= {fprefix+"/dicons/dNW.png"}/> NW</div>
          case _ => null;
      }


   
  /** 
    * Presents a status list over registered stations (standard HTML)
    */
   
   def handle_search(req : Request, res : Response) =
   {
       val filtid = if (_infraonly) "infra"  else req.getParameter("filter")
       val vfilt = ViewFilter.getFilter(filtid, authorizedForUpdate(req))
       
       var arg = req.getParameter("filter")
       var mob = req.getParameter("mobile");
       if (arg == null) 
           arg  = "__NOCALL__"; 
       val infra = _infraonly || "infra".equals(arg)
       val result: NodeSeq =
         <table>
         {
            for ( x:AprsPoint <- _api.getDB().getAll(arg)
                  if ( true || !vfilt.apply(x, 0).hideAll()) ) yield
            {
               val s = if (!x.isInstanceOf[Station]) null
                       else x.asInstanceOf[Station];

               val moving = if (s!=null) !s.getTrail().isEmpty()
                            else false;

               <tr class={
                 if (x.visible() && x.getPosition() != null) 
                      "" else "nopos" } 
                 onclick={
                 if (x.visible() && x.getPosition() != null) 
                     "findItem('" + x.getIdent() + "', 'true')"
                 else "" 
               }>
          
               <td>{x.getIdent()}</td>
               <td> 
               { if (moving && s.getSpeed() > 0)
                       _directionIcon(s.getCourse(), fprefix(req)) else 
                          if (s==null) TXT("obj") else null } </td>
               <td> { df.format(x.getUpdated()) } </td>
               { if (!"true".equals(mob)) 
                  <td> { if (x.getDescr() != null) x.getDescr() else "" } </td> 
                 else null }
               </tr>
           }
        } 
        </table>;
        printHtml (res, htmlBody (req, null, result))
   }
 




   def handle_station(req : Request, res : Response)  = 
       _handle_station(req, res, false)
       ;
       
   def handle_station_sec(req : Request, res : Response)  = 
       _handle_station(req, res, authorizedForUpdate(req))
       ;
  
  
  
   /** 
    * Info about station/object (standard HTML)
    */
   def _handle_station(req : Request, res : Response, canUpdate: Boolean) =
   {        
        val id = req.getParameter("id")
        val x = _api.getDB().getItem(id, null)
        val s = if (x.isInstanceOf[Station]) x.asInstanceOf[Station] else null
        val obj = if (x.isInstanceOf[AprsObject]) x.asInstanceOf[AprsObject] else null
        val edit  =  ( req.getParameter("edit") != null )
        val simple =  ( req.getParameter("simple") != null )
        val prefix = null
        val pathinfo = if (s != null && s.getPathInfo() != null && 
                           s.getPathInfo().length() > 1) 
                               cleanPath(s.getPathInfo()) else null
                         
        if (obj != null)
            obj.update();


        def itemList(items : Set[String]) = 
             <div class="trblock">
             {  
                var i=0
                if (items != null) 
                 for (it <- items) yield {
                    i += 1
                    val xx = _api.getDB().getItem(it, null)
                    val linkable = (xx != null  && xx.visible() && xx.getPosition() != null)
                    <span class={ if (linkable) "link_id" else ""} onclick={
                        if (linkable) 
                           "findItem('" + xx.getIdent() + "', true)"
                        else "" }> {it + " " } </span>
                }
                else null;
             }
             </div>
             ;
        
        
        /* Fields to be filled in */
        def fields(req : Request): NodeSeq =
            <xml:group>  
            <label for="callsign" class="leftlab">Ident:</label>
            <label id="callsign"><b> { if (x != null && x.getIdent != null) x.getIdent().replaceFirst("@.*","") else "" } </b></label>

            { if (!simple)
                 simpleLabel("symbol", "leftlab", I.tr("Symbol")+":",TXT( x.getSymtab()+" "+x.getSymbol())) else null }
            { if (x.getAlias() != null)
                 simpleLabel("alias", "leftlab", I.tr("Alias")+":", <b>{x.getAlias()}</b>) else null }
            { if (obj != null)
                 simpleLabel("owner", "leftlab", I.tr("Sender")+":", <b>{obj.getOwner().getIdent()}</b>) else null}
            { if (x.getDescr() != null && x.getDescr().length() > 0)
                 simpleLabel("descr", "leftlab", I.tr("Description")+":", TXT(x.getDescr())) else null}
            { if (s != null && s.getStatus() != null)
                 simpleLabel("status", "leftlab", I.tr("Status")+":", I.tr("Last received APRS status message"),
                      TXT ( s.getStatus().text + " [" + df.format(s.getStatus().time)+"]"))  else null}
      
            { simpleLabel("pos", "leftlab", I.tr("Position")+" (UTM):",
                if (x.getPosition() != null) showUTM(x.getPosition())
                else TXT(I.tr("not registered")) ) }
 
            { simpleLabel("posll", "leftlab", I.tr("Position")+" (latlong):",
                if (x.getPosition() != null) TXT( ll2dmString( x.getPosition().toLatLng()))
                else TXT(I.tr("not registered")) ) }
                
            { if (x != null && x.getAmbiguity() > 0)
                  simpleLabel("ambg", "leftlab", I.tr("Ambiguity")+":", 
                    TXT( "± "+0.01 * Math.pow(10, x.getAmbiguity())/2 + " "+I.tr("minutes") )) else null}
            
            { if (s != null && s.getAltitude() >= 0)
                  simpleLabel("altitude", "leftlab", I.tr("Altitude")+":", TXT(s.getAltitude() + " m ")) else null }
            { if (s != null && s.getSpeed() > 0)
                  simpleLabel("cspeed", "leftlab", I.tr("Movement")+":", _directionIcon(s.getCourse(), fprefix(req))) else null }

            { simpleLabel("hrd", "leftlab", I.tr("Last reported")+":", I.tr("Time of last received APRS report"),
                  TXT( df.format(x.getUpdated()))) }
                  
            { if (pathinfo != null) simpleLabel("via", "leftlab", I.tr("Via")+":", I.tr("Which route the last APRS report took"), 
                     TXT(pathinfo)) else null }
                  
            { var txt = "";
              if (s != null) {
                 if (s.isIgate()) txt += "IGATE "; 
                 if (s.isWideDigi()) txt += I.tr("Wide-Digi");
                 if ((s.isIgate() || s.isWideDigi()) && simple)
                   { simpleLabel("infra", "leftlab", I.tr("Infrastructure")+":", TXT(txt)) } else null 
              } else null
            }
            
            { if (simple)        
               <div id="traffic">
                  { if (s != null && s.isInfra() )
                     {  <label for="hrds" class="leftlab"> {I.tr("Traffic from")+":"} </label>
                        <label id="hrds"> { itemList(s.getTrafficFrom()) } </label> } else null }
               
                  { if (s != null && s.getTrafficTo != null && !s.getTrafficTo.isEmpty)
                     {  <label for="hrd" class="leftlab"> {I.tr("Traffic to")+":"} </label>
                        <label id="hrd"> { itemList(s.getTrafficTo()) } </label> } else null }
               </div>
               else null                    
            }
            
           
                                 
            { if (edit && canUpdate)
                  <div>
                  <br/>
                  { label("hidelabel", "leftlab", I.tr("Settings")+":") ++
                    checkBox("hidelabel", x.isLabelHidden(), TXT(I.tr("Hide ID"))) ++
                    checkBox("pers", x.isPersistent(), TXT(I.tr("Persistent storage"))) }
                  <br/>
                  { if (s != null)
                        label("newcolor", "leftlab", I.tr("Trail")+":") ++ 
                        checkBox("newcolor", false, TXT(I.tr("Find new colour")))
                    else null }
                  <br/>   
                  <label for="nalias" class="leftlab"> {I.tr("New alias")+":"} </label>
                  { textInput("nalias", 10, 20, 
                        if (x.getAlias()==null) "" else x.getAlias()) }
                  <br/>
                  { iconSelect(x, fprefix(req), "/icons/") }
                  </div>
               else null        
            }              
            </xml:group> 
            ;



        /* Action. To be executed when user hits 'submit' button */
        def action(req : Request): NodeSeq =
        {
             val perm = req.getParameter("pers");
             x.setPersistent( "true".equals(perm) );  
             val hide = req.getParameter("hidelabel");
             x.setLabelHidden( "true".equals(hide) );     
             
             val newcolor = req.getParameter("newcolor");
             if (s != null && "true".equals(newcolor) )
                s.nextTrailColor();             


             /* Alias setting */
             var alias = req.getParameter("nalias");
             var ch = false;
             if (alias != null && alias.length() > 0)      
                 ch = x.setAlias(alias);
             else
                { ch = x.setAlias(null)
                  alias = "NULL"
                }
             System.out.println("*** ALIAS: '"+alias+"' for '"+x.getIdent()+"' by user '"+getAuthUser(req)+"'")
             if (ch && _api.getRemoteCtl() != null)
                 _api.getRemoteCtl().sendRequestAll("ALIAS "+x.getIdent()+" "+alias, null);

             /* Icon setting */
             var icon = req.getParameter("iconselect");
             if ("system".equals(icon)) 
                 icon = null; 
             if (x.setIcon(icon) && _api.getRemoteCtl() != null )
                 _api.getRemoteCtl().sendRequestAll("ICON "+x.getIdent() + " " +
                    { if (icon==null) "NULL" else icon }, 
                    null);
            
             <h3>Oppdatert</h3>
        }


        printHtml (res, htmlBody ( req, null, 
                                   if (simple) fields(req)
                                   else htmlForm(req, prefix, fields, IF_AUTH(action), true, default_submit)))
    }
      


  
    private def cleanPath(txt:String): String = 
        txt.replaceAll("((WIDE|TRACE|SAR|NOR)[0-9]*(\\-[0-9]+)?\\*?,?)|(qA.),?", "")
           .replaceAll("\\*", "").replaceAll(",+|(, )+", ", ")   
        ;

      
   /** 
    * Presents a list over last positions and movements (standard HTML)
    */
   def handle_history(req : Request, res : Response) =
   {        
       val s = _api.getDB().getStation(req.getParameter("id"), null)
       val result: NodeSeq =
          if (s == null)
             <h2>Feil:</h2><p> {I.tr("Couldn't find station")} </p>;
          else
             <table>
               <tr><th>{I.tr("Time")} </th><th>Km/h </th><th> {I.tr("Dir")} </th><th> {I.tr("Distance")} </th><th> {I.tr("APRS via")} </th></tr>
               {
                   val h = s.getTrail()
                   var x = s.getHItem()
                   var i = 0;
                   var fp = _wfiledir /* FIXME FIXME fprefix(req) */
                   
                   for (it <- h.points()) yield {
                      i += 1
                      val arg = "'"+s.getIdent()+"', "+i
                      <tr onmouseover={"histList_hover("+arg+");"} 
                          onmouseout={"histList_hout("+arg+");"}
                          onclick = {"histList_click("+arg+");"} >
                      <td> { tf.format(x.getTS()) } </td>
                      <td> { if (x.speed >= 0) x.speed.toString else "" } </td>
                      <td> { if (x.speed > 0) _directionIcon(x.course, fp) else ""} </td>
                      <td> {
                         val dist = x.getPosition().toLatLng().distance(it.getPosition().toLatLng())
                         x = it.asInstanceOf[Trail.Item];
                         if (dist < 1)
                             "%3d m" format Math.round(dist*1000)
                         else
                             "%.1f km" format dist
                      }</td>
                      <td> { 
                        TXT( cleanPath(x.pathinfo)) } </td>
                      </tr>
                   }
               }
             </table>

        printHtml(res, htmlBody(req, null, result))
    } 
  
  
  

    def handle_trailpoint(req : Request, res : Response) =
    {
       val time = xf.parse(req.getParameter("time"))
       val ident = req.getParameter("id")
       val item = _api.getDB().getTrailPoint(ident, time)
       /* FIXME: Check if valid result */
       
       val result : NodeSeq = 
          if (item == null)
            TXT(I.tr("Couldn't find info")+": "+ident)
          else
            <xml:group>
            <label for="callsign" class="lleftlab"> {I.tr("Ident")+":"} </label>
            <label id="callsign"><b> { ident } </b></label>
            { simpleLabel("time",  "lleftlab", I.tr("Time")+":", TXT( df.format(item.getTS()))) ++
              { if (item.speed >= 0) simpleLabel("speed", "lleftlab", I.tr("Speed")+":", TXT(item.speed+" km/h") )
                else xml.NodeSeq.Empty } ++
              simpleLabel("dir",   "lleftlab", I.tr("Heading")+":", _directionIcon(item.course, fprefix(req)))  }
            <div id="traffic">
            { if (item.pathinfo != null) 
                 simpleLabel("via",   "lleftlab", I.tr("APRS via")+":", TXT( cleanPath(item.pathinfo)))
              else null }
            </div>
            </xml:group>
      
      printHtml(res, htmlBody(req, null, result)) 
    }
  
  }
}
