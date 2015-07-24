 
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
import scala.xml._
import scala.collection.JavaConversions._
import org.simpleframework.transport.connect.Connection
import org.simpleframework.transport.connect.SocketConnection
import org.simpleframework.http._
import uk.me.jstott.jcoord._
import no.polaric.aprsd._
import org.xnap.commons.i18n._



   
package no.polaric.aprsd.http 
{

   class AprsStationView 
      ( override val api: ServerAPI, override val model:Station, override val canUpdate: Boolean, override val req: Request) 
            extends AprsPointView(api, model, canUpdate, req) with ServerUtils
   {
       val pathinfo = if (model.getPathInfo() != null && model.getPathInfo().length() > 1) 
                           cleanPath(model.getPathInfo()) else null

            
            
       protected def reportinfo(req: Request): NodeSeq = {
            var txt = "";              
            if (model.isIgate()) txt += "IGATE "; 
            if (model.isWideDigi()) txt += I.tr("Wide-Digi");

            { if (pathinfo != null) simpleLabel("via", "leftlab", I.tr("Via")+":", I.tr("Which route the last APRS report took"), 
                     TXT(pathinfo)) else <span></span> } ++
            { if ((model.isIgate() || model.isWideDigi()) && simple) simpleLabel("infra", "leftlab", I.tr("Infrastructure")+":", 
                     TXT(txt)) else <span></span> } 
       }
      
      
       protected def trafficinfo(req: Request): NodeSeq =         
           <div id="traffic">
              { if (model.isInfra() )
                 {  <label for="hrds" class="leftlab"> {I.tr("Traffic from")+":"} </label>
                    <label id="hrds"> { itemList(model.getTrafficFrom()) } </label> } else null }
              { if (model.getTrafficTo != null && !model.getTrafficTo.isEmpty)
                 {  <label for="hrd" class="leftlab"> {I.tr("Traffic to")+":"} </label>
                    <label id="hrd"> { itemList(model.getTrafficTo()) } </label> } else null }
           </div>                  
            ;
            
            
        private def itemList(items : Set[String]) = 
             <div class="trblock">
             {  
                var i=0
                if (items != null) 
                for (it <- items.iterator) yield {
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
            
            
       override def fields(req : Request): NodeSeq = {
           ident(req) ++
           alias(req) ++
           descr(req) ++
           position(req) ++
           heightcourse(req) ++
           reporttime(req) ++
           reportinfo(req) ++
           { if (simple) trafficinfo(req) else <span></span> } ++
           basicSettings(req)
       }
   }
  
}