 
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
import spark.Request;
import spark.Response;




/**
 * Web view for Channels.
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
         val cid = req.queryParams("chan")
         val chp = "channel."+cid
         val prefix = <h3>{"Channel"+ " '"+cid+"'"}</h3>
         val is_backup = _api.getChanManager().isBackup(cid);
         var wasOn = _api.getBoolProperty(chp+".on", false)
         var wasType = _api.getProperty(chp+".type", null);      
                
                
         def state: NodeSeq = 
            if (model != null) 
               simpleLabel("info4", "leftlab", "State:", printState(model.getState()))
            else EMPTY
            ;
            
         
         protected def activate: NodeSeq = 
            if (!is_backup) 
                label("item1", "leftlab", "Channel:", "Tick to activate channel") ++
                boolField(chp+".on", "item1", "Activated") ++ br  
            else EMPTY
            ;
            
            
         protected  def typefield: NodeSeq = 
            typeField(chp+".type", "item2", 
                     "Type"+":", 
                     "Type (APRSIS, TNC2, KISS or TCPKISS, etc..)")
            ;
            
         protected def showtype: NodeSeq = 
            simpleLabel("item2", "leftlab", "Type:", TXT(_api.getProperty(chp+".type", "UNDEFINED")))
            ;
            
         protected def backupchan: NodeSeq = 
            if (!is_backup)
                textField(chp+".backup", "item3", 
                     "Backup channel:", 
                     "Channel to be tried if this channel fails", 10, 20, NAME)
            else EMPTY
            ;
             
             
         protected def inetaddr: NodeSeq = 
            textField(chp+".host", "item4", 
                 "Server address:", 
                 "DNS name or IP address for server", 20, 30, NAME) ++
            textField(chp+".port", "item5", 
                 "Server port:", 
                 "Port number", 6, 6, NUMBER)
            ;
         
         
         protected def action_inetaddr(chp: String): NodeSeq = 
             getField(req, "item4", chp+".host", NAME) ++ 
             getField(req, "item5", chp+".port", 1024, 65535) 
             ;
         
         
         protected def serialport: NodeSeq = 
             textField(chp+".port", "item8", 
                "Port:", 
                "Serial port device-name (e.g. /dev/ttyS0)", 12, 20, NAME) ++
             textField(chp+".baud", "item9", 
                "Baud:", "", 6, 8, NUMBER) 
             ;
             
         
         protected def visibility: NodeSeq = 
             label("item10", "leftlab", 
                 "Visibility"+":", 
                 "Tick to limit access to logged in users") ++
             boolField(chp+".restrict", "item10", "Only for logged in users") ++ br ++
             textField(chp+".tag", "item11", "Tag:", "", 10, 10, NAME)
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
                      <span class="fieldsuccess">{ "Deactivating channel" }<br/></span>
                  }
                  else EMPTY
             } ++
             {    if ((chan == null && chtype != null) || !chtype.equals(wasType) ) {
                      clear_config(chp); 
                      api.getChanManager.newInstance(_api, chtype, cid);
                      <span class="fieldsuccess">{ "Creating new channel instance" }<br/></span>
                  }
                  else EMPTY
             } ++
             {  
                  if ((changed || !wasOn) && isOn) {
                      chan.activate(_api);
                      <span class="fieldsuccess">{ "Activating channel" }<br/></span>
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
                simpleLabel("newchan", "leftlab", "New channel:",
                   TXT("Select the type of channel to create and press 'Update'.")) ++ br ++
                typefield
              
              ;
         
         
         
         def action(req : Request): NodeSeq = 
              br ++ br ++
              getField(req, "item2", chp+".type", ConfigUtils.CHANTYPE) ++
              action_activate
              ;
         
   }
  
}
