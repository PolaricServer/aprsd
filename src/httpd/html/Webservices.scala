/* 
 * Copyright (C) 2015-2020 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import org.xnap.commons.i18n._
import spark.Request;
import spark.Response;


   
package no.polaric.aprsd.http 
{

  class Webservices 
      ( val api: ServerAPI) extends XmlServices(api) with ServerUtils
  {
 
   val _time = new Date();
   
   /* 
    * Register views for position objects. 
    * addView ( class-of-model, class-of-view )
    */
   PointView.addView(classOf[AprsObject],  classOf[AprsObjectView])
   PointView.addView(classOf[Station],     classOf[AprsStationView])
   PointView.addView(classOf[OwnPosition], classOf[AprsPointView])
   PointView.addView(classOf[GpsPosition], classOf[AprsPointView])


   
   /**
    * Show clients that are active just now.
    */
   def handle_listclients(req : Request, res: Response): String =
   {       
       val I = getI18n(req)
       val head = <meta http-equiv="refresh" content="30" />
       val ws = _api.getWebserver().asInstanceOf[WebServer];
       
       def action(req : Request): NodeSeq =
         if (!getAuthInfo(req).admin)
              <h3>{I.tr("You are not authorized for such operations")}</h3>
         else     
              <h3>{I.tr("Clients active on server")}</h3>
              <fieldset>
              <table>
              <tr><th>Created</th><th>Client</th><th>In</th><th>Out</th><th>Userid</th></tr>
        
              { for (c <- ws.getJsonMapUpdater().clients()) yield
                   <tr><td>{ServerBase.tf.format(c.created())}</td><td>{c.getUid()}</td>
                       <td>{c.nIn()}</td><td>{c.nOut()}</td><td>{c.getUsername()}</td></tr> }
              </table>
              </fieldset>
              
       return printHtml (res, htmlBody(req, head, action(req))); 
   }

   
    
   /** 
    * Admin interface. 
    * To be developed further...
    */
   def handle_admin(req : Request, res: Response): String =
   {   
       val I = getI18n(req)
       val cmd = req.queryParams("cmd")
       val head = <meta http-equiv="refresh" content="60" />


       def action(req : Request): NodeSeq =
          if (!getAuthInfo(req).sar)
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
                simpleLabel("items", "leftlab", I.tr("Number of items:"), TXT(""+_api.getDB().nItems())) ++
                simpleLabel("items", "leftlab", I.tr("Number of connections:"), TXT(""+_api.getDB().getRoutes().nItems())) ++
                simpleLabel("items", "leftlab", I.tr("Own objects:"), TXT(""+_api.getDB().getOwnObjects().nItems())) ++   
                simpleLabel("items", "leftlab", I.tr("Number of clients now:"), 
                  TXT(""+(_api.getWebserver().nClients()) + " ("+_api.getWebserver().nLoggedin()+" "+ I.tr("logged in")+")" ) ) ++  
                simpleLabel("items", "leftlab", I.tr("Number of HTTP requests:"), 
                  TXT(""+(_api.getWebserver().nHttpReq()))) }    
              { simpleLabel("freemem", "leftlab", I.tr("Used memory:"), 
                  TXT( Math.round(StationDBImp.usedMemory()/1000)+" KBytes")) }   
                                
              { simpleLabel ("plugins", "leftlab", I.tr("Plugin modules")+": ", 
                 <div>
                    { if (PluginManager.isEmpty())
                         TXT("---") ++ br
                      else 
                         PluginManager.getPlugins.toSeq.map
                          { x => TXT(x.getDescr()) ++ br } 
                    }
                 </div> 
              )}
              
              { simpleLabel("channels", "leftlab", I.tr("Channels")+": ",
                <div>
                  { api.getChanManager().getKeys.toSeq.map
                     { x => api.getChanManager().get(x) }.map 
                        { ch => TXT(ch.getShortDescr()+": " + ch.getIdent()) ++
                             (if (ch.isActive())  "  ["+ I.tr("Active")+"]" else "") ++ br } 
                  }
                </div>
              )}
              
              
              { simpleLabel("igate", "leftlab", "Igate: ", 
                   if (_api.getIgate()==null) TXT("---") else TXT(""+_api.getIgate())) }   
              { if (_api.getRemoteCtl() != null && !_api.getRemoteCtl().isEmpty())  
                   simpleLabel("rctl", "leftlab", I.tr("Remote control")+": ", TXT(""+_api.getRemoteCtl())) else null; }     
                   
              </fieldset>  
              <button type="submit" onclick="top.window.close()" id="cancel">{I.tr("Cancel")} </button>
          else
              <h3>{I.tr("Unknown command")}</h3>
             
          return printHtml (res, htmlBody(req, head, action(req)));    
   }
   
   
   
   def symChoice(req: Request) = {
        val I = getI18n(req)
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
    }
   
         



    protected def itemList(I:I18n, list: List[TrackerPoint], mobile: Boolean, 
                  fprefix: String, loggedIn: Boolean): NodeSeq = 
      <table>
         <thead>
           <th>{I.tr("Ident")}</th><th>{I.tr("Updated")}</th>
           <th id="ilist_move">{I.tr("Move")}</th><th id="ilist_descr">{I.tr("Description")}</th>
         </thead> 
         <tbody>
         {
            for ( x:TrackerPoint <- list  
                  if (!x.getSource().isRestricted() || loggedIn || x.hasTag("OPEN"))) yield
            {
               val moving = !x.getTrail().isEmpty()
               var descr = x.getDescr()
               if (descr != null && descr.length() > 60)
                  descr = descr.substring(0,60)+"..";
                  
               <tr class={
                 if (x.visible() && x.getPosition() != null) 
                      "" else "nopos" } 
                 onclick={
                 if (x.visible() && x.getPosition() != null) 
                     "findItem('" + x.getIdent() + "', 'true')"
                 else "" 
               }>
          
               <td>{x.getDisplayId()}</td>
               <td> { ServerBase.df.format(x.getUpdated()) } </td>
               <td> 
               { if (moving && x.getSpeed() > 0)
                       _directionIcon(x.getCourse(), fprefix) 
                  else 
                      EMPTY } </td>

               { if (!mobile) 
                  <td> { if (descr != null) descr else "" } </td> 
                 else null }
               </tr>
           }
        } 
      </tbody>
      </table>
      ;
       
       


   /** 
    * View one particular point object. 
    */
   def handle_station(req : Request, res : Response): String  = 
       return _handle_station(req, res, getAuthInfo(req).sar)
       ;
   
   

   def _handle_station(req : Request, res : Response, canUpdate: Boolean): String =
   {
       val simple =  ( req.queryParams("simple") != null )
       val id = req.queryParams("id")
       val x:TrackerPoint = _api.getDB().getItem(id, null)
       val view = PointView.getViewFor(x, api, canUpdate, req)
       
       return printHtml (res, htmlBody ( req, null, 
                    if (simple) view.fields(req)
                    else htmlForm(req, null, view.fields, IF_AUTH(view.action), true, default_submit)))
   }
  


  

    def handle_trailpoint(req : Request, res : Response): String =
    {
       val I = getI18n(req)
       val time = ServerBase.xf.parse(req.queryParams("time"))
       val ident = req.queryParams("id")
       var x:TrackerPoint = _api.getDB().getItem(ident, time)
       if (x==null)
           x = _api.getDB().getItem(ident, null)
       val item = _api.getDB().getTrailPoint(ident, time)
       
       val result : NodeSeq = 
          if (item == null)
             TXT(I.tr("Couldn't find info")+": "+ident)
          else {
             val view = PointView.getViewFor(x, _api, false, req).asInstanceOf[TrackerPointView]
             view.trailpoint(req, item)
          }
      
      return printHtml(res, htmlBody(req, null, result)) 
    }
    
    
    
    val ddf = new java.text.DecimalFormat("##,###.###");
    def dfield(x: String, y: String) = 
       <xml:group>{x}<span class="dunit">{y}</span></xml:group>
       
       
    def handle_telemetry(req : Request, res : Response): String = 
    {
         val I = getI18n(req)
         val id = req.queryParams("id")
         val x:Station = _api.getDB().getItem(id, null).asInstanceOf[Station]
         val tm = x.getTelemetry(); 
         val d = tm.getCurrent();
         val result: NodeSeq =
           <xml:group>
             <h1>{ if (tm.getDescr()==null) I.tr("Telemetry from") + " " + x.getIdent() 
                   else tm.getDescr() }</h1>
             { for (i <- 0 to Telemetry.ANALOG_CHANNELS-1) yield {
                 var parm = tm.getMeta(i).parm
                 var unit = tm.getMeta(i).unit
                 parm = if (parm == null) I.tr("Channel")+" "+i else parm
                 unit = if (unit == null) "" else unit
                 
                 simpleLabel("chan" + i, "lleftlab", parm + ":", 
                    dfield("" + ddf.format(d.getAnalog(i)), unit)) 
               }
             } 
             { if (d.time != null) 
                  simpleLabel("hrd", "lleftlab", I.tr("Last reported")+":", I.tr("Time of last received telemetry report"),
                     TXT( ServerBase.df.format(d.time))) 
               else EMPTY       
             }
             <div id="bintlm">
             { for (i <- 0 to Telemetry.BINARY_CHANNELS-1; if (tm.getBinMeta(i).use)) yield {
                  val lbl = if (tm.getBinMeta(i).parm==null) "B"+i else tm.getBinMeta(i).parm
                  val cls = if (tm.getCurrent().getBinary(i)) "tlm on" else "tlm"
                  <div class={cls}>{lbl}</div>
               }
             } 
             </div>
             <div id="tlmhist"></div>
                  
             <button id="listbutton">{I.tr("Previous Data")}</button>
           </xml:group>
         
        return printHtml(res, htmlBody(req, null, result)) 
    }
    

  
  
    def handle_telhist(req : Request, res : Response): String = 
    {
         val I = getI18n(req)
         val id = req.queryParams("id")
         val x:Station = _api.getDB().getItem(id, null).asInstanceOf[Station]
         val tm = x.getTelemetry(); 
        
         val result: NodeSeq =
           <xml:group>
             <table>
             <tr>
             <th>Time</th>
             { for (i <- 0 to Telemetry.ANALOG_CHANNELS-1) yield {
                 var parm = tm.getMeta(i).parm
                 var unit = tm.getMeta(i).unit
                 parm = if (parm == null) I.tr("Ch")+i else parm
                 unit = if (unit == null) "" else unit
                 
                 <th title={ unit }> { parm } </th>
               }
             } 
             { for (i <- 0 to Telemetry.BINARY_CHANNELS-1; if (tm.getBinMeta(i).use)) yield {
                  val lbl = if (tm.getBinMeta(i).parm==null) "B"+i else tm.getBinMeta(i).parm
                  <th>{lbl}</th>
               }
             }
             </tr>
             { for (d <- tm.getHistory())  yield       
                <tr>
                <td> { ServerBase.df.format(d.time) } </td>
                 { for (i <- 0 to Telemetry.ANALOG_CHANNELS-1) yield 
                     <td>
                        { ddf.format(d.getAnalog(i)) }
                     </td>
                   
                 }
                 { for (i <- 0 to Telemetry.BINARY_CHANNELS-1; if (tm.getBinMeta(i).use)) yield {
                      var unit = if (tm.getBinMeta(i).unit==null) "true" else tm.getBinMeta(i).unit
                      <td> { if (d.getBinary(i)) unit else "" } </td>
                    }
                 }
                 </tr>
             }
             </table>

             
           </xml:group>
         
        return printHtml(res, htmlBody(req, null, result)) 
    }
  }
  
}
