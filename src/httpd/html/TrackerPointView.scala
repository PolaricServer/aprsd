 
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

import java.util._
import java.io._
import scala.xml._
import uk.me.jstott.jcoord._
import no.polaric.aprsd._
import org.xnap.commons.i18n._
import spark.Request;
import spark.Response;


   
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
       
       /** Show channel. */
       protected def channel(req: Request): NodeSeq = 
           if (getAuthInfo(req).admin)
               simpleLabel("channel", "leftlab", I.tr("Channel")+":", <span>{model.getSourceId()}</span>) 
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
                   checkBox("hidelabel", model.isLabelHidden(), TXT(I.tr("Hide ID"))) }
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
      
             val hide = req.queryParams("hidelabel");
             model.setLabelHidden( "true".equals(hide) );     

             /* Alias setting */
             var alias = fixText(req.queryParams("nalias"));
             var ch = false;
             if (alias != null && alias.length() > 0)      
                 ch = model.setAlias(alias);
             else
                { ch = model.setAlias(null)
                  alias = "NULL"
                }
             _api.log().info("TrackerPointView", 
                "ALIAS: '"+alias+"' for '"+model.getIdent()+"' by user '"+getAuthInfo(req).userid+"'")
             if (ch && api.getRemoteCtl() != null)
                 api.getRemoteCtl().sendRequestAll("ALIAS "+model.getIdent()+" "+alias, null);

             /* Icon setting */
             var icon = req.queryParams("iconselect");
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
            <label for="tp_ident" class="sleftlab"> {I.tr("Ident")+":"} </label>
            <label id="tp_ident"><b> { model.getIdent() } </b></label>
            { simpleLabel("tp_time",  "sleftlab", I.tr("Time")+":", TXT( ServerBase.df.format(tp.getTS()))) }
         </xml:group>

         
         
       def trailpoint(req: Request, tp: Trail.Item): NodeSeq = 
            tp_prefix(tp) ++
            { if (tp.speed >= 0) simpleLabel("tp_speed", "sleftlab", I.tr("Speed")+":", TXT(tp.speed+" km/h") )
                else EMPTY } ++
              simpleLabel("tp_dir",   "sleftlab", I.tr("Heading")+":", _directionIcon(tp.course, fprefix(req)))  
       
       
       
       override def fields(req : Request): NodeSeq = 
           ident(req) ++
           alias(req) ++
           channel(req) ++
           descr(req) ++
           position(req) ++
           heightcourse(req) ++ 
 //          basicSettings(req)
           ;
              
              
       override def action(req : Request): NodeSeq = 
           basicSettings_action(req)
           ;
   }
  
}
