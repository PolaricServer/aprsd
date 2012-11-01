import java.util._
import java.io._
import uk.me.jstott.jcoord._
import scala.xml._
import scala.collection.jcl.Conversions._
import no.polaric.aprsd._
import org.simpleframework.http.core.Container
import org.simpleframework.transport.connect.Connection
import org.simpleframework.transport.connect.SocketConnection
import org.simpleframework.http._



   
package no.polaric.aprsd.http 
{

  class Webserver 
      ( val api: ServerAPI,
        val props : Properties) extends XmlServer(api,  props) with ServerUtils
  {


   val _time = new Date();

 

   /**
    * Exctract UTM reference from request parameters.
    *   x and y: coordinates relative to mapserver UTM zone
    *   utmz: UTM zone (optional)
    *   utmnz: UTM zone letter (optional)
    *   returns UTM reference. Null if not possible to construct a correct
    *   UTM reference from the given parameters.
    */
   private def getUtmCoord(req : Request, nzone: Char, zone: Int) : UTMRef =
   {
        val x = req.getParameter("x")
        val y = req.getParameter("y")
        var utmz = req.getParameter("utmz")
        var utmnz = req.getParameter("utmnz")
        try {
           if (x != null && y != null)
               new UTMRef( x.toDouble, y.toDouble,
                        if (utmnz==null) 'W' else utmnz(0),
                        if (utmz==null) _utmzone else utmz.toInt )  /* FIXME: Lat zone */
           else
               null
        }
        catch {
           case e: Exception => println("*** Warning: "+e); null }
   }


   
   /** 
    * Print reference as UTM reference. 
    */
   private def showUTM(ref: Reference) : NodeSeq =
      try {
         val sref = ref.toLatLng().toUTMRef().toString()
         <span class="utmref">
         {sref.substring(0,5)}<span class="kartref">{
           sref.substring(5,8)}</span>{sref.substring(8,13)}<span class="kartref">{
           sref.substring(13,16)}</span>{sref.substring(16)}
         </span>  
      }
      catch {
          case e:Exception => Text("(ugyldig posisjon)")
      }
   
   
   /**
    * HTML form elements (fields) for entering a UTM reference.
    */
   private def utmForm(nzone: Char, zone: Int) : NodeSeq =
       <input id="utmz" name="utmz" type="text" size="2" maxlength="2" value={zone.toString} />
       <input id="utmnz" name="utmnz" type="text" size="1" maxlength="1" value={nzone.toString} />
       <input id="utmx" name="x" type="text" size="6" maxlength="6"/>
       <input id="utmy" name="y" type="text" size="7" maxlength="7"/>;




   /** 
    * Admin interface. 
    * To be developed further...
    */
   def handle_admin(req : Request, res: Response) =
   {   
       val out = getWriter(res);
       val cmd = req.getParameter("cmd")
       val head = <meta http-equiv="refresh" content="60" />


       def action(req : Request): NodeSeq =
          if (!authorizedForAdmin(req))
              <h3>Du er ikke autorisert for admin operasjoner</h3>
          else if ("gc".equals(cmd)) {
              _api.getDB().garbageCollect()
              <h3>GC, ok</h3>
          }
          else if ("clearobj".equals(cmd)) {
              _api.getDB().getOwnObjects().clear()
              <h3>Clear own objects, ok</h3>
          }
          else if ("clearlinks".equals(cmd)) {
              _api.getDB().getRoutes().clear()
              <h3>Clear link information, ok</h3>    
          }
          else if ("info".equals(cmd))    
              <h3>Status info for Polaric APRSD</h3>
              <fieldset>
              { simpleLabel("items", "leftlab", "Server kjørt siden:", TXT(""+_time)) }
              { simpleLabel("items", "leftlab", "Server versjon:", TXT(""+_api.getVersion())) }
              { simpleLabel("items", "leftlab", "Antall APRS enheter:", TXT(""+_api.getDB().nItems())) }
              { simpleLabel("items", "leftlab", "Antall forbindelser:", TXT(""+_api.getDB().getRoutes().nItems())) }
              { simpleLabel("items", "leftlab", "Egne objekter:", TXT(""+_api.getDB().getOwnObjects().nItems())) }   
              { simpleLabel("items", "leftlab", "Antall HTTP klienter nå:", TXT(""+(_api.getHttps().getClients()-1))) }  
              { simpleLabel("items", "leftlab", "Antall HTTP forespørsler:", TXT(""+(_api.getHttps().getReq()))) }    
              { simpleLabel("freemem", "leftlab", "Brukt minne:", 
                  TXT( Math.round(StationDBImp.usedMemory()/1000)+" KBytes")) }   
                            
              {  var i=0;
                 for (x <- PluginManager.getPlugins()) yield {
                    i+=1;
                    simpleLabel("", "leftlab", if (i<=1) "Plugin modul: " else "", 
                         TXT(x.getDescr())); 
                 }
              }    
              <br/>
         
              { var i = 0; 
                for ( x:String <- _api.getChanManager().getKeys()) yield
                 {  val ch = api.getChanManager().get(x); 
                    i += 1;
                    simpleLabel("chan_"+ch.getIdent(), 
                     "leftlab", "Kanal '"+ch.getIdent()+"' ("+ch.getShortDescr()+"):", TXT(ch.toString())) } 
              }

              
              { simpleLabel("igate", "leftlab", "Igate: ", 
                   if (_api.getIgate()==null) TXT("---") else TXT(""+_api.getIgate())) }   
              { if (_api.getRemoteCtl() != null && !_api.getRemoteCtl().isEmpty())  
                   simpleLabel("rctl", "leftlab", "Fjernkontroll: ", TXT(""+_api.getRemoteCtl())) else null; }     
                   
              </fieldset>  
              <input type="submit" onclick="window.close()" id="cancel" value="Avbryt"/>
          else
              <h3>Ukjent kommando</h3>
             
        printHtml (res, htmlBody(req, head, action(req)));    
   }
   
   
   
   
    /**
    * set own position
    */          
   def handle_setownpos(req : Request, res: Response, out: PrintWriter) =
   {
        val pos = getUtmCoord(req, 'W', _utmzone)
        val prefix = <h2>Legge inn egen posisjon</h2>
        val p = api.getOwnPos()
       
        /* Fields to be filled in */
        def fields(req : Request): NodeSeq =
           <xml:group>
          
            <label for="objsym" class="lleftlab">Symbol:</label>
            <input id="osymtab" name="osymtab" type="text" size="1" maxlength="1" value={ ""+p.getSymtab() }/>
            <input id="osym" name="osym" type="text" size="1" maxlength="1" value={ ""+p.getSymbol() } />
            <br/>      
            <label for="utmz" class="lleftlab">Pos (UTM): </label>
            {  if (pos==null)
                  utmForm('W', 34)
               else
                  showUTM(pos)
            }
            </xml:group>
             
             
        /* Action. To be executed when user hits 'submit' button */
        def action(req : Request): NodeSeq = {
               if (!authorizedForAdmin(req))
                  <h3>Du er ikke autorisert for admin operasjoner</h3>
               else {
                  val osymtab = req.getParameter("osymtab")
                  val osym  = req.getParameter("osym")
                  System.out.println("*** SET OWN POS by user '"+getAuthUser(req)+"'")
                  p.updatePosition(new Date(), pos, 
                      if (osymtab==null) '/' else osymtab(0), if (osym==null) 'c' else osym(0))
                  
                 <h2>Posisjon registrert</h2>
                 <p>pos={showUTM(pos) }</p>
              }
           };
            
        printHtml (res, htmlBody (req, null, htmlForm(req, prefix, fields, IF_AUTH(action))))
    }   
     
   
   
   
   
   def handle_sarmode(req : Request, res: Response) =
   {
       val prefix = <h2>Søk og redningsmodus</h2>
       var filter = req.getParameter("sar_prefix")
       var reason = req.getParameter("sar_reason")
       val on = req.getParameter("sar_on")
       
       def fields(req : Request): NodeSeq =          
           <xml:group>
           <p>Alias-info o.l. bare synlig for innloggete brukere.</p>
           <label for="sar_on" class="lleftlab">SAR modus:</label>
           { checkBox("sar_on", api.getSar() !=null, TXT("aktivert")) }  
           <br/>
           
           <label for="sar_prefix" class="lleftlab">Skjul prefiks:</label>
           <input id="sar_prefix" name="sar_prefix" width="50" value= 
             { if ( api.getSar()==null) "" else api.getSar().getFilter() }></input>
           <br/>
           
           <label for="sar_reason" class="lleftlab">Beskrivelse:</label>
           { if (api.getSar() == null)
                <input id="sar_reason" name="sar_reason" width="50" value={""} ></input>
             else 
                <xml:group>
                <label id="sar_reason">{ api.getSar().getReason() }
                <em> { "("+api.getSar().getUser()+")" } </em></label>
                <br/>
                { simpleLabel("sar_date", "lleftlab", "Aktivert:", TXT(""+api.getSar().getTime())) }
                </xml:group>
           }
           </xml:group>     
              
              
       def action(req : Request): NodeSeq = 
       {
          AprsPoint.abortWaiters(true);
          if (on != null && "true".equals(on) ) {
               val filt = if ("".equals(filter)) "NONE" else filter;
               api.setSar(reason, getAuthUser(req), filter);
               if (api.getRemoteCtl() != null)
                   api.getRemoteCtl().sendRequestAll("SAR "+getAuthUser(req)+" "+filt+" "+reason, null);
               
               <h3>Aktivert</h3>
               <p>{reason}</p>
          }
          else {   
               api.clearSar();
               if (api.getRemoteCtl() != null)
                  api.getRemoteCtl().sendRequestAll("SAR OFF", null);
               <h3>Avsluttet</h3>
          } 
       }
            
       
       printHtml (res, htmlBody (req, null, htmlForm(req, prefix, fields, IF_AUTH(action) )))
   }
   
   
   
   
   def handle_sarurl(req : Request, res: Response) =
   {
       val url = req.getParameter("url")
       
       def action(req : Request): NodeSeq = 
       {
          if (!authorizedForUpdate(req) && api.getSarUrl() != null )  
              <h3>SAR URL ikke tilgjengelig eller du er ikke autorisert</h3>
          else {
              val sarurl = api.getSarUrl().create(url)
              <h1>Kort-URL for søk og redning</h1>
              <h2><a class="sarurl" href={sarurl}>{sarurl}</a></h2>
              <h1>Nøkkel:</h1>
              <h2 class="sarurl">{SarUrl.getKey(sarurl)}</h2>
              <p>Gyldig i 1 døgn fra nå</p>
          }
       }   
       printHtml (res, htmlBody(req, null, action(req)));        
   }
   
   
   
   /**
    * Delete APRS object.
    */          

   def handle_deleteobject(req : Request, res: Response) =
   {
       val id = req.getParameter("objid").replaceFirst("@.*", "")
       val prefix = <h2>Slett objekt</h2>
       
       def fields(req : Request): NodeSeq =
           <label for="objid" class="lleftlab">Objekt ID:</label>
           <input id="objid" name="objid" type="text" size="9" maxlength="9"
              value={if (id==null) "" else id.replaceFirst("@.*", "")} />;
      
      
       def action(req : Request): NodeSeq =
          if (id == null) {
              <h3>Feil:</h3>
              <p>må oppgi 'objid' som parameter</p>;
          }
          else {
              if (_api.getDB().getOwnObjects().delete(id)) {
                  System.out.println("*** DELETE OBJECT: '"+id+"' by user '"+getAuthUser(req)+"'")
                  <h3>Objekt slettet!</h3>
              }
              else
                  <h3>Fant ikke objekt: {id}</h3>
          }  
          
       printHtml (res, htmlBody (req, null, htmlForm(req, prefix, fields, IF_AUTH(action) )))
   }          




   def handle_resetinfo(req : Request, res : Response) =
   {
       val id = req.getParameter("objid")
       val prefix = <h2>Nullstill info om stasjon/objekt</h2>
       
       def fields(req : Request): NodeSeq =
           <label for="objid" class="lleftlab">Objekt ID:</label>
           <input id="objid" name="objid" type="text" size="9" maxlength="9"
              value={if (id==null) "" else id} />;
      
      
       def action(req : Request): NodeSeq =
          if (id == null) {
              <h3>Feil:</h3>
              <p>må oppgi 'objid' som parameter</p>;
          }
          else {
             val x = _api.getDB().getItem(id, null);
             if (x != null)
                x.reset();
             <h3>Info om objekt nullstilt!</h3>
          }  
       printHtml (res, htmlBody (req, null, htmlForm(req, prefix, fields, IF_AUTH(action))))
   }          



             
   /**
    * add or edit APRS object.
    */          
   def handle_addobject(req : Request, res : Response) =
   {
        val pos = getUtmCoord(req, 'W', _utmzone)
        val id = req.getParameter("objid")
        val prefix = <h2>Legge inn objekt</h2>
        
        /* Fields to be filled in */
        def fields(req : Request): NodeSeq =
           <xml:group>
            <label for="objid" class="lleftlab">Objekt ID:</label>
            <input id="objid" name="objid" type="text" size="9" maxlength="9"/>
            <br/>
            <label for="objsym" class="lleftlab">Symbol:</label>
            <input id="osymtab" name="osymtab" type="text" size="1" maxlength="1" value="/" />
            <input id="osym" name="osym" type="text" size="1" maxlength="1" value="c" />
            <br/>      
            <label for="descr" class="lleftlab">Beskrivelse:</label>
            <input id="descr" name="descr" type="text" size="30" maxlength="40"/>
            <br/>
            <label for="utmz" class="lleftlab">Pos (UTM): </label>
            {  if (pos==null)
                  utmForm('W', 34)
               else
                  showUTM(pos)
            }
            <br/>
            <div>
            <label for="perm" class="lleftlab">Innstillinger: </label>
            {checkBox("perm", false, TXT("Tidløs (Permanent)"))}
            </div>
            </xml:group>
             
             
        /* Action. To be executed when user hits 'submit' button */
        def action(request : Request): NodeSeq =
            if (id == null || !id.matches("[a-zA-Z0-9_].*\\w*")) {
               <h2>Feil</h2>
               <p>må oppgi 'objid' som parameter og denne må begynne med bokstav/tall</p>;
            }
            else {
               val osymtab = req.getParameter("osymtab")
               val osym  = req.getParameter("osym")
               val otxt = req.getParameter("descr")
               val perm = req.getParameter("perm")
               System.out.println("*** SET OBJECT: '"+id+"' by user '"+getAuthUser(request)+"'")
               if ( _api.getDB().getOwnObjects().add(id, 
                      new AprsHandler.PosData( pos,
                        if (osymtab==null) '/' else osymtab(0),
                        if (osym==null) 'c' else osym(0)) ,
                      if (otxt==null) "" else otxt,
                      "true".equals(perm) ))
                  
                  <h2>Objekt registrert</h2>
                  <p>ident={"'"+id+"'"}<br/>pos={showUTM(pos) }</p>;
               else
                  <h2>Kan ikke registreres</h2>
                  <p>Objekt '{id}' er allerede registrert av noen andre</p>
           };
            
        printHtml (res, htmlBody (req, null, htmlForm(req, prefix, fields, IF_AUTH(action))))
    }


   def _directionIcon(direction:Int, fprefix: String): NodeSeq = 
        direction match {
          case x if !(22 until 337 contains x) =>
              <div><img src= {fprefix+"/dicons/dN.png"}/> N</div>
          case x if (22 until 67 contains x) =>
              <div><img src= {fprefix+"/dicons/dNE.png"}/> NE</div>
          case x if (67 until 112 contains x) =>
              <div><img src= {fprefix+"/dicons/dE.png"}/> E</div>
          case x if (112 until 157 contains x) =>
              <div><img src= {fprefix+"/dicons/dSE.png"}/> SE</div>
          case x if (157 until 202 contains x) =>
              <div><img src= {fprefix+"/dicons/dS.png"}/> S</div>
          case x if (202 until 247 contains x) =>
              <div><img src= {fprefix+"/dicons/dSW.png"}/> SW</div>
          case x if (247 until 292 contains x) =>
              <div><img src= {fprefix+"/dicons/dW.png"}/> W</div>
          case x if (292 until 337 contains x) =>
              <div><img src= {fprefix+"/dicons/dNW.png"}/> NW</div>
          case _ => null;
      }


   
  /** 
    * Presents a status list over registered stations (standard HTML)
    */
   
   def handle_search(req : Request, res : Response) =
   {
       val filtid = if (_infraonly) "infra"  else req.getParameter("filter")
       val vfilt = ViewFilter.getFilter(filtid)
       
       var arg = req.getParameter("filter")
       var mob = req.getParameter("mobile");
       if (arg == null) 
           arg  = "__NOCALL__"; 
       val infra = _infraonly || "infra".equals(arg)
       val result: NodeSeq =
         <table>
         {
            for ( x:AprsPoint <- _api.getDB().getAll(arg)
                  if ( vfilt.useObject(x)) ) yield
            {
               val s = if (!x.isInstanceOf[Station]) null
                       else x.asInstanceOf[Station];

               val moving = if (s!=null) !s.getTrail().isEmpty()
                            else false;

               <tr onclick={
                 if (x.visible() && x.getPosition() != null) 
                     "findStation('" + x.getIdent() + "', 'true')"
                 else "" 
               }>
          
               <td>{x.getIdent()}</td>
               <td> 
               { if (moving && s.getSpeed() > 0)
                       _directionIcon(s.getCourse(), fprefix(req)) else 
                          if (s==null) TXT("obj") else null } </td>
               <td> { df.format(x.getUpdated()) } </td>
               { if (!"true".equals(mob)) 
                  <td> { if (x.getDescr() != null) x.getDescr() else "" } </td> 
                 else null }
               </tr>
           }
        } 
        </table>;
        printHtml (res, htmlBody (req, null, result))
   }
 



   /**
    * Selection of icon. List available icons. 
    */
   def iconSelect(s: AprsPoint, fprefix: String): NodeSeq =
   {
       val webdir = System.getProperties().getProperty("webdir", ".")
       val icondir = new File(webdir+"/icons")
      
       val flt = new FilenameFilter()
           { def accept(dir:File, f: String): boolean = f.matches(".*\\.(png|gif|jpg)") } 
       val cmp = new Comparator[File] ()
           { def compare (f1: File, f2: File) : int = f1.getName().compareTo(f2.getName()) } 
       
       val files = icondir.listFiles(flt);
       <div id="iconselect">    
       <input type="radio" name="iconselect" value="system"
                   checked={if (s.iconIsNull()) "checked" else null:String }>Automatisk</input>
       { if (files != null) {
           Arrays.sort(files, cmp);
           for (f:File <- files) yield
              <input type="radio" name="iconselect" value={f.getName()}
                  checked={if (!s.iconIsNull() && f.getName().equals(s.getIcon())) "checked"
                           else null:String}>
              <img src={fprefix+"/icons/"+f.getName()} width="22" height="22" />&nbsp;
              </input>
         }
         else null
       }
       </div>
    }
   


   def handle_station(req : Request, res : Response)  = 
       _handle_station(req, res, false)
       
   def handle_station_sec(req : Request, res : Response)  = 
       _handle_station(req, res, authorizedForUpdate(req))
  
  
   /** 
    * Info about station/object (standard HTML)
    */
   def _handle_station(req : Request, res : Response, canUpdate: boolean) =
   {        
        val id = req.getParameter("id")
        val x = _api.getDB().getItem(id, null)
        val s = if (x.isInstanceOf[Station]) x.asInstanceOf[Station] else null
        val obj = if (x.isInstanceOf[AprsObject]) x.asInstanceOf[AprsObject] else null
        val edit  =  ( req.getParameter("edit") != null )
        val simple =  ( req.getParameter("simple") != null )
        val prefix = null
        val pathinfo = if (s != null && s.getPathInfo() != null && 
                           s.getPathInfo().length() > 1) 
                               cleanPath(s.getPathInfo()) else null
                         
        if (obj != null)
            obj.update();


        def itemList(items : Set[String]) = 
             <div class="trblock">
             {  
                var i=0
                if (items != null) 
                 for (it <- items) yield {
                    i += 1
                    val xx = _api.getDB().getItem(it, null)
                    val linkable = (xx != null  && xx.visible() && xx.getPosition() != null)
                    <span class={ if (linkable) "link_id" else ""} onclick={
                        if (linkable) 
                           "findStation('" + xx.getIdent() + "', true)"
                        else "" }> {it + " " } </span>
                }
                else null;
             }
             </div>
        
        
        
        /* Fields to be filled in */
        def fields(req : Request): NodeSeq =
            <xml:group>  
            <label for="callsign" class="leftlab">Ident:</label>
            <label id="callsign"><b> { x.getIdent().replaceFirst("@.*","") } </b></label>

            { if (!simple)
                 simpleLabel("symbol", "leftlab", "Symbol:",TXT( x.getSymtab()+" "+x.getSymbol())) else null }
            { if (x.getAlias() != null)
                 simpleLabel("alias", "leftlab", "Alias:", <b>{x.getAlias()}</b>) else null }
            { if (obj != null)
                 simpleLabel("owner", "leftlab", "Avsender:", <b>{obj.getOwner().getIdent()}</b>) else null}
            { if (x.getDescr() != null && x.getDescr().length() > 0)
                 simpleLabel("descr", "leftlab", "Beskrivelse:", TXT(x.getDescr())) else null}
            { if (s != null && s.getStatus() != null)
                 simpleLabel("status", "leftlab", "Status:", "Sist mottatte APRS statusmelding",
                      TXT ( s.getStatus().text + " [" + df.format(s.getStatus().time)+"]"))  else null}
      
            { simpleLabel("pos", "leftlab", "Posisjon (UTM):",
                if (x.getPosition() != null) showUTM(x.getPosition())
                else TXT("ikke registrert") ) }
 
            { simpleLabel("posll", "leftlab", "Position (latlong):",
                if (x.getPosition() != null) TXT( ll2dmString( x.getPosition().toLatLng()))
                else TXT("ikke registrert") ) }
                
            { if (x != null && x.getAmbiguity() > 0)
                  simpleLabel("ambg", "leftlab", "Unøyaktighet:", 
                    TXT( "± "+0.01 * Math.pow(10, x.getAmbiguity())/2 + " minutter" )) else null}
            
            { if (s != null && s.getAltitude() >= 0)
                  simpleLabel("altitude", "leftlab", "Høyde (o/h):", TXT(s.getAltitude() + " m ")) else null }
            { if (s != null && s.getSpeed() > 0)
                  simpleLabel("cspeed", "leftlab", "Bevegelse:", _directionIcon(s.getCourse(), fprefix(req))) else null }

            { simpleLabel("hrd", "leftlab", "Sist rapportert:", "Tidspunkt for siste mottatte APRS rapport",
                  TXT( df.format(x.getUpdated()))) }
                  
            { if (pathinfo != null) simpleLabel("via", "leftlab", "Via:", "Hvilken rute siste APRS rapport har tatt", 
                     TXT(pathinfo)) else null }
                  
            { var txt = "";
              if (s != null) {
                 if (s.isIgate()) txt += "IGATE "; 
                 if (s.isWideDigi()) txt += "Wide-Digi";
                 if ((s.isIgate() || s.isWideDigi()) && simple)
                   { simpleLabel("infra", "leftlab", "Infrastruktur:", TXT(txt)) } else null 
              } else null
            }
            
            { if (simple)        
               <div id="traffic">
                  { if (s != null && s.isInfra() )
                     {  <label for="hrds" class="leftlab">Trafikk fra:</label>
                        <label id="hrds"> { itemList(s.getTrafficFrom()) } </label> } else null }
               
                  { if (s != null && s.getTrafficTo != null && !s.getTrafficTo.isEmpty)
                     {  <label for="hrd" class="leftlab">Trafikk til:</label>
                        <label id="hrd"> { itemList(s.getTrafficTo()) } </label> } else null }
               </div>
               else null                    
            }
            
           
                                 
            { if (edit && canUpdate)
                  <div>
                  <br/>
                  <label for="hidelabel" class="leftlab">Innstillinger:</label>
                  {checkBox("hidelabel", x.isLabelHidden(), TXT("Skjul ID"))}
                  {checkBox("pers", x.isPersistent(), TXT("Varig lagring"))}
                  <br/>
                  { if (s != null)
                       <xml:group>
                       <label for="newcolor" class="leftlab">Spor:</label>
                       { checkBox("newcolor", false, TXT("Finn ny Farge")) }
                       </xml:group> else null}
                  <br/>   
                  <label for="nalias" class="leftlab">Ny alias:</label>
                  <input id="nalias" name="nalias" width="10"
                         value={ if (x.getAlias()==null) "" else x.getAlias()} ></input>
                  <br/>
                  { iconSelect(x, fprefix(req)) }
                  </div>
               else null        
            }
                          
            </xml:group> ;



        /* Action. To be executed when user hits 'submit' button */
        def action(req : Request): NodeSeq =
        {
             val perm = req.getParameter("pers");
             x.setPersistent( "true".equals(perm) );  
             val hide = req.getParameter("hidelabel");
             x.setLabelHidden( "true".equals(hide) );     
             
             val newcolor = req.getParameter("newcolor");
             if (s != null && "true".equals(newcolor) )
                s.nextTrailColor();             


             /* Alias setting */
             var alias = req.getParameter("nalias");
             var ch = false;
             if (alias != null && alias.length() > 0)      
                 ch = x.setAlias(alias);
             else
                { ch = x.setAlias(null)
                  alias = "NULL"
                }
             System.out.println("*** ALIAS: '"+alias+"' for '"+x.getIdent()+"' by user '"+getAuthUser(req)+"'")
             if (ch && _api.getRemoteCtl() != null)
                 _api.getRemoteCtl().sendRequestAll("ALIAS "+x.getIdent()+" "+alias, null);

             /* Icon setting */
             var icon = req.getParameter("iconselect");
             if ("system".equals(icon)) 
                 icon = null; 
             if (x.setIcon(icon) && _api.getRemoteCtl() != null )
                 _api.getRemoteCtl().sendRequestAll("ICON "+x.getIdent() + " " +
                    { if (icon==null) "NULL" else icon }, 
                    null);
            
             <h3>Oppdatert</h3>
        }


        printHtml (res, htmlBody ( req, null, 
                                   if (simple) fields(req)
                                   else htmlForm(req, prefix, fields, IF_AUTH(action))))
    }
      


  
    private def cleanPath(txt:String): String = 
        txt.replaceAll("((WIDE|TRACE|SAR|NOR)[0-9]*(\\-[0-9]+)?\\*?),?|(qA.),?", "")
           .replaceAll("\\*", "").replaceAll(",", ", ")   


      
   /** 
    * Presents a list over last positions and movements (standard HTML)
    */
   def handle_history(req : Request, res : Response) =
   {        
       val s = _api.getDB().getStation(req.getParameter("id"), null)
       val result: NodeSeq =
          if (s == null)
             <h2>Feil:</h2><p>Fant ikke stasjon</p>;
          else
             <table>
               <tr><th>Tidspunkt</th><th>Km/h </th><th>Retn </th><th>Distanse</th><th>APRS via</th></tr>
               {
                   val h = s.getTrail()
                   var x = s.getHItem()
                   var i = 0;
                   var fp = _wfiledir /* FIXME FIXME fprefix(req) */
                   
                   for (it <- h.items()) yield {
                      i += 1
                      val arg = "'"+s.getIdent()+"', "+i
                      <tr onmouseover={"histList_hover("+arg+");"} 
                          onmouseout={"histList_hout("+arg+");"}
                          onclick = {"histList_click("+arg+");"} >
                      <td> { tf.format(x.time) } </td>
                      <td> { if (x.speed >= 0) x.speed.toString else "" } </td>
                      <td> { if (x.speed > 0) _directionIcon(x.course, fp) else ""} </td>
                      <td>{
                         val dist = x.getPosition().toLatLng().distance(it.getPosition().toLatLng())
                         x = it;
                         if (dist < 1)
                             "%3d m" format Math.round(dist*1000)
                         else
                             "%.1f km" format dist
                      }</td>
                      <td> { 
                        TXT( cleanPath(it.pathinfo)) } </td>
                      </tr>
                   }
               }
             </table>

        printHtml(res, htmlBody(req, null, result))
    } 
  
  
  

    def handle_trailpoint(req : Request, res : Response) =
    {
       val time = xf.parse(req.getParameter("time"))
       val ident = req.getParameter("id")
       val item = _api.getDB().getTrailPoint(ident, time)
       /* FIXME: Check if valid result */
       
       val result : NodeSeq = 
          if (item == null)
            <xml:group>
            TXT("Fant ikke info: "+ident)
            </xml:group>
          else
            <xml:group>
            <label for="callsign" class="lleftlab">Ident:</label>
            <label id="callsign"><b> { ident } </b></label>
            { simpleLabel("time",  "lleftlab", "Tid:", TXT( df.format(item.time))) }
            { simpleLabel("speed", "lleftlab", "Fart:", TXT(item.speed+" km/h") )  }
            { simpleLabel("dir",   "lleftlab", "Retning:", _directionIcon(item.course, fprefix(req)))  }
            <div id="traffic">
            { if (item.pathinfo != null) 
                 simpleLabel("via",   "lleftlab", "APRS via:", TXT( cleanPath(item.pathinfo)))
              else null }
            </div>
            </xml:group>
      
      printHtml(res, htmlBody(req, null, result)) 
    }
  
  }
}
