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
              <tr><th>Created</th><th>Client</th><th>In</th><th>Out</th><th>Userid</th><th>Fmt</th></tr>
        
              { for (c <- ws.getMapUpdater().clients()) yield
                   <tr><td>{ServerBase.tf.format(c.created())}</td><td>{c.getUid()}</td>
                       <td>{c.nIn()}</td><td>{c.nOut()}</td><td>{c.getUsername()}</td><td>XML</td></tr>  }
                   
              { for (c <- ws.getJsonMapUpdater().clients()) yield
                   <tr><td>{ServerBase.tf.format(c.created())}</td><td>{c.getUid()}</td>
                       <td>{c.nIn()}</td><td>{c.nOut()}</td><td>{c.getUsername()}</td><td>JSON</td></tr> }
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
   
   
   
    /**
    * set own position
    */          
   def handle_setownpos(req : Request, res: Response): String =
   {
        val I = getI18n(req)
        val pos = getCoord(req)
        val prefix = <h2> {I.tr("Set your own position")} </h2>
        val p = api.getOwnPos()
       
        /* Fields to be filled in */
        def fields(req : Request): NodeSeq =
            <xml:group>
            {
              label("osymtab", "lleftlab", I.tr("Symbol")+":", 
                    I.tr("APRS symbol table and symbol. Fill in or chose from the list at the right side.")) ++
              textInput("osymtab", 1, 1, ""+p.getSymtab()) ++
              textInput("osym", 1, 1, ""+p.getSymbol()) ++
              symChoice(req) ++
              br ++
              label("utmz", "lleftlab", I.tr("Pos (UTM)")+":", I.tr("Position in UTM format.")) ++
              {  if (pos==null)
                    utmForm('W', 34)
                 else
                    showUTM(req, pos)
              }
           }
           </xml:group>
           ;  
             
        /* Action. To be executed when user hits 'submit' button */
        def action(req : Request): NodeSeq = {
               if (!getAuthInfo(req).admin)
                  <h3> {I.tr("You are not authorized for admin operations")} </h3>
               else {
                  val osymtab = req.queryParams("osymtab")
                  val osym  = req.queryParams("osym")
                  _api.log().info("Webservices","SET OWN POS by user '"+getAuthInfo(req).userid+"'")
                  p.updatePosition(new Date(), pos, 
                      if (osymtab==null) '/' else osymtab(0), if (osym==null) 'c' else osym(0))
                  
                 <h2> {I.tr("Position registerred")} </h2>
                 <p>pos={ showUTM(req, pos) }</p>
              }
        };
            
        return printHtml (res, htmlBody (req, null, htmlForm(req, prefix, fields, IF_AUTH(action))))
    }   
     
   
   
   
   
   def handle_sarmode(req : Request, res: Response): String =
   {
       val I = getI18n(req)
       val prefix = <h2> {I.tr("Search and rescue mode")} </h2>
       var filter = req.queryParams("sar_prefix")
       var reason = req.queryParams("sar_reason")
       var hidesar = req.queryParams("sar_hidesar")
       val on = req.queryParams("sar_on")
       
       def fields(req : Request): NodeSeq =          
           <xml:group>
           <p> {I.tr("Certain types of info only visible for authorized users.")} </p>
           <label for="sar_on" class="lleftlab"> {I.tr("SAR mode")+":"} </label>
           { checkBox("sar_on", api.getSar() !=null, TXT(I.tr("activated"))) }  
           <br/>
           <label for="sar_hidesar" class="lleftlab"> {I.tr("Alias/icon")+":"} </label>
           { checkBox("sar_hidesar", api.getSar()==null || api.getSar().isAliasHidden(), TXT(I.tr("hidden"))) }  
           <br/>
           
           <label for="sar_prefix" class="lleftlab"> {I.tr("Hide prefix")+":"} </label>
           { textInput("sar_prefix", 25, 50,
               if ( api.getSar()==null) "" else api.getSar().getFilter() ) }
           <br/>
           
           
           <label for="sar_reason" class="lleftlab"> {I.tr("Description")+":"} </label>
           { if (api.getSar() == null)
                textInput("sar_reason", 25, 50, "")
             else 
                <xml:group>
                <label id="sar_reason">{ api.getSar().getReason() }
                <em> { "("+api.getSar().getUser()+")" } </em></label>
                <br/>
                { simpleLabel("sar_date", "lleftlab", I.tr("Activated")+":", TXT(""+api.getSar().getTime())) }
                </xml:group>
           }
           </xml:group>     
              
              
       def action(req : Request): NodeSeq = 
       {
          TrackerPoint.abortWaiters(true);
          if (on != null && "true".equals(on) ) {
               val hide = (hidesar != null && "true".equals(hidesar) ) 
               val filt = if ("".equals(filter)) "NONE" else filter;
               
               api.setSar(reason, getAuthInfo(req).userid, filter, hide);
               
               reason = if (!hide) "[NOHIDE] "+reason 
                        else reason;
               if (api.getRemoteCtl() != null)
                   api.getRemoteCtl().sendRequestAll("SAR "+getAuthInfo(req).userid+" "+filt+" "+reason, null);
               
               
               <h3> {I.tr("Activated")} </h3>
               <p>{reason}</p>
          }
          else {   
               api.clearSar();
               if (api.getRemoteCtl() != null)
                  api.getRemoteCtl().sendRequestAll("SAR OFF", null);
               <h3> {I.tr("Ended")} </h3>
          } 
       }
            
       
       return printHtml (res, htmlBody (req, null, htmlForm(req, prefix, fields, IF_AUTH(action) )))
   }
   
   
   
   /**
    * Delete APRS object.
    */          

   def handle_deleteobject(req : Request, res: Response): String =
   {
       val I = getI18n(req)
       var id = req.queryParams("objid")
       id = if (id != null) id.replaceFirst("@.*", "") else null
       val prefix = <h2> { I.tr("Remove object")} </h2>
       
       def fields(req : Request): NodeSeq =
           <xml:group>
           <label for="objid" class="lleftlab">Objekt ID:</label>
           { textInput("objid", 9, 9, 
                if (id==null) "" else id.replaceFirst("@.*", "") ) }
           </xml:group>
           ;
      
      
       def action(req : Request): NodeSeq =
          if (id == null) {
              <h3> {I.tr("Error")+":"} </h3>
              <p> {I.tr("'objid' must be given as parameter")} </p>;
          }
          else {
              if (_api.getDB().getOwnObjects().delete(id)) {
                  _api.log().info("Webservices", "DELETE OBJECT: '"+id+"' by user '"+getAuthInfo(req).userid+"'")
                  <h3> {I.tr("Object removed!")} </h3>
              }
              else
                  <h3> {I.tr("Couldn't find object")+": "+id} </h3>
          }  
          ;
          
       return printHtml (res, htmlBody (req, null, htmlForm(req, prefix, fields, IF_AUTH(action) )))
   }          


   
   
   def taglist(item: PointObject, args: Array[String], freeField: Boolean): NodeSeq = 
   {
     def hasTag(x: String): Boolean = if (item!=null) inArgs(x) || item.hasTag(x) 
                                      else inArgs(x)
     def inArgs(x: String): Boolean = if (args!= null) args.map(y => y.equals(x)).reduce((y,z) => y || z) 
                                      else false
     
     <div class="taglist"> {
            for (x <- PointObject.getUsedTags.toSeq) yield {
               val pfx = x.split('.')(0)
               if (pfx.length == x.length || hasTag(pfx) || !PointObject.tagIsUsed(pfx))
                   checkBox("tag_"+x, hasTag(x), 
                        TXT(x)) ++ br
               else
                   EMPTY
            }
         }
         { if (freeField) checkBox("newtagc", false, textInput("newtag", 10, 20, ""))
           else EMPTY }
     </div>
   }
         
         

   def handle_addtag(req : Request, res : Response): String =
   {
       val I = getI18n(req)
       val id = req.queryParams("objid")
       val item = _api.getDB().getItem(id, null);
       val prefix = <h2> {I.tr("Tags for: "+id)} </h2>
       
       def fields(req : Request): NodeSeq = 
          taglist(item, null, true)
             
           
       def setTag(item:PointObject, tag:String)
       {
           item.setTag(tag)
           _api.log().info("Webservices", "TAG: '"+tag+"' for '"+item.getIdent()+"' by user '"+getAuthInfo(req).userid+"'")
           if (api.getRemoteCtl() != null)
              api.getRemoteCtl().sendRequestAll("TAG "+item.getIdent()+" "+tag, null);
       }
       
       def removeTag(item:PointObject, tag:String)
       {
           item.removeTag(tag)
           _api.log().info("Webservices", "REMOVE TAG: '"+tag+"' for '"+item.getIdent()+"' by user '"+getAuthInfo(req).userid+"'")
           if (api.getRemoteCtl() != null)
              api.getRemoteCtl().sendRequestAll("RMTAG "+item.getIdent()+" "+tag, null);
       }
      
      
       def action(req : Request): NodeSeq = {
           val newtag = req.queryParams("newtag")
           val newtagc = req.queryParams("newtagc")
           val newtagcc = (newtag != null && newtagc != null && newtagc.equals("true"));
           <div>
           { for (x:String <- PointObject.getUsedTags.toSeq) yield {
               val ux = req.queryParams("tag_"+x);
               val uxx = (ux != null && ux.equals("true"));
               
               if (uxx != item.hasTag(x)) {
                   if (uxx)
                       setTag(item,x)
                   else 
                       removeTag(item, x)
                   <span class="fieldsuccess">{"Tag '"+x+"'. " + 
                     (if (uxx) "Added" else "Removed") + "."}</span><br/>
               }
               else
                   <span>{"Tag '"+x+"'. No change."}</span><br/>
           }} 
           {  if (newtagcc) {
                setTag(item, newtag)
                
                <span class="fieldsuccess">{"Tag '"+newtag+"'. Added."}</span>
              }
              else EMPTY
           }
           </div>
         }
          
          
       return printHtml (res, htmlBody (req, null, htmlForm(req, prefix, fields, IF_AUTH(action))))
   }          

   
   /**
    * Remove trail from station. 
    */
   def handle_resetinfo(req : Request, res : Response): String =
   {
       val I = getI18n(req)
       val id = req.queryParams("objid")
       val prefix = <h2> {I.tr("Reset info about station/object")} </h2>
       
       def fields(req : Request): NodeSeq =
           <label for="objid" class="lleftlab">Objekt ID:</label>
           <input id="objid" name="objid" type="text" size="9" maxlength="9"
              value={if (id==null) "" else id} />;
           ;
      
       def action(req : Request): NodeSeq =
          if (id == null) {
              <h3> {I.tr("Error")+":"} </h3>
              <p> {I.tr("'objid' must be given as parameter")} </p>;
          }
          else {
             val x = _api.getDB().getItem(id, null);
             if (x != null)
                x.reset();
             <h3> {I.tr("Info about object is reset!")} </h3>
          } 
          ;
          
       return printHtml (res, htmlBody (req, null, htmlForm(req, prefix, fields, IF_AUTH(action))))
   }          



             
   /**
    * Add or edit APRS object.
    */          
   def handle_addobject(req : Request, res : Response): String =
   {
        val I = getI18n(req)
        val pos = getCoord(req)
        val id = fixText(req.queryParams("objid"))
        val prefix = <h2> {I.tr("Add/edit object")} </h2>
        
        /* Fields to be filled in */
        def fields(req : Request): NodeSeq =
            <xml:group>
            {
              label("objid", "lleftlab", I.tr("Object ID")+":", I.tr("Identifier for object")) ++
              textInput("objid", 9, 9, "") ++
              br ++
              label("osymtab", "lleftlab", I.tr("Symbol")+":", 
                     I.tr("APRS symbol table and symbol. Fill in or chose from the list at the right side.")) ++
              textInput("osymtab", 1, 1, "/") ++
              textInput("osym", 1, 1, "c") ++
              symChoice(req) ++
              br ++
              label("descr", "lleftlab", I.tr("Description")+":", "") ++
              textInput("descr", 30, 40, "") ++
              br ++
              label("utmz", "lleftlab", I.tr("Pos (UTM)")+":", I.tr("Object's position")) ++
              {  if (pos==null)
                   utmForm('W', 34)
                 else
                   showUTM(req, pos)
              }
            }
            <br/>
            <div>
            {
              label("perm", "lleftlab", I.tr("Settings")+":", "") ++
              checkBox("perm", false, TXT(I.tr("Timeless (Permanent)")))
            }
            </div>
            </xml:group>
            ;
            

            
            
        /* Action. To be executed when user hits 'submit' button */
        def action(request : Request): NodeSeq =
            if (id == null || !id.matches("[a-zA-Z0-9_].*\\w*")) {
               <h2> { I.tr("Invalid id {0}",id)} </h2>
               <p>  { I.tr("please give 'objid' as a parameter. This must start with a letter/number")} </p>;
            }
            else {
               val osymtab = req.queryParams("osymtab")
               val osym  = req.queryParams("osym")
               val otxt = req.queryParams("descr")
               val perm = req.queryParams("perm")
               
               _api.log().info("Webservices", "SET OBJECT: '"+id+"' by user '"+getAuthInfo(request).userid+"'")
               if ( _api.getDB().getOwnObjects().add(id, 
                      new AprsHandler.PosData( pos,
                         if (osym==null) 'c' else osym(0) ,
                         if (osymtab==null) '/' else osymtab(0)),
                      if (otxt==null) "" else otxt,
                      "true".equals(perm) ))
                  
                  <h2> {I.tr("Objekt updated")} </h2>
                  <p>ident={"'"+id+"'"}<br/>pos={showUTM(req, pos) }</p>;
               else
                  <h2> {I.tr("Cannot update")} </h2>
                  <p>  {I.tr("Object '{0}' is already added by someone else", id)} </p>
            }
            ;
            
        return printHtml (res, htmlBody (req, null, htmlForm(req, prefix, fields, IF_AUTH(action))))
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
    * Send instant message. 
    */
   protected def handle_sendmsg(req : Request, res : Response): String = 
   {
        val I = getI18n(req)
        val mbox = _api.getWebserver().getMbox()
        val prefix = <h2> {I.tr("Send instant message")} </h2>
        
        
        def userList(id: String): NodeSeq = 
           <select name={id} id={id}> 
            { for (x:String <- mbox.getUsers) yield
                 <option value={x}>{x}</option>
            }
            <option value="ALL-SAR">ALL-SAR</option>
            <option value="ALL-LOGIN">ALL-LOGIN</option>
           </select>
        ; 
        
        
        /* Fields to be filled in */
        def fields(req : Request): NodeSeq =
            label("msgto", "lleftlab", I.tr("To")+":", "") ++
            userList("msgto") ++ br ++
            label("msgcontent", "lleftlab", I.tr("Message")+":", "") ++
            textArea("msgcontent", 240, "");
            ;
            
            
        /* Action. To be executed when user hits 'submit' button */
        def action(req : Request): NodeSeq = 
        {
            val to = req.queryParams("msgto")
            val txt = req.queryParams("msgcontent")
            mbox.postMessage(getAuthInfo(req).userid, to, txt)
            
            <h2>{I.tr("Message posted")}</h2>        
        }
         
            
        return printHtml (res, htmlBody (req, null, htmlForm(req, prefix, fields, IF_AUTH(action))))
    }
   
   
       
       
   
  /** 
    * Search in the database over point objects. 
    * Returns a list (standard HTML)
    */
   def handle_search(req : Request, res : Response): String = 
      return _handle_search(req, res, false)
      ;
      
   def handle_search_sec(req : Request, res : Response): String = 
      return _handle_search(req, res, true)
      ;
    
   def _handle_search(req : Request, res : Response, loggedIn: Boolean): String =
   {
       val I = getI18n(req)
       val filtid = req.queryParams("filter")
       val tags = req.queryParams("tags")
       
       var arg = req.queryParams("srch")
       var mob = req.queryParams("mobile");
       if (arg == null) 
           arg  = "__NOCALL__"; 
       val infra = "infra".equals(arg)
       val tagList = if (tags==null || tags.equals("")) null else tags.split(",")
      
       val result = itemList(I, _api.getDB().search(arg, tagList), "true".equals(mob), fprefix(req), loggedIn )
       return printHtml (res, htmlBody (req, null, result))
   }
 


   /** 
    * View one particular point object. 
    */
   def handle_station(req : Request, res : Response): String  = 
       return _handle_station(req, res, false)
       ;
   
   
   def handle_station_sec(req : Request, res : Response): String  = 
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
  

   
   
   def handle_tags(req: Request, res: Response): String = {
       val args = req.queryParams("tags")
       val tags = if (args == null) null else args.split(",")
       return printHtml (res, htmlBody (req, null, taglist(null, tags, false)))
   }
   
   
      
   /** 
    * Presents a list over last positions and movements (standard HTML)
    */
   def handle_history(req : Request, res : Response): String =
   {        
       val I = getI18n(req)
       val s = _api.getDB().getItem(req.queryParams("id"), null)
       val result: NodeSeq =
          if (s == null)
             <h2>{I.tr("Error")+":"}</h2><p> {I.tr("Couldn't find item")} </p>;
          else
             <table>
               <tr><th>{I.tr("Time")} </th><th>Km/h </th><th> {I.tr("Dir")} </th><th> {I.tr("Distance")} </th><th> {I.tr("APRS via")} </th></tr>
               {
                   val h = s.getTrail()
                   var x = s.getHItem()
                   var i = 0;
                   var fp = _wfiledir /* FIXME FIXME fprefix(req) */
                   
                   for (it <- h.points()) yield {
                      i += 1
                      val arg = "'"+s.getIdent()+"', "+i
                      <tr onmouseover={"histList_hover("+arg+");"} 
                          onmouseout={"histList_hout("+arg+");"}
                          onclick = {"histList_click("+arg+");"} >
                      <td> { ServerBase.tf.format(x.getTS()) } </td>
                      <td> { if (x.speed >= 0) x.speed.toString else "" } </td>
                      <td> { if (x.speed > 0) _directionIcon(x.course, fp) else ""} </td>
                      <td> {
                         val dist = x.getPosition().toLatLng().distance(it.getPosition().toLatLng())
                         x = it.asInstanceOf[Trail.Item];
                         if (dist < 1)
                             "%3d m" format Math.round(dist*1000)
                         else
                             "%.1f km" format dist
                      }</td>
                      <td> { 
                        TXT( cleanPath(x.pathinfo)) } </td>
                      </tr>
                   }
               }
             </table>

        return printHtml(res, htmlBody(req, null, result))
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
