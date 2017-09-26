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
import uk.me.jstott.jcoord._
import no.polaric.aprsd._
import org.xnap.commons.i18n._
import spark.Request;
import spark.Response;


   
package no.polaric.aprsd.http 
{

   class AprsObjectView 
      ( override val api: ServerAPI, override val model:AprsObject, override val canUpdate: Boolean, override val req: Request) 
            extends AprsPointView(api, model, canUpdate, req) with ServerUtils
   {
       /** Sender info. */
       protected def sender(req : Request): NodeSeq = 
           simpleLabel("owner", "leftlab", I.tr("Sender")+":", <b>{model.getOwner().getIdent()}</b>) 
       
       
       
       override def fields(req : Request): NodeSeq = {
           model.update()
           ident(req) ++
           alias(req) ++
           { if (!simple) aprssym(req) else EMPTY } ++                        
           sender(req) ++
           descr(req) ++
           position(req) ++
           reporttime(req) ++
           basicSettings(req)
       }
   }
  
}
