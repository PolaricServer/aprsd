/*
 * Copyright (C) 2016 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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

package no.polaric.aprsd;
import java.net.*;
import java.util.*;
import java.io.*;
import uk.me.jstott.jcoord.*;


public class Signs extends Source
{
    /*
     * There is only one instance of this class. In future versions we should 
     * consider allowing multiple instances with different access permissions. 
     */
    private static Signs _signs;
    
    public static void init(ServerAPI api) 
    { 
       _signs = 
          new Signs(api, System.getProperties().getProperty("confdir", ".")+"/signs");  
    }
    
    
    
    /**
     * Interface for searching in a database external to this class. 
     */
    public interface ExtDb {
        public Iterable<Item> search(long scale, Reference uleft, Reference lright);
        public void close(); 
    }
    
    
     /**
     * Sign item. A pointobject plus scale and url.
     */
    public static class Item extends PointObject {
        protected int _id;
        protected long _maxScale;
        protected String _url;
        
        public boolean visible(long scale)
          { return scale <= _maxScale; }
          
        public int getId() 
          { return _id; }
          
        public String getIdent()
          { return ""+_id; }
          
        public Source getSource()
          { return null; }
       
        public long getScale()
          { return _maxScale; }
          
        public String getUrl()
          { return _url; }
        
        public Item (int i, Reference r, long sc, String ic, String url, String txt)
          { super(r);  _id = i; _maxScale = sc; _icon = ic; _url = url; _description = txt; 
            if (_url.matches("-|null|none")) 
                 _url = null; }   
    }
    
    
    private BufferedReader  _rd;
    private StringTokenizer _next;
    private List<Item> _list = new ArrayList();
    private ExtDb _extdb = null;
    
    
    /* Source methods 
     * Note that as a source this is mostly a dummy.
     */
     public String getShortDescr() 
         { return "SI0"; }
    
    
    /**
     * Read signs file.
     * Format of each line: 
     * UTM-zone, UTM-easting, UTM-northing, max-scale, icon-filename, URL, description.
     * Example:  
     *    33W, 123456, 1234567, 30000, symbol.gif, http://mysite.net, This is my site 
     */  
    protected Signs(ServerAPI api, String file) 
    {
        _init (api, "Signs", false, null);
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
                   if (x.length > 7)
                     x[6] = x[6] + " "+ x[7];
                   Item it = new Item(-1, pos, scale, x[4], x[5], x[6]);
                   _list.add(it);
               }
            }     
        }
        catch (FileNotFoundException  e) 
            { _api.log().info("Signs", "No signs file present."); }
        catch (Exception  e) 
            { _api.log().warn("Signs", ""+e); }    
    }     
    
    
    public static void setExtDb(ExtDb x)
       { _signs._extdb = x; }
       
       
    
    public static synchronized Iterable<Item>
          search(long scale, Reference uleft, Reference lright)
    {
         LinkedList<Item> result = new LinkedList();
         if (_signs == null)
            return result; 
         if (uleft==null || lright==null)
            return _signs._list;
          
         for (Item s: _signs._list)
            if (s.visible(scale) && s.isInside(uleft, lright, 0.1, 0.1))
                result.add(s);
        
         if (_signs._extdb != null) {
            /* Copy items from database resultset. This may be a little
             * inefficient, so consider to support returning the resultset directly. 
             * However, this allows us to mix it with signs from static file rather easily.
             */
            for (Item s: _signs._extdb.search(scale, uleft, lright)) 
               result.add(s);
            
            _signs._extdb.close();   
         }
         return result;
    }

    
    public static List<Item> getList() 
        { return _signs._list; }
    

}
