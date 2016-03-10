 
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



/**
 * Web view for PointObjects.
 * This is the top level view class. Extend this for subclasses of PointObject. 
 * It is typically extended by overriding the fields() and action() methods and adding
 * new parts. 
 */
package no.polaric.aprsd.http 
{
   object PointView {
   
      var map = scala.collection.immutable.Map[Class[_<:PointObject], Class[_<:PointView]](); 
      
      def getViewFor(x:PointObject, api: ServerAPI, canUpdate: Boolean, req: Request):PointView = {
          val cls = if (x !=null) map(x.getClass) else null; 
          if (cls == null) 
              new PointView(api,x,canUpdate,req); 
          else 
              cls.getConstructors()(0).newInstance(api, x, canUpdate:java.lang.Boolean, req).asInstanceOf[PointView];
      }
      
      def addView(model: Class[_<:PointObject], view: Class[_<:PointView]) = 
         map += (model -> view)
      ;
   }
   

   class PointView 
      ( val api: ServerAPI, val model:PointObject, val canUpdate:Boolean, val req: Request) 
             extends ServerBase(api) with ServerUtils
   {
       val I = getI18n(req);
       val simple =  ( req.getParameter("simple") != null )
       val edit  =  ( req.getParameter("edit") != null )
    
    
       /** Identifier field. */
       protected def ident(req : Request): NodeSeq =
           <xml:group>  
              <label for="ident" class="leftlab">Ident:</label>
              <label id="ident"><b> 
                { if (model != null && model.getIdent != null) model.getIdent().replaceFirst("@.*","") else "" } 
              </b></label>
           </xml:group>
           
           
       /** Description. */
       protected def descr(req: Request): NodeSeq = 
           if (model != null && model.hasDescr() && model.getDescr().length() > 0)
               simpleLabel("descr", "leftlab", I.tr("Description")+":", TXT(model.getDescr())) else <span></span>
       
       
       
       /** Position info */
       protected def position(req : Request): NodeSeq = 
            simpleLabel("pos", "leftlab", I.tr("Position")+" (UTM):",
                if (model.getPosition() != null) showUTM(req, model.getPosition())
                else TXT(I.tr("not registered")) ) ++
            simpleLabel("posll", "leftlab", I.tr("Position")+" (latlong):",
                if (model.getPosition() != null) TXT( ll2dmString( model.getPosition().toLatLng()))
                else TXT(I.tr("not registered")) )
        
            
            
       
       def fields(req : Request): NodeSeq = 
           ident(req) ++
           descr(req) ++
           position(req)
           ;
              
              
       def action(req : Request): NodeSeq = null;
   }
  
}