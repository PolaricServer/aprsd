/* 
 * Copyright (C) 2015-2022 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import spark.Request;
import spark.Response;


   
package no.polaric.aprsd.http 
{

  class Webservices 
      ( val api: ServerAPI) extends XmlServices(api) with ServerUtils
  {
 
   val _time = new Date();
   

   /**
    * Show clients that are active just now.
    */
   def handle_listclients(req : Request, res: Response): String =
   {       
       val head = <meta http-equiv="refresh" content="30" />
       val ws = _api.getWebserver().asInstanceOf[WebServer];
       
       def action(req : Request): NodeSeq =
         if (!getAuthInfo(req).admin)
              <h3>{"You are not authorized for such operations"}</h3>
         else     
              <h3>{"Clients active on server"}</h3>
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
       val cmd = req.queryParams("cmd")
       val head = <meta http-equiv="refresh" content="60" />


       def action(req : Request): NodeSeq =
          if (!getAuthInfo(req).sar && !getAuthInfo(req).admin)
              <h3>{"You are not authorized for such operations"}</h3>

          else if ("clearobj".equals(cmd)) {
              _api.getDB().getOwnObjects().clear()
              <h3>{"Clear own objects, ok"}</h3>
          }
          else if ("clearlinks".equals(cmd)) {
              _api.getDB().getRoutes().clear()
              <h3>{"Clear link information, ok"}</h3>    
          }
          else if ("info".equals(cmd))    
              <h3>Status info for Polaric APRSD</h3>
              <fieldset>
              { simpleLabel("items", "leftlab", "Server run since:", TXT(""+_time)) ++
                simpleLabel("items", "leftlab", "Server version:", TXT(""+_api.getVersion())) ++ 
                simpleLabel("items", "leftlab", "Number of items:", TXT(""+_api.getDB().nItems())) ++
                simpleLabel("items", "leftlab", "Number of connections:", TXT(""+_api.getDB().getRoutes().nItems())) ++
                simpleLabel("items", "leftlab", "Own objects:", TXT(""+_api.getDB().getOwnObjects().nItems())) ++   
                simpleLabel("items", "leftlab", "Number of clients now:", 
                  TXT(""+(_api.getWebserver().nClients()) + " ("+_api.getWebserver().nLoggedin()+" "+ "logged in)" ) ) ++  
                simpleLabel("items", "leftlab", "Number of HTTP requests:", 
                  TXT(""+(_api.getWebserver().nHttpReq()))) }    
              { simpleLabel("freemem", "leftlab", "Used memory:", 
                  TXT( Math.round(StationDBBase.usedMemory()/1000)+" KBytes")) }   
                                
              { simpleLabel ("plugins", "leftlab", "Plugin modules"+": ", 
                 <div>
                    { if (PluginManager.isEmpty())
                         TXT("---") ++ br
                      else 
                         PluginManager.getPlugins.toSeq.map
                          { x => TXT(x.getDescr()) ++ br } 
                    }
                 </div> 
              )}
              
              { simpleLabel("channels", "leftlab", "Channels: ",
                <div>
                  { api.getChanManager().getKeys.toSeq.map
                     { x => api.getChanManager().get(x) }.map 
                        { ch => TXT(ch.getShortDescr()+": " + ch.getIdent()) ++
                             (if (ch.isActive())  "  [Active]" else "") ++ br } 
                  }
                </div>
              )}
              
              
              { simpleLabel("igate", "leftlab", "Igate: ", 
                   if (_api.getIgate()==null) TXT("---") else TXT(""+_api.getIgate())) }   
              { if (_api.getRemoteCtl() != null && !_api.getRemoteCtl().isEmpty())  
                   simpleLabel("rctl", "leftlab", "Remote control"+": ", TXT(""+_api.getRemoteCtl())) else null; }     
                   
              </fieldset>  
              <button type="submit" onclick="top.window.close()" id="cancel">{"Cancel"} </button>
          else
              <h3>{"Unknown command"}</h3>
             
          return printHtml (res, htmlBody(req, head, action(req)));    
   }


    
    val ddf = new java.text.DecimalFormat("##,###.###");
    def dfield(x: String, y: String) = 
       <xml:group>{x}<span class="dunit">{y}</span></xml:group>
       
  

  }
  
}
