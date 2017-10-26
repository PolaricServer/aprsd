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
import org.xnap.commons.i18n._





package no.polaric.aprsd.http
{

  object ConfigUtils {
      /* Available channel types */
      var chantype = Array("APRSIS", "KISS", "TCPKISS", "TNC2")
      var CHANTYPE = "APRSIS|KISS|TCPKISS|TNC2"
      
      
      def addChanType(t: String) = {
         chantype = chantype :+ t
         CHANTYPE += ("|" + t)
      }
       
  }
  
  

  trait ConfigUtils extends ServerUtils
  {
      override def getApi = super.getApi
        
          
          
      /* Regular expressions that define format of input of different types */    
      val TEXT = ".*"
      val NAME = "[A-Za-z0-9_\\-\\.\\/]+"
      val LIST = "([A-Za-z0-9_\\-\\.]+)(,\\s?([A-Za-z0-9_\\-\\.]+))*"
      val NUMBER = "\\-?[0-9]+"
      val BOOLEAN = "true|false|TRUE|FALSE"
      val CALLSIGN = "[A-Za-z0-9]{3,6}(\\-[0-9]{1,2})?"
      val UTMPOS = "[0-9]{2}[A-Za-z]\\s+[0-9]{6}\\s+[0-9]{7}"
      

  
         
      
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
      

                   
      protected def typeField(propname: String, id: String, lbl: String, title: String) : NodeSeq = 
      {
         val pval = getApi.getProperty(propname, "")
         label(id, "leftlab", lbl, title) ++
         <select name={id} id={id}> 
            { for (x <- ConfigUtils.chantype) yield
                 <option value={x} selected={if (x.equals(pval)) "selected" else null}>{x}</option>
            }
         </select><br/>
       }           
          
          
      /** Text field with label */
      protected def textField(propname: String, id: String, lbl: String, title: String, 
                              length: Int, maxlength: Int, pattern: String, ptext: String): NodeSeq = 
          label(id, "leftlab", lbl, title) ++ 
          textInput(id, length, maxlength, pattern, getApi.getProperty(propname, "")) ++ 
          {
             if (ptext != null)
                <span class="postfield">{ptext}</span>
             else null
          } ++ br
          ;     
         
   
          
      protected def textField(propname: String, id: String, lbl: String, title: String, 
                              length: Int, maxlength: Int, pattern: String): NodeSeq = 
          textField(propname, id, lbl, title, length, maxlength, pattern, "")
          ;
          
          
      /** Boolean field without label */    
      protected def boolField(propname: String, id: String, lbl: String): NodeSeq = 
          checkBox(id, getApi.getBoolProperty(propname,false), TXT(lbl))
          ;
          
          
      /** Boolean field with label */
      protected def sBoolField(propname: String, id: String, lbl: String, title: String): NodeSeq =
          label(id, "leftlab", lbl, title) ++
          boolField(propname, id, "Aktivert.") ++ br
          ;
          
          
      /** UTM position field */    
      protected def utmField(propname: String): NodeSeq = 
          if (getApi.getProperty(propname, null) == null)
             utmForm('W',33)
          else
             utmForm(getApi.getProperty(propname, null))
          ;
        
       
      
      protected def printState(st: Channel.State, I:I18n): NodeSeq = 
         st match {
            case Channel.State.OFF => TXT( I.tr("Inactive (off)"))
            case Channel.State.STARTING => TXT( I.tr("Connecting..."))
            case Channel.State.RUNNING => 
                <span>{ I.tr("Active (ok)") }<img class="state" src="../aprsd/images/ok.png"/></span>  
            case Channel.State.FAILED => 
                <span>{ I.tr("Inactive (failed)") }<img class="state" src="../aprsd/images/fail.png"/></span>
         }
         ;
         
      /* Set to true if value of one or more fields has changed */
      var changed = false;
         
               
      /**
       * Get value of a given field from HTTP request parameters.
       * Check if format of input is correct and if field value has changed. 
       */
      protected def _getField(req : Request, value: String, propname: String, pattern: String, 
          isnum: Boolean, min: Int, max: Int): NodeSeq = 
      {          
          val xold = getApi.getProperty(propname,"")
          var x = value;
          
          
          def checkNum(x: String): Boolean = 
             if (isnum) {
                val xx = x.toInt
                xx >= min && xx <= max
             }
             else true
             ;
             
             
          x = if (x == null || (pattern.equals(BOOLEAN) && x.equals(""))) "false" 
              else x
              
          if (("".equals(x) && !"".equals(xold)) || (x != null && x.matches(pattern) && checkNum(x))) 
               /* Test om vi har endret verdi på et felt */
               if (!x.equals(xold)) {
                   changed = true
                   getApi.getConfig().setProperty(propname, x)
                    <span class="fieldsuccess">Field <b>{propname}</b> = '{x}'. Changed. <br/> </span>
               }               
               else <span>Field <b>{propname}</b>. Unchanged.<br/></span>          
          else if (x != null && !"".equals(x))
             <span class="fielderror">Field <b>{propname}</b>. Value out of range or format error. Value = '{x}'<br/></span>
          else <span></span>
      }
    
    
      
      protected def getField(req : Request, id: String, propname: String, pattern: String): NodeSeq =
          _getField(req, req.queryParams(id), propname, pattern, false, 0,0)
          ;
          
          
      protected def getField(req : Request, id1: String, id2: String, propname: String, pattern: String): NodeSeq =
          _getField(req, req.queryParams(id1)+req.queryParams(id2), propname, pattern, false, 0,0)
          ;
   
   
      protected def getUtmField(req : Request, id1: String, id2: String, id3: String, id4: String, propname: String, pattern: String): NodeSeq =
          _getField(req, req.queryParams(id1)+req.queryParams(id2)+" "+req.queryParams(id3)+" "+req.queryParams(id4), 
                    propname, pattern, false, 0,0)
          ;
          
          
      protected def getField(req : Request, id: String, propname: String, min: Int, max: Int): NodeSeq = 
          _getField(req, req.queryParams(id), propname, NUMBER, true, min, max)
          ; 
           
      
      
  }

}
