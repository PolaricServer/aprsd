 
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

   class AprsPointView 
      ( override val api: ServerAPI, override val model:AprsPoint, override val canUpdate: Boolean, override val req: Request) 
            extends TrackerPointView(api, model, canUpdate, req) with ServerUtils
   {

       protected def aprssym(req: Request): NodeSeq = 
           simpleLabel("symbol", "leftlab", I.tr("APRS symbol")+":",TXT( model.getSymtab()+" "+model.getSymbol())) 
       
       
       protected def reporttime(req: Request): NodeSeq = 
           simpleLabel("hrd", "leftlab", I.tr("Last reported")+":", I.tr("Time of last received APRS report"),
                 TXT( df.format(model.getUpdated()))) 
                 
       
       override def fields(req : Request): NodeSeq = 
           ident(req) ++
           alias(req) ++
           { if (!simple) aprssym(req) else <span></span> } ++
           descr(req) ++
           position(req) ++
           reporttime(req) ++
           basicSettings(req)
           ;
   }
  
}