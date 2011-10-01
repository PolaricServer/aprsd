
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

    public static class PosData {
       public Reference pos;
       public int course = -1;
       public int speed = -1;
       public char symbol, symtab;
       public long altitude = -1; 
       public PosData () {}
       public PosData (Reference p, char sym, char stab)
          {pos=p; symbol=sym; symtab=stab; }
       public PosData (Reference p, int crs, int sp, char sym, char stab)
          {pos=p; course=crs; speed=sp; symbol=sym; symtab=stab; }   
    }


    public void handlePosReport(String sender, Date ts, PosData newpos,  
            String descr, String pathinfo);
    
    public void handleStatus(Date ts, String msg, String pathinfo);
                                   
    public void handleMessage(Date ts, String src, String dest, String msg);
    
    public void handlePacket(Date ts, String src, String dest, String path, String txt);


    /**
     * Dummy handler class. Does nothing. 
     */
    public class Dummy implements AprsHandler 
    {
       public void handlePosReport(String sender, Date ts, PosData newpos,  
               String descr, String pathinfo) {};
       public void handleStatus(Date ts, String msg, String pathinfo) {};                               
       public void handleMessage(Date ts, String src, String dest, String msg) {}
       public void handlePacket(Date ts, String src, String dest, String path, String txt) {}
    }     
}
 
