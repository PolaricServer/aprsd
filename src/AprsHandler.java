
package no.polaric.aprsd;
import no.polaric.aprsd.*;
import java.util.*;
import uk.me.jstott.jcoord.*;

/**
 * Handle APRS packets and reports. To be implemented by
 * default handler and/or plugins. 
 */
 
public interface AprsHandler
{
    public void handlePosReport(String sender, Date ts, Reference newpos, int crs, int sp, int alt, 
            String descr, char sym, char altsym, String pathinfo);
    
    public void handleStatus(Date ts, String msg, String pathinfo);
                                   
    public void handleMessage(Date ts, String src, String dest, String msg);
    
    public void handlePacket(Date ts, String src, String dest, String path, String txt);


    /**
     * Dummy handler class. Does nothing. 
     */
    public class Dummy implements AprsHandler 
    {
       public void handlePosReport(String sender, Date ts, Reference newpos, int crs, int sp, int alt, 
               String descr, char sym, char altsym, String pathinfo) {};
       public void handleStatus(Date ts, String msg, String pathinfo) {};                               
       public void handleMessage(Date ts, String src, String dest, String msg) {}
       public void handlePacket(Date ts, String src, String dest, String path, String txt) {}
    }     
}
 
