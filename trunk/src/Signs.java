package no.polaric.aprsd;
import java.net.*;
import java.util.*;
import java.io.*;
import uk.me.jstott.jcoord.*;


public class Signs 
{
    private static Signs _signs = new Signs("signs");
    
    public static class Item {
        public Reference pos;
        public String icon;
        public String text;
        public Item (Reference r, String ic, String txt)
          { pos = r; icon = ic; text = txt; }
    }
    
    private BufferedReader  _rd;
    private StringTokenizer _next;
    private List<Item> _list = new ArrayList();
  
    
    protected Signs(String file) 
    {
        try {
           _rd = new BufferedReader(new FileReader(file));
           while (_rd.ready())
           {
               String line = _rd.readLine();
               if (!line.startsWith("#")) 
               {                 
                   String[] x = line.split(",\\s*");  
                   if (x.length < 5)
                       continue;
                   if (!x[0].matches("[0-9]{2}[a-zA-Z]") || 
                       !x[1].matches("[0-9]{6}") || !x[2].matches("[0-9]{7}"))
                       continue;    
                   double easting = Long.parseLong(x[1]);
                   double northing = Long.parseLong(x[2]);
                   Reference pos = new UTMRef( Integer.parseInt( x[0].substring(0,2)), 
                                               x[0].charAt(2),
                                               easting, northing);
                   Item it = new Item(pos, x[3], x[4]);
                   _list.add(it);
               }
            }     
        }
        catch (Exception e) 
            { System.out.println("SIGNLIST WARNING: "+e); }
    } 

    public static List<Item> getList() { return _signs._list; }
}
