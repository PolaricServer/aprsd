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

  trait ServerUtils extends ServerBase
  {
      val doctype = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN \">";

        
         
      
      protected def refreshPage(req: Request, resp: Response, t: Int, url: String) = 
      {
         val mid = req.queryParams("mid");
         val cid = req.queryParams("chan");
         var lang = req.queryParams("lang");
         lang = if (lang==null) "en" else lang
         val uparm = "?lang=" + lang + 
           { if (mid != null) "&mid="+mid else "" } + 
           { if (cid != null) "&chan="+cid else "" }
            
         resp.header("Refresh", t+";url=\""+url + uparm+"\"")
      }
      
      
      
      

      protected def htmlBody (req: Request, head : NodeSeq, content : NodeSeq, mapframe : String) : Node =
         if (req.queryParams("ajax") == null)           
            <html>
            <head>
            <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
            <script> 
             {
               " function hideButt(x) { " +
               "    var b = document.getElementById(x); " +
               "    if (b!=null) b.style.display='none'; } " +
               " function loadBody() { "+
               "   if (typeof(init_polaric) == 'function') { "+
               "      init_polaric('"+mapframe+"', '*'); " +
               "      hideButt('logout');" + 
               "   } else hideButt('cancel'); } " 
             }
            </script>
            { head }
            <link href={"style.css"} rel="stylesheet" type="text/css" />
            { if (req.queryParams("inapp") != null)
                  <script src="../Aprs/iframeApi.js"></script>
              else 
                  EMPTY 
            }
            </head>
            <body onload={"loadBody()"} >
            {content} 
            </body>
            </html>
       
        else
           <xml:group> { content } </xml:group>
        ;

        
       protected def htmlBody (req: Request, head : NodeSeq, content : NodeSeq) : Node = 
            htmlBody(req, head, content, "_PARENT_" )
           ;
        
       protected def br = <br/>
           
       protected def EMPTY = xml.NodeSeq.Empty
       
       
       /** File name prefix */
       // FIXME: Do we need this anymore??
       protected def fprefix(req: Request) : String = 
           if ((req.queryParams("ajax") == null)) "../"+_wfiledir else _wfiledir 
           ;
           
           
       protected def showIcon(req:Request, icon:String, size:String) = {
          val siz = if (size==null) "22" else size
          if (icon == null) EMPTY 
          else <img src={fprefix(req)+"/icons/"+icon} width={siz} height={siz} />
       }
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
      
      /** Input type textarea */
      protected def textArea(id : String, maxlen: Int, value: String): NodeSeq =  
           <textarea id={id} name={id} maxlength={maxlen.toString()}>{value}</textarea>
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
             if (getAuthInfo(req).sar)
                func(req)
             else {
                val i18n = getI18n(req)
                <h2> {i18n.tr("You are not authorized for this operation")} </h2>
             }
          wrapper
       }
       
       
       protected def IF_ADMIN( func: (Request) => NodeSeq ) 
                      : (Request) => NodeSeq = 
       {
          def wrapper(req : Request) : NodeSeq =
             if (getAuthInfo(req).admin)
                func(req)
             else {
                val i18n = getI18n(req)
                <h2> {i18n.tr("You are not authorized for this operation")} </h2>
             }
          wrapper
       }
       

       def simple_submit(req: Request): NodeSeq = {
           val i18n = getI18n(req)
           <button type="submit" name="update" id="update"> {i18n.tr("Update")} </button>
       }
           
           
       def default_submit(req: Request): NodeSeq = {
           val i18n = getI18n(req)
           <button type="submit" onclick="window.close()" id="cancel"> {i18n.tr("Cancel")} </button>
           <button type="submit" name="update" id="update"> {i18n.tr("Update")} </button>
       }
           
           
       /**
        * Generic HTML form method.
        * Field content and action to be performed are given as function-arguments
        */
       def _htmlForm( req  : Request,
                             prefix : NodeSeq,
                             fields : (Request) => NodeSeq,
                             action : (Request) => NodeSeq,
                             close  : Boolean,
                             jump   : Integer,
                             submit : (Request) => NodeSeq ) : NodeSeq =
       {
           if (req.queryParams("update") != null) 
               <div>
                 {
                   if (close)
                     <script>setTimeout('window.close()', 2000);</script>
                   else {
                      if (jump != 0) 
                         <script>
                           {"setTimeout(function() {window.history.go("+jump+")}, 2000);"}
                         </script>
                   }
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

       
       def htmlFormJump( req  : Request,
                             prefix : NodeSeq,
                             fields : (Request) => NodeSeq,
                             action : (Request) => NodeSeq,
                             close  : Boolean,
                             submit : (Request) => NodeSeq ) : NodeSeq =
           _htmlForm(req, prefix, fields, action, close, -1, submit)
           ; 
           
           
           
       def htmlFormJump( req  : Request,
                             prefix : NodeSeq,
                             fields : (Request) => NodeSeq,
                             action : (Request) => NodeSeq) : NodeSeq =
           htmlFormJump(req, prefix, fields, action, true, default_submit)
           ;
       
       
       def htmlForm( req  : Request,
                             prefix : NodeSeq,
                             fields : (Request) => NodeSeq,
                             action : (Request) => NodeSeq,
                             close  : Boolean,
                             submit : (Request) => NodeSeq ) : NodeSeq =
           _htmlForm(req, prefix, fields, action, close, 0, submit)
           ; 
           
           
           
       def htmlForm( req  : Request,
                             prefix : NodeSeq,
                             fields : (Request) => NodeSeq,
                             action : (Request) => NodeSeq) : NodeSeq =
           htmlForm(req, prefix, fields, action, true, default_submit)
           ;   
       
       

       protected def printHtml(resp : Response, content : Node ) : String =
       {        
            resp.`type`("text/html; charset=utf-8")
            return doctype + Xhtml.toXhtml(content)
       }
  

       protected def printXml(res : Response, content : Node ) : String = 
       {   
           if (res.`type` == null)
               res.`type`("text/xml; charset=utf-8")
           return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>" +
                   content.toString()
       }
       
       
  
   /**
    * Exctract reference from request parameters.
    *   x and y: coordinates in lat long projection
    *   returns reference. Null if not possible to construct a correct
    *   reference from the given parameters.
    *    
    */
   protected def getCoord(req : Request) : Reference =
   {
        val x = req.queryParams("x")
        val y = req.queryParams("y")
        try {
           if (x != null && y != null)
               new LatLng( y.toDouble, x.toDouble );
           else
               null
        }
        catch {
           case e: Exception => println("*** Warning: "+e); null }
   }


   
   /** 
    * Print reference as UTM reference. 
    */
   protected def showUTM(req: Request, ref: Reference) : NodeSeq = {
      val i18n = getI18n(req)
      try {
         val sref = ref.toLatLng().toUTMRef().toString()
         <span class="utmref">
         {sref.substring(0,5)}<span class="kartref">{
           sref.substring(5,8)}</span>{sref.substring(8,13)}<span class="kartref">{
           sref.substring(13,16)}</span>{sref.substring(16)}
         </span>  
      }
      catch {
          case e:Exception => Text(i18n.tr("(invalid position)"))
      }
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


  
   def cleanPath(txt:String): String = 
        txt.replaceAll("((WIDE|TRACE|SAR|NOR)[0-9]*(\\-[0-9]+)?\\*?,?)|(qA.),?", "")
           .replaceAll("\\*", "").replaceAll(",+|(, )+", ", ")   
        ;
        
        
   def _directionIcon(direction:Int, fprefix: String): NodeSeq = 
        direction match {
          case x if !(22 until 337 contains x) =>
              <span><img src= {fprefix+"/images/dN.png"}/> N</span>
          case x if (22 until 67 contains x) =>
              <span><img src= {fprefix+"/images/dNE.png"}/> NE</span>
          case x if (67 until 112 contains x) =>
              <span><img src= {fprefix+"/images/dE.png"}/> E</span>
          case x if (112 until 157 contains x) =>
              <span><img src= {fprefix+"/images/dSE.png"}/> SE</span>
          case x if (157 until 202 contains x) =>
              <span><img src= {fprefix+"/images/dS.png"}/> S</span>
          case x if (202 until 247 contains x) =>
              <span><img src= {fprefix+"/images/dSW.png"}/> SW</span>
          case x if (247 until 292 contains x) =>
              <span><img src= {fprefix+"/images/dW.png"}/> W</span>
          case x if (292 until 337 contains x) =>
              <span><img src= {fprefix+"/images/dNW.png"}/> NW</span>
          case _ => null;
      }

      
      
   /**
    * Selection of icon. List available icons. 
    * @param s object to select icon for (see what is already selected)
    * @param wprefix  web prefix 
    * @param fprefix for icon files (relative to wprefix)
    */
   def iconSelect(req: Request, s: PointObject, wprefix: String, fprefix: String): NodeSeq =
   {
       def fmatch(x: String, y: String): Boolean = {
          val yy = y.substring(y.lastIndexOf("/")+1)
          (x.equals(y) || x.equals(yy)) 
       }
      
      
       val i18n = getI18n(req)
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
           checked={if (s== null || s.iconIsNull()) "checked" else null:String } /> {i18n.tr("Automatic")} 
       
                   
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
