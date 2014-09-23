/* 
 * Copyright (C) 2014 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
        ;

        
       protected def br = <br/>
           
       /** File name prefix */
       // FIXME: Do we need this anymore??
       protected def fprefix(req: Request) : String = 
           if ((req.getParameter("ajax") == null)) "../"+_wfiledir else _wfiledir 
           ;
           
           
       protected def label(id:String, cls:String, lbl:String): NodeSeq =
           <label for={id} class={cls}>{lbl}</label>
          ;
       
       
       protected def label(id:String, cls:String, lbl:String, title: String): NodeSeq =
           <label for={id} class={cls} title={title}>{lbl}</label>
          ;
          
       /** Label with field */   
       protected def simpleLabel(id:String, cls:String, lbl:String, content: NodeSeq): NodeSeq =
           <label for={id} class={cls}>{lbl}</label>
           <label id={id}> {content}</label>
          ;
       
       /** Label with field */
       protected def simpleLabel(id:String, cls:String, lbl:String, title:String, content: NodeSeq): NodeSeq =  
           <label for={id} title={title} class={cls}>{lbl}</label>
           <label id={id}> {content}</label>
          ;
          
      /** Input type text */
      protected def textInput(id : String, length: Int, maxlength: Int, pattern: String, value: String): NodeSeq =  
           <input type="text" id={id} name={id} size={length.toString()} maxlength={maxlength.toString()} 
                  pattern={pattern} value={value} />
          ;
          
      protected def textInput(id : String, length: Int, maxlength: Int, value: String): NodeSeq = 
          textInput(id, length, maxlength, null, value)
          ;
          
          
      protected def TXT(t:String): NodeSeq = <xml:group>{t}</xml:group>
   
   
      /** Input type checkbox */
      protected def checkBox(id:String, check: Boolean, t: NodeSeq): NodeSeq =
           <xml:group>
           <input type="checkbox" name={id} id={id}
              checked= {if (check) "checked" else null:String}
              value="true" />
           {t}
           </xml:group>
           ;


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
       
       
       protected def IF_ADMIN( func: (Request) => NodeSeq ) 
                      : (Request) => NodeSeq = 
       {
          def wrapper(req : Request) : NodeSeq =
             if (authorizedForAdmin(req))
                func(req)
             else
                <h2>Du er ikke autorisert for denne operasjonen</h2>
      
          wrapper
       }

       def simple_submit(req: Request): NodeSeq =
           <input type="submit" name="update" id="update" value="Oppdater"/>
           ;
           
       def default_submit(req: Request): NodeSeq = 
           <input type="submit" onclick="window.close()" id="cancel" value="Avbryt"/>
           <input type="submit" name="update" id="update" value="Oppdater"/>
           ;
           
           
       /**
        * Generic HTML form method.
        * Field content and action to be performed are given as function-arguments
        */
       protected def htmlForm( req  : Request,
                             prefix : NodeSeq,
                             fields : (Request) => NodeSeq,
                             action : (Request) => NodeSeq,
                             close  : Boolean,
                             submit : (Request) => NodeSeq ) : NodeSeq =
       {
           if (req.getParameter("update") != null) 
               <div>
                 {
                   if (close)
                     <script source="javascript">setTimeout('window.close()', 2000);</script>
                   else null  
                 } 
                 { action(req) }
               </div>;
        
           else

              <form action="" method="post">
                  { prefix }
                <fieldset>
                  { fields(req) }
                </fieldset>
                  { submit(req);}
              </form>
       }

       
       protected def htmlForm( req  : Request,
                             prefix : NodeSeq,
                             fields : (Request) => NodeSeq,
                             action : (Request) => NodeSeq) : NodeSeq =
           htmlForm(req, prefix, fields, action, true, default_submit)
           ;
           
           

       protected def printHtml(res : Response, content : Node ) =
       {        
            val out = getWriter(res);
            res.setValue("Content-Type", "text/html; charset=utf-8");
            out.println (doctype + Xhtml.toXhtml(content) )
            out.close()
       }
  

       protected def printXml(res : Response, content : Node ) = 
       {
           val out = getWriter(res)
           if (res.getValue("Content-Type") == null)
               res.setValue("Content-Type", "text/xml; charset=utf-8");
           out.println("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>")
           out.println(content.toString())
           out.close()
       }
       
       
  
   /**
    * Exctract UTM reference from request parameters.
    *   x and y: coordinates relative to mapserver UTM zone
    *   utmz: UTM zone (optional)
    *   returns UTM reference. Null if not possible to construct a correct
    *   UTM reference from the given parameters.
    *   
    *   zones will probably be mandatory from version 1.6. 
    */
   protected def getUtmCoord(req : Request, zone: Int) : UTMRef =
   {
        val x = req.getParameter("x")
        val y = req.getParameter("y")
        var utmz = req.getParameter("utmz")
        try {
           if (x != null && y != null)
               new UTMRef( x.toDouble, y.toDouble,
                   'W', if (utmz==null) _utmzone else utmz.toInt ).toLatLng().toUTMRef();
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
   protected def utmForm(nzone: Char, zone: Int, x: String, y: String) : NodeSeq =
       <input id="utmz" name="utmz" type="text" size="2" maxlength="2" pattern="[0-9]{2}" value={zone.toString} />
       <input id="utmnz" name="utmnz" type="text" size="1" maxlength="1" pattern="[a-zA-Z]" value={nzone.toString} />
       <input id="utmx" name="x" type="text" size="6" maxlength="6" pattern="[0-9]{6}" value={x} />
       <input id="utmy" name="y" type="text" size="7" maxlength="7" pattern="[0-9]{7}" value={y} />
       ;
        
        
   protected def utmForm(ref: Reference): NodeSeq =
   { 
      val sref = ref.toLatLng().toUTMRef()
      utmForm(sref.getLatZone(), sref.getLngZone(), sref.getEasting().toString(), sref.getNorthing().toString())
   }
       
       
   protected def utmForm(ref: String): NodeSeq =
   {
      val r = ref.split("\\s+")
      utmForm(r(0)(2), r(0).substring(0,2).toInt, r(1), r(2))
   }
   
   
   protected def utmForm(nzone: Char, zone: Int) : NodeSeq = 
       utmForm(nzone, zone, null, null)
       ;


   /**
    * Selection of icon. List available icons. 
    * @param s object to select icon for (see what is already selected)
    * @param wprefix  web prefix 
    * @param fprefix for icon files (relative to wprefix)
    */
   def iconSelect(s: PointObject, wprefix: String, fprefix: String): NodeSeq =
   {
       def fmatch(x: String, y: String): Boolean = {
          val yy = y.substring(y.lastIndexOf("/")+1)
          (x.equals(y) || x.equals(yy)) 
       }
       
       val webdir = System.getProperties().getProperty("webdir", ".")
       val fsprefix = if (fprefix.charAt(0) == '/') fprefix.substring(1,fprefix.length()) 
                      else fprefix 

       val icondir = new File(webdir+"/"+fsprefix)
       
       val flt = new FilenameFilter()
           { def accept(dir:File, f: String): Boolean = f.matches(".*\\.(png|gif|jpg)") } 
       val cmp = new Comparator[File] ()
           { def compare (f1: File, f2: File) : Int = f1.getName().compareTo(f2.getName()) } 
       val files = icondir.listFiles(flt); 

       <div id="iconselect">    
       <input type="radio" name="iconselect" value="system"
           checked={if (s== null || s.iconIsNull()) "checked" else null:String } /> Automatisk 
       
                   
       { if (files != null) {
           Arrays.sort(files, cmp)
           for (f:File <- files) yield {
              <xml:group>
              <input type="radio" name="iconselect" value={f.getName()}
                  checked={ if (s != null && !s.iconIsNull() && fmatch(f.getName(), s.getIcon())) "checked" else null:String } />
              <img src={wprefix+fprefix+f.getName()} width="22" height="22" />&nbsp;
              </xml:group> 
           }
         }
         else null
       }
       </div>
    }
   
  
  }

}
