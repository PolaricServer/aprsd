
package no.polaric.aprsd;
import no.polaric.aprsd.*;
import java.util.*;
import uk.me.jstott.jcoord.*;


public interface Database 
{
    public void addPosReport(String sender, Date ts, Reference newpos, int crs, int sp, int alt, 
            String descr, char sym, char altsym, String pathinfo);
    
    public void addStatus(Date ts, String msg, String pathinfo);
                                   
    /* Perhaps pathinfo could be added in a separate method and put in 
     * as separate database table */
}
 
