 
import java.util._
import java.io._
import scala.xml._
import scala.collection.JavaConversions._
import no.polaric.aprsd._
import spark.Request;
import spark.Response;




package no.polaric.aprsd.http
{

  
  class ConfigService
      ( val api: ServerAPI ) extends ServerBase(api) with ConfigUtils
  {
    
      /**
       * Handle restarting of the server by calling external script. 
       * Note that this must use sudo to operate with root privileges. 
       */
      def handle_restartServer(req : Request, res: Response) =
      {
          def action(req : Request): NodeSeq = {
             val pb = new ProcessBuilder("/usr/bin/sudo", "-n", "/usr/bin/polaric-restart")
             pb.inheritIO();
             pb.start(); 
             
             <br/>
             <h2>{ "Restart server..." }</h2>
             <span>{ "You may have to reload/close window and log in again" }</span>
          }
             
          printHtml (res, htmlBody (req, null, IF_ADMIN(action)(req) ))
      }
      
         
          
      
      /** 
       * Override htmlBody, in order to add a menu/status on the left side. 
       */
      override def htmlBody(req: Request, xhead : NodeSeq, content : NodeSeq) : Node =
      {
           val heads = xhead
           
           var selected = req.queryParams("mid")           
           selected = if (selected==null) "1" else selected
           var lang = req.queryParams("lang")
           lang = if (lang==null) "en" else lang
           val inapp = if (req.queryParams("inapp") != null) "&inapp=true" else ""
           
           
           def mitem(url:String, id: Integer, txt: String) : Node = 
           {
              val delim = if (url.contains("?")) "&" else "?"
              val cls = if ((""+id).equals(selected)) "selected" else ""
              
              <li class={cls}>
                <a href={ url + delim + "lang="+lang+"&mid="+id+inapp }>{txt}</a>
              </li>
           }
           
           
           
           def body = 
              <div id="config_menu">
              <ul class="menu">
               { mitem("config_menu", 1, "Status info") }
                 <ul>
                  { mitem("config_clients", 2, "Client list")
                  }
                 </ul>
               {
                 mitem("config", 4, "Server config") ++
                 mitem("config_posreport", 5, "Own position") ++
                 mitem("config_mapdisplay", 6,"Display on map")
               }
              <li>{ "Data channels..." }</li>
              <ul>
               {
                  val chs = _api.getProperty("channels", null).split(",(\\s)*")
                  var mid=6
                  for (ch <- chs) yield {
                     mid += 1;
                     mitem("config_chan?chan="+ch, mid, ch)
                  }
               }
               </ul>
             </ul>
             <br/>
             <button id="logout" onclick={"location.href='logout?url=config_menu'"} >Logout</button>
             <button id="restart" onclick={"location.href='restartServer?lang="+lang+"'"} >RESTART</button>

             {
                if (changed)
                   <div class="status">
                      { "Changes are done. Restart to activate." }
                   </div>
                else null
             }
             
             </div>
             <div id="config_main">
                { content }
             </div>
             ;
             
             super.htmlBody(req, heads, body) 
      }
      
 
      /**
       * This is the first menu choice. It just puts the admin status from aprsd in a iframe 
       */
      def handle_config_menu(req : Request, res: Response) =
      {
          var lang = req.queryParams("lang")
          lang = if (lang==null) "en" else lang
          
          def action(req : Request): NodeSeq =
             <iframe id="config_main" name="config_main" src={"admin?cmd=info&lang="+lang} />
             ;   
                          
          printHtml (res, htmlBody(req, null, action(req)))
      }
    
    
    
     /**
       * The second menu choice: List of active clients. 
       */
      def handle_config_clients(req : Request, res: Response) =
      {
          def action(req : Request): NodeSeq =
             <iframe id="config_main" name="config_main" src={"listclients"} />
             ;   
          printHtml (res, htmlBody(req, null, action(req)))
      }
      
      
     /**
       * The third menu choice: List of users of the system. 
       */
      def handle_config_users(req : Request, res: Response) =
      {
          def action(req : Request): NodeSeq =
             <iframe id="config_main" name="config_main" src={"listusers"} />
             ;   
          printHtml (res, htmlBody(req, null, action(req)))
      }
      
      
      
      /**
       * The main configuration of aprsd. Callsign, users, channels. Igate. Remote control.
       */
      def handle_config(req : Request, res: Response) =
      {            
          val prefix = <h3>{"Configuration of Polaric APRSD"}</h3>
          
          def fields(req : Request): NodeSeq =
                textField("default.mycall", "item1", 
                      "Callsign:", "", 10, 10, CALLSIGN) ++
                br ++
                textField("channels", "item4", 
                      "Data channels"+":", 
                      "Data channels for tracking", 30, 50, LIST, "(list)")   ++
                textField("channel.default.inet", "item5", 
                      "Primary APRS/IS channel"+":", "", 10, 10, NAME) ++ 
                textField("channel.default.rf", "item6", 
                      "Primary RF channel:", "", 10, 10, NAME) ++ 
                br ++
                label("item7", "leftlab", 
                      "Igate:", 
                      "Tick to activate RF<->internet gateway") ++
                boolField("igate.on", "item7", 
                      "Activated.") ++ br ++ 
                label("item8", "leftlab", 
                      "Igating to RF:", 
                      "Tick to activate internet->RF igating") ++
                boolField("igate.rfgate.allow", "item8", 
                      "Activated.") ++
                boolField("objects.rfgate.allow", "item9", 
                      "RF igating for objects.") ++ br ++
                textField("objects.rfgate.range", "item10", 
                      "Radius objects:", 
                      "Area for sending of objects RF", 6, 10, NUMBER, "(km)") ++
                textField("igate.rfgate.path", "item11", 
                      "Digipeater path, igate:", 
                      "Default (see also next field)", 20, 30, LIST) ++
                textField("message.rfpath", "item12", 
                      "Digi path, messages:", 
                      "...has also effect for messages to RF igate", 20, 30, LIST) ++
                textField("objects.rfpath", "item13", 
                      "Digipeater path, objects:", 
                      "...has also effect for objects to RF igate", 20, 30, LIST) ++
                textField("message.alwaysRf", "item13.2", 
                      "Always send on RF:",
                      "messages with dest that matches this will be sent on RF", 20, 30, TEXT, "(regex)") ++
                br ++ 
                label("item14", "leftlab", 
                      "Remote control:", 
                      "Tick to activate remote control") ++
                boolField("remotectl.on", "item14", 
                      "Activated.") ++
                br ++
                textField("remotectl.radius", "item14.2", 
                      "Radius of interest:", 
                      "Radius in which we want to receive item-updates", 6, 10, NUMBER, "(km)") ++
                textField("remotectl.connect", "item15", 
                      "Rc server:", 
                      "Rc server (callsign of another PS instance)", 10, 10, NAME) ++
                textField("message.auth.key", "item16", 
                      "Authentication key:", 
                      "Key for authentication (for remote control)", 20, 30, TEXT)
               ;
              
              
         def action(req : Request): NodeSeq = 
         {
               br ++ br ++
               getField(req, "item1", "default.mycall", CALLSIGN) ++ 
               getField(req, "item2", "user.admin", TEXT) ++ 
               getField(req, "item3", "user.update", TEXT) ++ 
               getField(req, "item4", "channels", LIST) ++ 
               getField(req, "item5", "channel.default.inet", NAME) ++ 
               getField(req, "item6", "channel.default.rf", NAME) ++ 
               getField(req, "item7", "igate.on", BOOLEAN) ++
               getField(req, "item8", "igate.rfgate.allow", BOOLEAN) ++ 
               getField(req, "item9", "objects.rfgate.allow", BOOLEAN) ++ 
               getField(req, "item10", "objects.rfgate.range", 0, 99999) ++ 
               getField(req, "item11", "igate.rfgate.path", LIST) ++ 
               getField(req, "item12", "message.rfpath", LIST) ++
               getField(req, "item13", "objects.rfpath", LIST) ++
               getField(req, "item13.2", "message.alwaysRf", TEXT) ++
               getField(req, "item14", "remotectl.on", BOOLEAN) ++
               getField(req, "item14.2", "remotectl.radius", NUMBER) ++
               getField(req, "item15", "remotectl.connect", NAME) ++
               getField(req, "item16", "message.auth.key", TEXT) 
         }

         
         
         printHtml (res, htmlBody (req, null, htmlFormJump(req, prefix, 
             IF_ADMIN(fields), IF_ADMIN(action), false, default_submit)))
     }
     
     
      /**
       * Parameters related to showing tracks on map. 
       */
      def handle_config_mapdisplay(req : Request, res: Response) =
      {          
          val prefix = <h3>{ "Map display settings" }</h3>
          
          def fields(req : Request): NodeSeq =
                textField("aprs.expiretime", "item1", 
                     "Max inactivity:", 
                     "How long can an object be inactive before it disappears", 
                     4, 4, NUMBER, "(minutes)") ++
                br ++
                textField("map.trail.maxPause", "item2", 
                     "Max inactivity for trail:", 
                     "How long can an object be inactive before its trail disappears", 
                     4, 4, NUMBER, "(minutes)") ++        
                br ++
                textField("map.trail.maxAge", "item4", 
                     "Trail length:", 
                     "How long timespan to draw a trail for", 
                     4, 4, NUMBER, "(minutes)") 
         ;
              
              
          def action(req : Request): NodeSeq = 
          {
               br ++ br ++
               getField(req, "item1", "aprs.expiretime", 0, 1440) ++ 
               getField(req, "item2", "map.trail.maxPause", 0, 1440) ++
               getField(req, "item4", "map.trail.maxAge", 0, 1440) 
          }
              
          printHtml (res, htmlBody (req, null, htmlFormJump(req, prefix, 
             IF_ADMIN(fields), IF_ADMIN(action), false, default_submit)))
      }     
      
      
      
      
      /**
       * Using the Polaric APRSD as a tracker that send position reports. 
       */
      def handle_config_posreport(req : Request, res: Response) =
      {   
          val prefix = <h3>{ "Tracking of own position" }</h3>
          
          def fields(req : Request): NodeSeq =
                label("item1", "leftlab", 
                      "Position report:", 
                      "Tick to activate position reporting") ++
                boolField("ownposition.tx.on", "item1", 
                      "Activated") ++  
                boolField("ownposition.tx.allowrf", "item2", 
                      "Allow transmission on RF") ++
                br ++
                label("item3", "leftlab", "", "") ++
                boolField("ownposition.tx.compress", "item3", "Compress") ++
                br ++ br ++
                label("item4", "leftlab", 
                      "Symbol:", 
                      "APRS symbol-table and symbol") ++
                textInput("item4", 1, 1, ".", ""+_api.getProperty("ownposition.symbol", "/c")(0)) ++
                textInput("item5", 1, 1, ".", ""+_api.getProperty("ownposition.symbol", "/c")(1)) ++
                br ++
                textField("ownposition.tx.rfpath", "item6", 
                      "Digipeater path:", "", 20, 30, LIST) ++
                textField("ownposition.tx.comment", "item7", 
                      "Description"+":", "", 20, 40, TEXT) ++ br ++
                label("utmz", "leftlab", 
                      "Default position:", 
                      "Server's position in UTM format") ++        
                utmField("ownposition.pos") ++ br ++ 
                br ++
                label("item8", "leftlab", "Tracking with GPS"+":", 
                      "Tick to use position from GPS") ++
                boolField("ownposition.gps.on", "item8", 
                      "Activated") ++ 
                boolField("ownposition.gps.adjustclock", "item9", 
                      "Adjust clock from GPS") ++ br ++
                textField("ownposition.gps.port", "item10", 
                      "GPS Port:", 
                      "Serial port device-name (e.g. /dev/ttyS0)", 12, 20, NAME) ++
                textField("ownposition.gps.baud", "item11", 
                      "GPS Baud:", "", 6, 8, NUMBER) ++ br ++
                textField("ownposition.tx.minpause", "item12", 
                      "Min pause:", 
                      "Minimum time between transmissions", 4, 5, NUMBER, "(seconds)") ++
                textField("ownposition.tx.maxpause", "item13", 
                      "Max pause:", 
                      "Maximum time between transmissions", 4, 5, NUMBER, "(seconds)") ++
                textField("ownposition.tx.mindist", "item14", 
                      "Min distance:", 
                      "Distance between transmissions when speed is low", 4, 5, NUMBER, "(meter)") ++
                textField("ownposition.tx.maxturn", "item15", 
                      "Max turn:", 
                      "Max change in direction before transmission", 4, 5, NUMBER, "(degrees)")
              ;
              
              
         def action(req : Request): NodeSeq = 
         {                 
              br ++ br ++
              getField(req, "item1", "ownposition.tx.on", BOOLEAN) ++
              getField(req, "item2", "ownposition.tx.allowrf", BOOLEAN) ++
              getField(req, "item3", "ownposition.tx.compress", BOOLEAN) ++
              getField(req, "item4", "item5", "ownposition.symbol", "..") ++ 
              getField(req, "item6", "ownposition.tx.rfpath", LIST) ++ 
              getField(req, "item7", "ownposition.tx.comment", TEXT) ++ 
              getUtmField(req, "utmz", "utmnz", "x", "y", "ownposition.pos", UTMPOS) ++
              getField(req, "item8", "ownposition.gps.on", BOOLEAN) ++
              getField(req, "item9", "ownposition.gps.adjustclock", BOOLEAN) ++
              getField(req, "item10", "ownposition.gps.port", NAME) ++
              getField(req, "item11", "ownposition.gps.baud", 300, 999999) ++
              getField(req, "item12", "ownposition.tx.minpause", 10, 60*60*60) ++
              getField(req, "item13", "ownposition.tx.maxpause", 20, 60*60*60) ++
              getField(req, "item14", "ownposition.tx.mindist", 10, 999999) ++
              getField(req, "item15", "ownposition.tx.maxturn", 0, 360) ++
              _action
         }
                       
         def _action: NodeSeq = 
         {
            changed=false
            _api.getOwnPos().init();
            <span></span>
         }
         printHtml (res, htmlBody (req, null, htmlFormJump(req, prefix, 
             IF_ADMIN(fields), IF_ADMIN(action), false, default_submit)))
     }
     
     
     /* Views of different channel subclasses. Plugins may add more.. */
     ChannelView.addView(classOf[InetChannel],     classOf[AprsChannelView])
     ChannelView.addView(classOf[Tnc2Channel],     classOf[AprsChannelView])
     ChannelView.addView(classOf[KissTncChannel],  classOf[AprsChannelView])
     ChannelView.addView(classOf[TcpKissChannel],  classOf[AprsChannelView])
     
     
     /**
      * Configuration of each individual channel.
      * A MVC pattern. 
      * Could this be a generic method? 
      */
     def handle_config_chan(req: Request, res: Response) = 
     {
         val cid = req.queryParams("chan")
         val prefix = <h3>{"Channel"+ " '"+cid+"'"}</h3>
         
          /* Get the channel in question */
         val ch = _api.getChanManager().get(cid).asInstanceOf[Channel]
           
          /* Get or create a view for the channel */
         val view = if (ch == null)
                         new ChannelView(_api, null, req);  
                    else ChannelView.getViewFor(ch, _api, req)
           
           
         def fields(req: Request): NodeSeq = {
            if (ch != null) 
               refreshPage(req, res, 60, "config_chan")
            view.fields(req)
         }
         def action(req: Request): NodeSeq = {
            view.action(req)
         }
         
         printHtml (res, htmlBody ( req, null, 
             htmlFormJump( req, prefix, IF_ADMIN(fields), IF_ADMIN(action), false, default_submit)))    
     }
     

     
    
     /**
      * Handle password change (to be placed in separate window). 
      */
      def handle_passwd(req: Request, res: Response) = 
      {
          val prefix = <h3>{ "Register user/password" }</h3>
          var username = getAuthInfo(req).userid
          
          
          
          def fields(req : Request): NodeSeq =
             label("item1", "lleftlab", "Username:", "Username for new or existing user") ++
             { if (getAuthInfo(req).admin)
                  textInput("item1", 20, 20, NAME, "")
               else 
                  <label id="item1">{username}</label>
             } ++
             br ++
             label("item2", "lleftlab", "Password:", "") ++
             textInput("item2", 20, 30, ".*", "")
          ;
          
          
          
          def action(req : Request): NodeSeq = 
          {
             username = if (getAuthInfo(req).admin) req.queryParams("item1") 
                        else username
             val passwd = req.queryParams("item2")             
             val cmd = "/usr/bin/sudo /usr/bin/htpasswd -b /etc/polaric-aprsd/passwd "+username+" "+passwd
             val p = Runtime.getRuntime().exec(cmd)
             val res = p.waitFor()
             
             if (res == 0) {
                 api.getWebserver().asInstanceOf[WebServer].getAuthConfig().reloadPasswds();
                 <h3>{ "Password updated for user"+": '"+username+"'" }</h3>
             }
             else if (res == 5)
                 <h3>{ "Error: Your input is too long" }</h3>
             else if (res == 6)
                 <h3>{ "Error: Your input contains illegal characters" }</h3>
             else 
                 <h3>{ "Error: Couldn't update (server problem)" }</h3>
       
          }
 
 
          printHtml (res, super.htmlBody (req, null, htmlFormJump(req, prefix, IF_AUTH(fields), IF_AUTH(action))))
      }
      
     
     
     
  }

}
