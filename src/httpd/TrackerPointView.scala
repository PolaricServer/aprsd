 
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
import org.simpleframework.transport.connect.Connection
import org.simpleframework.transport.connect.SocketConnection
import org.simpleframework.http._
import uk.me.jstott.jcoord._
import no.polaric.aprsd._
import org.xnap.commons.i18n._



   
package no.polaric.aprsd.http 
{

   class TrackerPointView 
      ( override val api: ServerAPI, override val model:TrackerPoint, override val canUpdate: Boolean, override val req: Request) 
            extends PointView(api, model, canUpdate, req) with ServerUtils
   {
       
       /** Show alias. */
       protected def alias(req: Request): NodeSeq = 
           if (model.getAlias() != null)
               simpleLabel("alias", "leftlab", I.tr("Alias")+":", <b>{model.getAlias()}</b>) 
           else EMPTY 
       
                                  
       /** Show altitude and course */                    
       protected def heightcourse(req: Request): NodeSeq =
            { if (model.getAltitude() >= 0)
                  simpleLabel("altitude", "leftlab", I.tr("Altitude")+":", TXT(model.getAltitude() + " m ")) else <span></span> } ++
            { if (model.getSpeed() > 0)
                  simpleLabel("cspeed", "leftlab", I.tr("Movement")+":", _directionIcon(model.getCourse(), fprefix(req))) else <span></span> }
            ;
            
       
       /** Basic settings. */
       /* FIXME: Add trail colour when ready. */
       protected def basicSettings(req: Request) = 
           if (edit && canUpdate)
               <div>
               <br/>
               { label("hidelabel", "leftlab", I.tr("Settings")+":") ++
                   checkBox("hidelabel", model.isLabelHidden(), TXT(I.tr("Hide ID"))) ++
                   checkBox("pers", model.isPersistent(), TXT(I.tr("Persistent storage"))) }
               <br/>
               <label for="nalias" class="leftlab"> {I.tr("New alias")+":"} </label>
               { textInput("nalias", 10, 20, 
                    if (model.getAlias()==null) "" else model.getAlias()) }
               <br/>
               { iconSelect(req, model, fprefix(req), "/icons/") }
               </div>
            else EMPTY        
       
       
       
       /** Action for basic settings. */
       /* FIXME: Add trail colour when ready. */
       protected def basicSettings_action(req: Request) = {
             val perm = req.getParameter("pers");
             model.setPersistent( "true".equals(perm) );  
             val hide = req.getParameter("hidelabel");
             model.setLabelHidden( "true".equals(hide) );     

             /* Alias setting */
             var alias = fixText(req.getParameter("nalias"));
             var ch = false;
             if (alias != null && alias.length() > 0)      
                 ch = model.setAlias(alias);
             else
                { ch = model.setAlias(null)
                  alias = "NULL"
                }
             System.out.println("*** ALIAS: '"+alias+"' for '"+model.getIdent()+"' by user '"+getAuthUser(req)+"'")
             if (ch && api.getRemoteCtl() != null)
                 api.getRemoteCtl().sendRequestAll("ALIAS "+model.getIdent()+" "+alias, null);

             /* Icon setting */
             var icon = req.getParameter("iconselect");
             if ("system".equals(icon)) 
                 icon = null; 
             if (model.setIcon(icon) && _api.getRemoteCtl() != null )
                 _api.getRemoteCtl().sendRequestAll("ICON "+model.getIdent() + " " +
                    { if (icon==null) "NULL" else icon }, 
                    null);
            
             <h3>{I.tr("Updated")}</h3>
       }
  
       /** Prefix for trailpoints: id and timestamp. */
       protected def tp_prefix(tp: Trail.Item): NodeSeq = 
         <xml:group>
            <label for="tp_ident" class="lleftlab"> {I.tr("Ident")+":"} </label>
            <label id="tp_ident"><b> { model.getIdent() } </b></label>
            { simpleLabel("tp_time",  "lleftlab", I.tr("Time")+":", TXT( df.format(tp.getTS()))) }
         </xml:group>

         
         
       def trailpoint(req: Request, tp: Trail.Item): NodeSeq = 
            tp_prefix(tp) ++
            { if (tp.speed >= 0) simpleLabel("tp_speed", "lleftlab", I.tr("Speed")+":", TXT(tp.speed+" km/h") )
                else EMPTY } ++
              simpleLabel("tp_dir",   "lleftlab", I.tr("Heading")+":", _directionIcon(tp.course, fprefix(req)))  
       
       
       
       override def fields(req : Request): NodeSeq = 
           ident(req) ++
           alias(req) ++
           descr(req) ++
           position(req) ++
           heightcourse(req) ++ 
           basicSettings(req)
           ;
              
              
       override def action(req : Request): NodeSeq = 
           basicSettings_action(req)
           ;
   }
  
}