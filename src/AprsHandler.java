
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
       public int ambiguity  = 0; 
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


    public void handlePosReport(Source s, String sender, Date ts, PosData newpos,  
            String descr, String pathinfo);
    
    public void handleStatus(Source s, Date ts, String msg);
                                   
    public void handleMessage(Source s, Date ts, String src, String dest, String msg);
    
    public void handlePacket(Source s, Date ts, String src, String dest, String path, String txt);


    /**
     * Dummy Aprs handler class. Does nothing. 
     */
    public class Dummy implements AprsHandler 
    {
       public void handlePosReport(Source s, String sender, Date ts, PosData newpos,  
               String descr, String pathinfo) {};
       public void handleStatus(Source s, Date ts, String msg) {};                               
       public void handleMessage(Source s, Date ts, String src, String dest, String msg) {}
       public void handlePacket(Source s, Date ts, String src, String dest, String path, String txt) {}
    }     
}
 
