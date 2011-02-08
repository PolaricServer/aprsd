
package no.polaric.aprsd;
import no.polaric.aprsd.*;
import java.util.*;
import uk.me.jstott.jcoord.*;

/* FIXME: Logger interface? Default impl: Log to file or dummy
 * And/Or superclass of StationDB? */
 
public interface AprsLog
{
    public void logPosReport(String sender, Date ts, Reference newpos, int crs, int sp, int alt, 
            String descr, char sym, char altsym, String pathinfo);
    
    public void logStatus(Date ts, String msg, String pathinfo);
                                   
    public void logMessage(Date ts, String src, String dest, String msg);
    
    public void logAprsPacket(Date ts, String src, String dest, String path, String txt);


    public class Dummy implements AprsLog 
    {
       public void logPosReport(String sender, Date ts, Reference newpos, int crs, int sp, int alt, 
               String descr, char sym, char altsym, String pathinfo) {};
       public void logStatus(Date ts, String msg, String pathinfo) {};                               
       public void logMessage(Date ts, String src, String dest, String msg) {}
       public void logAprsPacket(Date ts, String src, String dest, String path, String txt) {}
    }     
}
 
