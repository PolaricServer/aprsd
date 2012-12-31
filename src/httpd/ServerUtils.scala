import java.util._
import java.io._
import scala.xml._
import org.simpleframework.transport.connect.Connection
import org.simpleframework.transport.connect.SocketConnection
import org.simpleframework.http._
import uk.me.jstott.jcoord._



package no.polaric.aprsd.http 
{

  trait ServerUtils extends ServerBase
  {
      val doctype = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN \">";


      protected def htmlBody (req: Request, head : NodeSeq, content : NodeSeq) : Node =
        if (req.getParameter("ajax") == null)           
            <html>
            <head>
            <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
            { head }
            <link href={ fprefix(req)+"/style.css"} rel="stylesheet" type="text/css" />
            </head>
            <body>
            {content} 
            </body>
            </html>
       
       else
           <xml:group> { content } </xml:group>


           
       protected def fprefix(req: Request) : String = 
           if ((req.getParameter("ajax") == null)) "/"+_wfiledir else _wfiledir 

      
      
       protected def simpleLabel(id:String, cls:String, lbl:String, content: NodeSeq): NodeSeq =
           <label for={id} class={cls}>{lbl}</label>
           <label id={id}> {content}</label>
          ;
          
       protected def simpleLabel(id:String, cls:String, lbl:String, title:String, content: NodeSeq): NodeSeq =  
           <label for={id} title={title} class={cls}>{lbl}</label>
           <label id={id}> {content}</label>
          ;
          
          
       protected def TXT(t:String): NodeSeq = <xml:group>{t}</xml:group>
   
   
       protected def checkBox(id:String, check: Boolean, t: NodeSeq): NodeSeq =
           <input type="checkbox" name={id} id={id}
              checked= {if (check) "checked" else null:String}
              value="true">
          {t}
          </input>



       /**
        * Modify a function to execute only if user is authorized for update. 
        * Runs original function if authorized. Return error message if not. 
        */
       protected def IF_AUTH( func: (Request) => NodeSeq ) 
                      : (Request) => NodeSeq = 
       {
          def wrapper(req : Request) : NodeSeq =
          if (authorizedForUpdate(req))
             func(req)
          else
             <h2>Du er ikke autorisert for denne operasjonen</h2>
      
          wrapper
       }




       /**
        * Generic HTML form method.
        * Field content and action to be performed are given as function-arguments
        */
       protected def htmlForm( req    : Request,
                             prefix : NodeSeq,
                             fields : (Request) => NodeSeq,
                             action : (Request) => NodeSeq ) : NodeSeq =
       {
           if (req.getParameter("update") != null) 
               <div><script source="javascript">setTimeout('window.close()', 2000);</script>
               { action(req) }
               </div>;
        
           else

              <form action="" method="post">
              { prefix }
              <fieldset>
                 { fields(req) }
              </fieldset>

              <input type="submit" onclick="window.close()" id="cancel" value="Avbryt"/>
              <input type="submit" name="update" id="update" value="Oppdater"/>
              </form>
       }



       protected def printHtml(res : Response, content : Node ) =
       {        
            val out = getWriter(res);
            res.set("Content-Type", "text/html; charset=utf-8");
            out.println (doctype + Xhtml.toXhtml(content, true, true) )
            out.close()
       }
  

  
   /**
    * Exctract UTM reference from request parameters.
    *   x and y: coordinates relative to mapserver UTM zone
    *   utmz: UTM zone (optional)
    *   utmnz: UTM zone letter (optional)
    *   returns UTM reference. Null if not possible to construct a correct
    *   UTM reference from the given parameters.
    */
   protected def getUtmCoord(req : Request, nzone: Char, zone: Int) : UTMRef =
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
   protected def showUTM(ref: Reference) : NodeSeq =
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
   protected def utmForm(nzone: Char, zone: Int) : NodeSeq =
       <input id="utmz" name="utmz" type="text" size="2" maxlength="2" value={zone.toString} />
       <input id="utmnz" name="utmnz" type="text" size="1" maxlength="1" value={nzone.toString} />
       <input id="utmx" name="x" type="text" size="6" maxlength="6"/>
       <input id="utmy" name="y" type="text" size="7" maxlength="7"/>;




   /**
    * Selection of icon. List available icons. 
    * @param s object to select icon for (see what is already selected)
    * @param wprefix  web prefix 
    * @param fprefix for icon files (relative to wprefix)
    */
   def iconSelect(s: AprsPoint, wprefix: String, fprefix: String): NodeSeq =
   {
       val webdir = System.getProperties().getProperty("webdir", ".")
       val fsprefix = if (fprefix.charAt(0) == '/') fprefix.substring(1,fprefix.length()) 
                      else fprefix 
       System.out.println("ICONDIR="+webdir+"/"+fsprefix);
       val icondir = new File(webdir+"/"+fsprefix)
       
       val flt = new FilenameFilter()
           { def accept(dir:File, f: String): boolean = f.matches(".*\\.(png|gif|jpg)") } 
       val cmp = new Comparator[File] ()
           { def compare (f1: File, f2: File) : int = f1.getName().compareTo(f2.getName()) } 
       
       val files = icondir.listFiles(flt);
       <div id="iconselect">    
       { if (s != null)
         <input type="radio" name="iconselect" value="system"
                   checked={if (s.iconIsNull()) "checked" else null:String }>Automatisk</input>
       }            
       { if (files != null) {
           Arrays.sort(files, cmp);
           for (f:File <- files) yield
              <input type="radio" name="iconselect" value={f.getName()}
                  checked={if (s != null && !s.iconIsNull() && f.getName().equals(s.getIcon())) "checked"
                           else null:String}>
              <img src={wprefix+fprefix+f.getName()} width="22" height="22" />&nbsp;
              </input>
         }
         else null
       }
       </div>
    }
   
  
  }

}
