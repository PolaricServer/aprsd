
import scala.xml._
import org.simpleframework.transport.connect.Connection
import org.simpleframework.transport.connect.SocketConnection
import org.simpleframework.http._


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
            <link href={_wfiledir+"/style.css"} rel="stylesheet" type="text/css" />
            </head>
            <body>
            {content} 
            </body>
            </html>
       
       else
           <xml:group> { content } </xml:group>


       protected def simpleLabel(id:String, cls:String, lbl:String, content: NodeSeq): NodeSeq =
           <label for={id} class={cls}>{lbl}</label>
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
  

  }

}
