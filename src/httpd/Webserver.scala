import java.util._
import java.io._
import uk.me.jstott.jcoord._
import scala.xml._
import scala.collection.jcl.Conversions._
import no.polaric.aprsd._

   
package no.polaric.aprsd.http 
{


  class Webserver 
      ( val db: StationDB,
        val port : Int,
        val props : Properties) extends HttpServer(db,port,props)
  {


   val doctype = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN \">";
   val _time = new Date();

   

   private def htmlBody (css : boolean, head : NodeSeq, content : NodeSeq) : Node =
   {
        <html>
        <head>
        <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
        { head }
        { if (css) 
           <link href="style.css" rel="stylesheet" type="text/css" />  
          else null }
        </head>
        <body>
          {content} 
        </body>
        </html>
   }
   
   
   private def simpleLabel(id:String, cls:String, lbl:String, content: NodeSeq): NodeSeq =
           <label for={id} class={cls}>{lbl}</label>
           <label id={id}> {content}</label>
          ;
 
   private def TXT(t:String): NodeSeq = <xml:group>{t}</xml:group>
   
   
   private def checkBox(id:String, check: Boolean, t: NodeSeq): NodeSeq =
          <input type="checkbox" name={id} id={id}
             checked= {if (check) "checked" else null:String}
             value="true">
          {t}
          </input>


  /**
   * Modify a function to execute only if user is authorized for update. 
   * Runs original function if authorized. Return error message if not. 
   */
  private def IF_AUTH( func: (Properties, Properties) => NodeSeq ) 
                    : (Properties, Properties) => NodeSeq = 
  {
      def wrapper(hdr: Properties, parms: Properties) : NodeSeq =
        if (authorizedForUpdate(hdr))
           func(hdr, parms)
        else
           <h2>Du er ikke autorisert for denne operasjonen</h2>
      
      wrapper
  }


   /**
    * Generic HTML form method.
    * Field content and action to be performed are given as function-arguments
    */
   private def htmlForm( hdr: Properties, parms: Properties,
                         prefix : NodeSeq,
                         fields : (Properties, Properties) => NodeSeq,
                         action : (Properties, Properties) => NodeSeq ) : NodeSeq =
   {
        if (parms.getProperty("update") != null) 
           <div><script source="javascript">setTimeout('window.close()', 2000);</script>
           { action(hdr, parms) }
           </div>;
        
        else
           <form action="" method="post">
           { prefix }
           <fieldset>
              { fields(hdr, parms) }
           </fieldset>

           <input type="submit" onclick="window.close()" id="cancel" value="Avbryt"/>
           <input type="submit" name="update" id="update" value="Oppdater"/>
           </form>
   }


   private def printHtml(out : PrintWriter, content : Node ) : String =
   {
        out.println (doctype + Xhtml.toXhtml(content, true, true))
        out.flush()
        "text/html; charset=utf-8"
   }
   

   /**
    * Exctract UTM reference from request parameters.
    *   x and y: coordinates relative to mapserver UTM zone
    *   utmz: UTM zone (optional)
    *   utmnz: UTM zone letter (optional)
    *   returns UTM reference. Null if not possible to construct a correct
    *   UTM reference from the given parameters.
    */
   private def getUtmCoord(parms : Properties, nzone: Char, zone: Int) : UTMRef =
   {
        val x = parms.getProperty("x")
        val y = parms.getProperty("y")
        var utmz = parms.getProperty("utmz")
        var utmnz = parms.getProperty("utmnz")
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
   def _serveAdmin(hdr: Properties, parms: Properties, out: PrintWriter) : String =
   {   
       val cmd = parms.getProperty("cmd")
       val head = <meta http-equiv="refresh" content="60" />

       def action(hdr: Properties, parms: Properties): NodeSeq =
          if (!authorizedForAdmin(hdr))
              <h3>Du er ikke autorisert for admin operasjoner</h3>
          else if ("gc".equals(cmd)) {
              _db.garbageCollect()
              <h3>GC, ok</h3>
          }
          else if ("clearobj".equals(cmd)) {
              _db.getOwnObjects().clear()
              <h3>Clear own objects, ok</h3>
          }
          else if ("clearlinks".equals(cmd)) {
              _db.getRoutes().clear()
              <h3>Clear link information, ok</h3>    
          }
          else if ("info".equals(cmd))    
              <h3>Status info for Polaric APRSD</h3>
              <fieldset>
              { simpleLabel("items", "leftlab", "Server kjørt siden:", TXT(""+_time)) }
              { simpleLabel("items", "leftlab", "Server versjon:", TXT(""+Main.version)) }
              { simpleLabel("items", "leftlab", "Antall APRS enheter:", TXT(""+_db.nItems())) }
              { simpleLabel("items", "leftlab", "Antall forbindelser:", TXT(""+_db.getRoutes().nItems())) }
              { simpleLabel("items", "leftlab", "Egne objekter:", TXT(""+_db.getOwnObjects().nItems())) }   
              { simpleLabel("items", "leftlab", "Antall HTTP klienter nå:", TXT(""+(_requests-1))) }  
              { simpleLabel("items", "leftlab", "Antall besøk (sesjoner):", TXT(""+_sessions)) }
              { simpleLabel("items", "leftlab", "Antall HTTP forespørsler:", TXT(""+_reqNo)) }   
              { simpleLabel("freemem", "leftlab", "Brukt minne:", 
                  TXT( Math.round(StationDBImp.usedMemory()/1000)+" KBytes")) }   
              <br/>
              { simpleLabel("ch1", "leftlab", "Kanal 1 (APRS-IS): ", 
                   if (Main.ch1==null) TXT("---") else TXT(""+Main.ch1)) } 
              { simpleLabel("ch2", "leftlab", "Kanal 2 (TNC): ", 
                   if (Main.ch2==null) TXT("---") else TXT(""+Main.ch2)) }
              { simpleLabel("igate", "leftlab", "Igate: ", 
                   if (Main.igate==null) TXT("---") else TXT(""+Main.igate)) }   
              { if (Main.rctl != null && !Main.rctl.isEmpty())  
                   simpleLabel("rctl", "leftlab", "Fjernkontroll: ", TXT(""+Main.rctl)) else null; }     
                   
              </fieldset>  
              <input type="submit" onclick="window.close()" id="cancel" value="Avbryt"/>
          else
             <h3>Ukjent kommando</h3>
             
        printHtml (out, htmlBody(true, head, action(hdr, parms)));    
   }


   /**
    * set own position
    */          
   def _serveSetOwnPos(hdr: Properties, parms: Properties, out: PrintWriter) : String =
   {
        val pos = getUtmCoord(parms, 'W', _utmzone)
        val prefix = <h2>Legge inn egen posisjon</h2>
        val p = Main.ownpos
       
        /* Fields to be filled in */
        def fields(hdr: Properties, parms: Properties): NodeSeq =
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
        def action(hdr: Properties, parms: Properties): NodeSeq = {
               if (!authorizedForAdmin(hdr))
                  <h3>Du er ikke autorisert for admin operasjoner</h3>
               else {
                  val osymtab = parms.getProperty("osymtab")
                  val osym  = parms.getProperty("osym")
                  System.out.println("*** SET OWN POS by user '"+getAuthUser(hdr)+"'")
                  p.updatePosition(new Date(), pos, 
                      if (osymtab==null) '/' else osymtab(0), if (osym==null) 'c' else osym(0))
                  
                 <h2>Posisjon registrert</h2>
                 <p>pos={showUTM(pos) }</p>
              }
           };
            
        printHtml (out, htmlBody (true, null, htmlForm(hdr, parms, prefix, fields, IF_AUTH(action))))
    }   
   


   def _serveSarMode(hdr: Properties, parms: Properties, out: PrintWriter) : String =
   {
       val prefix = <h2>Søk og redningsmodus</h2>
       var filter = parms.getProperty("sar_prefix")
       var reason = parms.getProperty("sar_reason")
       val on = parms.getProperty("sar_on")
       
       def fields(hdr: Properties, parms: Properties): NodeSeq =          
           <xml:group>
           <p>Alias-info o.l. bare synlig for innloggete brukere.</p>
           <label for="sar_on" class="lleftlab">SAR modus:</label>
           { checkBox("sar_on", Main.sarmode!=null, TXT("aktivert")) }  
           <br/>
           
           <label for="sar_prefix" class="lleftlab">Skjul prefiks:</label>
           <input id="sar_prefix" name="sar_prefix" width="50" value= 
             { if ( Main.sarmode==null) "" else Main.sarmode.getFilter() }></input>
           <br/>
           
           <label for="sar_reason" class="lleftlab">Beskrivelse:</label>
           { if (Main.sarmode == null)
                <input id="sar_reason" name="sar_reason" width="50" value={""} ></input>
             else 
                <xml:group>
                <label id="sar_reason">{ Main.sarmode.getReason() }
                <em> { "("+Main.sarmode.getUser()+")" } </em></label>
                <br/>
                { simpleLabel("sar_date", "lleftlab", "Aktivert:", TXT(""+Main.sarmode.getTime())) }
                </xml:group>
           }
           </xml:group>     
              
              
       def action(hdr: Properties, parms: Properties): NodeSeq = 
       {
          AprsPoint.abortWaiters(true);
          if (on != null && "true".equals(on) ) {
               val filt = if ("".equals(filter)) "NONE" else filter;
               Main.sarmode = new SarMode(reason, getAuthUser(hdr), filter);
               if (Main.rctl != null) 
                  Main.rctl.sendRequestAll("SAR "+getAuthUser(hdr)+" "+filt+" "+reason, null);
               <h3>Aktivert</h3>
               <p>{reason}</p>
          }
          else {   
               Main.sarmode = null;
               if (Main.rctl != null) 
                   Main.rctl.sendRequestAll("SAR OFF", null);
               <h3>Avsluttet</h3>
          } 
       }
       
             
       printHtml (out, htmlBody (true, null, htmlForm(hdr, parms, prefix, fields, IF_AUTH(action) )))
   }
   
   
   def _serveSarUrl(hdr: Properties, parms: Properties, out: PrintWriter) : String =
   {
       val url = parms.getProperty("url")
       
       def action(hdr: Properties, parms: Properties): NodeSeq = 
       {
          if (!authorizedForUpdate(hdr) && Main.sarurl != null )  
              <h3>SAR URL ikke tilgjengelig eller du er ikke autorisert</h3>
          else {
              val sarurl = Main.sarurl.create(url)
              <h1>Kort-URL for søk og redning</h1>
              <h2><a class="sarurl" href={sarurl}>{sarurl}</a></h2>
              <p>Gyldig i 1 døgn fra nå</p>
          }
       }   
       printHtml (out, htmlBody(false, null, action(hdr, parms)));        
   }
      
      
      
      
   /**
    * Delete APRS object.
    */          

   def _serveDeleteObject(hdr: Properties, parms: Properties, out: PrintWriter) : String =
   {
       val id = parms.getProperty("objid")
       val prefix = <h2>Slett objekt</h2>
       
       def fields(hdr: Properties, parms: Properties): NodeSeq =
           <label for="objid" class="lleftlab">Objekt ID:</label>
           <input id="objid" name="objid" type="text" size="9" maxlength="9"
              value={if (id==null) "" else id.replaceFirst("@.*", "")} />;
      
      
       def action(hdr: Properties, parms: Properties): NodeSeq =
          if (id == null) {
              <h3>Feil:</h3>
              <p>må oppgi 'objid' som parameter</p>;
          }
          else {
              if (Main.ownobjects.delete(id)) {
                  System.out.println("*** DELETE OBJECT: '"+id+"' by user '"+getAuthUser(hdr)+"'")
                  <h3>Objekt slettet!</h3>
              }
              else
                  <h3>Fant ikke objekt: {id}</h3>
          }  
          
       printHtml (out, htmlBody (true, null, htmlForm(hdr, parms, prefix, fields, IF_AUTH(action) )))
   }          




   def _serveResetInfo(hdr: Properties, parms: Properties, out: PrintWriter) : String =
   {
       val id = parms.getProperty("objid")
       val prefix = <h2>Nullstill info om stasjon/objekt</h2>
       
       def fields(hdr: Properties, parms: Properties): NodeSeq =
           <label for="objid" class="lleftlab">Objekt ID:</label>
           <input id="objid" name="objid" type="text" size="9" maxlength="9"
              value={if (id==null) "" else id} />;
      
      
       def action(hdr: Properties, parms: Properties): NodeSeq =
          if (id == null) {
              <h3>Feil:</h3>
              <p>må oppgi 'objid' som parameter</p>;
          }
          else {
             val x = _db.getItem(id);
             if (x != null)
                x.reset();
             <h3>Info om objekt nullstilt!</h3>
          }  
       printHtml (out, htmlBody (true, null, htmlForm(hdr, parms, prefix, fields, IF_AUTH(action))))
   }          



             
   /**
    * add or edit APRS object.
    */          
   def _serveAddObject(hdr: Properties, parms: Properties, out: PrintWriter) : String =
   {
        val pos = getUtmCoord(parms, 'W', _utmzone)
        val id = parms.getProperty("objid")
        val prefix = <h2>Legge inn objekt</h2>
        
        /* Fields to be filled in */
        def fields(hdr: Properties, parms: Properties): NodeSeq =
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
        def action(hdr: Properties, parms: Properties): NodeSeq =
            if (id == null || !id.matches("[a-zA-Z0-9_].*\\w*")) {
               <h2>Feil</h2>
               <p>må oppgi 'objid' som parameter og denne må begynne med bokstav/tall</p>;
            }
            else {
               val osymtab = parms.getProperty("osymtab")
               val osym  = parms.getProperty("osym")
               val otxt = parms.getProperty("descr")
               val perm = parms.getProperty("perm");
               System.out.println("*** SET OBJECT: '"+id+"' by user '"+getAuthUser(hdr)+"'")
               if ( Main.ownobjects.add(id, pos,
                      if (osymtab==null) '/' else osymtab(0),
                      if (osym==null) 'c' else osym(0),
                      if (otxt==null) "" else otxt,
                      "true".equals(perm) ))
                  
                  <h2>Objekt registrert</h2>
                  <p>ident={"'"+id+"'"}<br/>pos={showUTM(pos) }</p>;
               else
                  <h2>Kan ikke registreres</h2>
                  <p>Objekt '{id}' er allerede registrert av noen andre</p>
           };
            
        printHtml (out, htmlBody (true, null, htmlForm(hdr, parms, prefix, fields, IF_AUTH(action))))
    }


   def _directionIcon(direction:Int): NodeSeq = 
        direction match {
          case x if !(22 until 337 contains x) =>
              <div><img src= {"srv/dicons/dN.png"}/> N</div>
          case x if (22 until 67 contains x) =>
              <div><img src= {"srv/dicons/dNE.png"}/> NE</div>
          case x if (67 until 112 contains x) =>
              <div><img src= {"srv/dicons/dE.png"}/> E</div>
          case x if (112 until 157 contains x) =>
              <div><img src= {"srv/dicons/dSE.png"}/> SE</div>
          case x if (157 until 202 contains x) =>
              <div><img src= {"srv/dicons/dS.png"}/> S</div>
          case x if (202 until 247 contains x) =>
              <div><img src= {"srv/dicons/dSW.png"}/> SW</div>
          case x if (247 until 292 contains x) =>
              <div><img src= {"srv/dicons/dW.png"}/> W</div>
          case x if (292 until 337 contains x) =>
              <div><img src= {"srv/dicons/dNW.png"}/> NW</div>
          case _ => null;
      }


   
  /** 
    * Presents a status list over registered stations (standard HTML)
    */
   
   def _serveSearch(hdr: Properties, parms: Properties, out: PrintWriter, vfilt: ViewFilter): String =
   {
       var arg = parms.getProperty("filter");
       var mob = parms.getProperty("mobile");
       if (arg == null) 
           arg  = "__NOCALL__"; 
       val infra = _infraonly || "infra".equals(arg);
       val result: NodeSeq =
         <table>
         {
            for ( x:AprsPoint <- _db.getAll(arg)
                  if ( vfilt.useObject(x)) ) yield
            {
               val s = if (!x.isInstanceOf[Station]) null
                       else x.asInstanceOf[Station];

               val moving = if (s!=null) !s.getHistory().isEmpty()
                            else false;

               <tr onclick={
                 if (x.visible() && x.getPosition() != null) 
                     "findStation('" + x.getIdent() + "', 'true')"
                 else "" 
               }>
          
               <td>{x.getIdent()}</td>
               <td> 
               { if (moving && s.getSpeed() > 0)
                       _directionIcon(s.getCourse()) else 
                          if (s==null) TXT("obj") else null } 
               </td>
                          
               <td> { df.format(x.getUpdated()) } </td>
               { if (!"true".equals(mob)) 
                  <td> { if (x.getDescr() != null) x.getDescr() else "" } </td> 
                 else null }
               </tr>
           }
        } 
        </table>;
        printHtml (out, htmlBody (false, null, result))
   }
 



   /**
    * Selection of icon. List available icons. 
    */
   def iconSelect(s: AprsPoint): NodeSeq =
   {
       val icondir = new File(_icondir);
       
       val flt = new FilenameFilter()
           { def accept(dir:File, f: String): boolean = f.matches(".*\\.(png|gif|jpg)") } 
       val cmp = new Comparator[File] ()
           { def compare (f1: File, f2: File) : int = f1.getName().compareTo(f2.getName()) } 
       
       val files = icondir.listFiles(flt);
       Arrays.sort(files, cmp);
          
       <div id="iconselect">    
       <input type="radio" name="iconselect" value="system"
                   checked={if (s.iconIsNull()) "checked" else null:String }>Automatisk</input>
       { if (files != null)
           for (f:File <- files) yield
              <input type="radio" name="iconselect" value={f.getName()}
                  checked={if (!s.iconIsNull() && f.getName().equals(s.getIcon())) "checked"
                           else null:String}>
              <img src={"icons/"+f.getName()} width="22" height="22" />&nbsp;
              </input>
         else null
       }
       </div>
    }
   



  
   /** 
    * Info about station/object (standard HTML)
    */
   def _serveStation(hdr: Properties, parms: Properties, out: PrintWriter, 
           canUpdate: boolean): String =
   {        
        val id = parms.getProperty("id")
        val x = _db.getItem(id)
        val s = if (x.isInstanceOf[Station]) x.asInstanceOf[Station] else null
        val obj = if (x.isInstanceOf[AprsObject]) x.asInstanceOf[AprsObject] else null
        val edit  =  ( parms.getProperty("edit") != null )
        val simple =  ( parms.getProperty("simple") != null )
        val prefix = null
        if (obj != null)
            obj.update();


        def itemList(items : Set[String]) = 
             <div class="trblock">
             {  
                var i=0
                if (items != null) 
                 for (it <- items) yield {
                    i += 1
                    val xx = _db.getItem(it)
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
        def fields(hdr: Properties, parms: Properties): NodeSeq =
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
                 simpleLabel("status", "leftlab", "Status:",
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
                  simpleLabel("altitude", "leftlab", "Høyde (o/h):", TXT(s.getAltitude() + " m ")) else null}
            { if (s != null && s.getSpeed() > 0)
                  simpleLabel("cspeed", "leftlab", "Bevegelse:", _directionIcon(s.getCourse())) else null}

            { simpleLabel("hrd", "leftlab", "Sist rapportert:", TXT( df.format(x.getUpdated()))) }
            
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
                  { iconSelect(x) }
                  </div>
               else null        
            }
                          
            </xml:group> ;



        /* Action. To be executed when user hits 'submit' button */
        def action(hdr: Properties, parms: Properties): NodeSeq =
        {
             val perm = parms.getProperty("pers");
             x.setPersistent( "true".equals(perm) );  
             val hide = parms.getProperty("hidelabel");
             x.setLabelHidden( "true".equals(hide) );     
             
             val newcolor = parms.getProperty("newcolor");
             if (s != null && "true".equals(newcolor) )
                s.nextTrailColor();             


             /* Alias setting */
             var alias = parms.getProperty("nalias");
             var ch = false;
             if (alias != null && alias.length() > 0)      
                 ch = x.setAlias(alias);
             else
                { ch = x.setAlias(null)
                  alias = "NULL"
                }
             System.out.println("*** ALIAS: '"+alias+"' for '"+x.getIdent()+"' by user '"+getAuthUser(hdr)+"'")
             if (ch && Main.rctl != null)
                 Main.rctl.sendRequestAll("ALIAS "+x.getIdent()+" "+alias, null);

             /* Icon setting */
             var icon = parms.getProperty("iconselect");
             if ("system".equals(icon)) 
                 icon = null; 
             if (x.setIcon(icon)) {      
                  System.out.println("*** ICON: for '"+x.getIdent()+"' by user '"+getAuthUser(hdr)+"'")   
                  if (Main.rctl != null) 
                     Main.rctl.sendRequestAll("ICON "+x.getIdent() + " " +
                        { if (icon==null) "NULL" else icon }, 
                         null);
             }
            
             <h3>Oppdatert</h3>
        }


        printHtml (out, htmlBody ( !simple, null, 
                                   if (simple) fields(hdr, parms)
                                   else htmlForm(hdr, parms, prefix, fields, IF_AUTH(action))))
    }
      




  
    private def cleanPath(txt:String): String = 
        txt.replaceAll("((WIDE|TRACE|SAR|NOR)[0-9]+(\\-[0-9]+)?\\*?),?|(qA.),?", "")
           .replaceAll("\\*", "").replaceAll(",", ", ")
           
           
      
   /** 
    * Presents a list over last positions and movements (standard HTML)
    */
   def _serveStationHistory(hdr: Properties, parms: Properties, out: PrintWriter): String =
   {        
       val s = _db.getStation(parms.getProperty("id"))
       val result: NodeSeq =
          if (s == null)
             <h2>Feil:</h2><p>Fant ikke stasjon</p>;
          else
             <table>
               <tr><th>Tid</th><th>Km/h </th><th>Retn </th><th>Distanse</th><th>APRS via</th></tr>
               {
                   val h = s.getHistory()
                   var x = s.getHItem()
                   var i = 0;
                   for (it <- h.items()) yield {
                      i += 1
                      val arg = "'"+s.getIdent()+"', "+i
                      <tr onmouseover={"histList_hover("+arg+");"} 
                          onmouseout={"histList_hout("+arg+");"}
                          onclick = {"histList_click("+arg+");"} >
                      <td> { tf.format(x.time) } </td>
                      <td> { if (x.speed >= 0) x.speed.toString else "" } </td>
                      <td> { if (x.speed > 0) _directionIcon(x.course) else ""} </td>
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

        printHtml(out, htmlBody(false, null, result))
    } 
 

    
    

    def _serveTrailPoint(hdr: Properties, parms: Properties, out: PrintWriter): String =
    {
       val s = _db.getStation(parms.getProperty("id"))
       val index = Integer.parseInt(parms.getProperty("index"))
       val h = s.getHistory()
       val item = h.getPoint(index)
       /* FIXME: Check if valid result */
    
       val result : NodeSeq =
         <xml:group>
         <label for="callsign" class="lleftlab">Ident:</label>
         <label id="callsign"><b> { s.getIdent() } </b></label>
         { simpleLabel("time",  "lleftlab", "Tid:", TXT( df.format(item.time))) }
         { simpleLabel("speed", "lleftlab", "Fart:", TXT(item.speed+" km/h") )  }
         { simpleLabel("dir",   "lleftlab", "Retning:", _directionIcon(item.course))  }
         <div id="traffic">
         { simpleLabel("via",   "lleftlab", "APRS via:", TXT(cleanPath(item.pathinfo)))  }
         </div>
         </xml:group>
       printHtml(out, htmlBody(false, null, result)) 
    }
  
  }
}
