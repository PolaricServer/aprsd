package no.polaric.aprsd;
import java.net.*;
import java.util.*;
import java.io.*;
import uk.me.jstott.jcoord.*;


public class Signs 
{
    private static Signs _signs = 
       new Signs(System.getProperties().getProperty("confdir", ".")+"/signs");
    
    
    public static class Item extends PointObject {
        public long _maxScale;
        public String _url;
        
        public boolean visible(long scale)
          { return scale <= _maxScale; }
          
        public String getUrl()
          { return _url; }
        
        public Item (Reference r, long sc, String ic, String url, String txt)
          { super(r);  _maxScale = sc; _icon = ic; _url = url; _description = txt; 
            if (_url.matches("-|null|none")) 
                 _url = null; }   
    }
    
    
    private BufferedReader  _rd;
    private StringTokenizer _next;
    private List<Item> _list = new ArrayList();
    
    
    /**
     * Read signs file.
     * Format of each line: 
     * UTM-zone, UTM-easting, UTM-northing, max-scale, icon-filename, URL, description.
     * Example:  
     *    33W, 123456, 1234567, 30000, symbol.gif, http://mysite.net, This is my site 
     */  
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
                   if (x.length < 7)
                       continue;
                   if (!x[0].matches("[0-9]{2}[a-zA-Z]") || 
                       !x[1].matches("[0-9]{6}") || 
                       !x[2].matches("[0-9]{7}") ||
                       !x[3].matches("[0-9]+"))
                       continue;    
                   double easting = Long.parseLong(x[1]);
                   double northing = Long.parseLong(x[2]);
                   long scale = Long.parseLong(x[3]);
                   Reference pos = new UTMRef( Integer.parseInt( x[0].substring(0,2)), 
                                               x[0].charAt(2),
                                               easting, northing);
                   Item it = new Item(pos, scale, x[4], x[5], x[6]);
                   _list.add(it);
               }
            }     
        }
        catch (Exception e) 
            { System.out.println("SIGNLIST WARNING: "+e); }
    }     
    
    
    
    public static List<Item>
          search(UTMRef uleft, UTMRef lright)
    {
         if (uleft==null || lright==null)
            return _signs._list;
            
         LinkedList<Item> result = new LinkedList();
         for (Item s: _signs._list)
            if (s.isInside(uleft, lright))
                result.add(s);
        return result;
    }

    public static List<Item> getList() { return _signs._list; }
}
