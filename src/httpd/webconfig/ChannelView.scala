 
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




/**
 * Web view for Channels.
 * This is the top level view class. Extend this for subclasses of PointObject. 
 * It is typically extended by overriding the fields() and action() methods and adding
 * new parts. 
 */
package no.polaric.aprsd.http
{
   object ChannelView {
   
      var map = scala.collection.immutable.Map[Class[_<:Channel], Class[_<:ChannelView]](); 
      
      def getViewFor(x:Channel, api: ServerAPI, req: Request):ChannelView = 
          map(x.getClass).getConstructors()(0).newInstance(api, x, req).asInstanceOf[ChannelView];
      
      def addView(model: Class[_<:Channel], view: Class[_<:ChannelView]) = 
         map += (model -> view)
      ;
   }
   

   class ChannelView 
      ( val api: ServerAPI, val model: Channel, val req: Request) 
             extends ServerBase(api) with ConfigUtils
   {
         val I = getI18n(req);
         val cid = req.queryParams("chan")
         val chp = "channel."+cid
         val prefix = <h3>{I.tr("Channel")+ " '"+cid+"'"}</h3>
         val is_backup = _api.getChanManager().isBackup(cid);
         var wasOn = _api.getBoolProperty(chp+".on", false)
         var wasType = _api.getProperty(chp+".type", null);      
                
                
         def state: NodeSeq = 
            if (model != null) 
               simpleLabel("info4", "leftlab", I.tr("State")+":", printState(model.getState(), I))
            else EMPTY
            ;
            
         
         protected def activate: NodeSeq = 
            if (!is_backup) 
                label("item1", "leftlab", I.tr("Channel")+":", I.tr("Tick to activate channel")) ++
                boolField(chp+".on", "item1", I.tr("Activated")) ++ br  
            else EMPTY
            ;
            
            
         protected  def typefield: NodeSeq = 
            typeField(chp+".type", "item2", 
                     I.tr("Type")+":", 
                     I.tr("Type (APRSIS, TNC2, KISS or TCPKISS, etc..)"))
            ;
            
         protected def showtype: NodeSeq = 
            simpleLabel("item2", "leftlab", I.tr("Type")+":", TXT(_api.getProperty(chp+".type", "UNDEFINED")))
            ;
            
         protected def backupchan: NodeSeq = 
            if (!is_backup)
                textField(chp+".backup", "item3", 
                     I.tr("Backup channel")+":", 
                     I.tr("Channel to be tried if this channel fails"), 10, 20, NAME)
            else EMPTY
            ;
             
             
         protected def inetaddr: NodeSeq = 
            textField(chp+".host", "item4", 
                 I.tr("Server address")+":", 
                 I.tr("DNS name or IP address for server"), 20, 30, NAME) ++
            textField(chp+".port", "item5", 
                 I.tr("Server port")+":", 
                 I.tr("Port number"), 6, 6, NUMBER)
            ;
         
         
         protected def action_inetaddr(chp: String): NodeSeq = 
             getField(req, "item4", chp+".host", NAME) ++ 
             getField(req, "item5", chp+".port", 1024, 65535) 
             ;
         
         
         protected def serialport: NodeSeq = 
             textField(chp+".port", "item8", 
                I.tr("Port")+":", 
                I.tr("Serial port device-name (e.g. /dev/ttyS0)"), 12, 20, NAME) ++
             textField(chp+".baud", "item9", 
                I.tr("Baud")+":", "", 6, 8, NUMBER) 
             ;
             
         
         protected def visibility: NodeSeq = 
             label("item10", "leftlab", 
                 I.tr("Visibility")+":", 
                 I.tr("Tick to limit access to logged in users")) ++
             boolField(chp+".restrict", "item10", I.tr("Only for logged in users")) ++ br ++
             textField(chp+".tag", "item11", I.tr("Tag")+":", "", 10, 10, NAME)
             ;
         
         protected def action_visibility: NodeSeq = 
             getField(req, "item10", chp+".restrict", BOOLEAN) ++
             getField(req, "item11", chp+".tag", NAME)
             ;
             
        
         protected def clear_config(chp: String) = {
             val cf = _api.getConfig()
             cf.remove(chp+".port")
             cf.remove(chp+".baud")
             cf.remove(chp+".host")
             cf.remove(chp+".backup")
             cf.setProperty(chp+".port", "")
         }
             
             
         protected def action_activate = {    
             val chtype = _api.getProperty(chp+".type", null);
             var chan = _api.getChanManager().get(cid)
             val isOn = _api.getBoolProperty(chp+".on", false)
            
            
             {
                  if ((changed || !isOn) && wasOn) {
                      chan.deActivate();
                      <span class="fieldsuccess">{ I.tr("Deactivating channel") }<br/></span>
                  }
                  else EMPTY
             } ++
             {    if ((chan == null && chtype != null) || !chtype.equals(wasType) ) {
                      clear_config(chp); 
                      api.getChanManager.newInstance(_api, chtype, cid);
                      <span class="fieldsuccess">{ I.tr("Creating new channel instance") }<br/></span>
                  }
                  else EMPTY
             } ++
             {  
                  if ((changed || !wasOn) && isOn) {
                      chan.activate(_api);
                      <span class="fieldsuccess">{ I.tr("Activating channel") }<br/></span>
                  }
                  else EMPTY
             } ++
             {
                  wasOn = isOn;
                  wasType = chtype;
                  changed = false; 
                  EMPTY
             }
         } 
             
             
         def fields(req : Request): NodeSeq =   
                simpleLabel("newchan", "leftlab", I.tr("New channel"+":"),
                   TXT(I.tr("Select the type of channel to create and press 'Update'."))) ++ br ++
                typefield
              
              ;
         
         
         
         def action(req : Request): NodeSeq = 
              br ++ br ++
              getField(req, "item2", chp+".type", ConfigUtils.CHANTYPE) ++
              action_activate
              ;
         
   }
  
}
