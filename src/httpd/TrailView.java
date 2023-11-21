
package no.polaric.aprsd.http;
import no.polaric.aprsd.*;
import java.io.*;

 
public class TrailView
{
    private boolean _first = true;
    private int n = 0;
    private LatLng _itx;  
    private String t = "00000000000000";
    private String _pre, _post; 
    private PrintWriter _out;    
       

    public TrailView(PrintWriter out, String pre, String post, LatLng firstpos)
    {
       _pre = pre; _post = post; 
       _itx = (firstpos == null ? null :  firstpos);
       _out = out; 
    }
    
    
    
    public void addPoint(TPoint p)
    {
        if (n==0) 
           _out.println( _pre );     
  
        if (_itx != null) {       
           if (!_first) 
              _out.print(", "); 
           else
              _first = false;   
           _out.println( ServerBase.roundDeg(_itx.getLng())+ " " + ServerBase.roundDeg(_itx.getLat() ) +
               " " + t);           
        }

        if (n++ > 100) {
           n = 0;
           _out.println(_post);
        }
        else {
           _itx = p.getPosition();
           if (p.getTS() == null) 
              t = "0";
           else
              t = ServerBase.xf.format(p.getTS());
       }
    }
    
                 
    public void close() 
    {
        if (_itx != null)
            _out.println(", "+ ServerBase.roundDeg(_itx.getLng())+ " " + ServerBase.roundDeg(_itx.getLat()) +
                         " "+t);  // FIXME: get first time
        if (n > 0) 
           _out.println(_post);
    }
}


