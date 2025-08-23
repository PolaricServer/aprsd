/*
 * Copyright (C) 2016-2023 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import no.polaric.aprsd.point.*;
import java.net.*;
import java.util.*;
import java.io.*;



public class Signs extends Source
{
    /*
     * There is only one instance of this class. In future versions we should 
     * consider allowing multiple instances with different access permissions. 
     */
    private static Signs _signs;
    
    public static void init(AprsServerAPI api) 
    { 
       _signs = 
          new Signs(api, System.getProperties().getProperty("confdir", ".")+"/signs");  
    }
    
    
    
    /**
     * Interface for searching in a database external to this class. 
     */
    public interface ExtDb {
        public Iterable<Item> search(String uid, String group, long scale, LatLng uleft, LatLng lright);
        public void close(); 
    }
    
    
    /**
     * Sign item. A pointobject plus scale and url.
     */
    public static class Item extends PointObject {
        protected String _id;
        protected long _maxScale;
        protected String _url;
        protected String _type = "sign";
        
        public boolean visible(long scale)
          { return scale <= _maxScale; }
          
        public String getId() 
          { return _id; }
          
        public String getIdent()
          { return _id.matches(".*@(local|datex)") ? "__loc."+(_id) : "__db."+_id; }
          
        public Source getSource()
          { return null; }
       
        public long getScale()
          { return _maxScale; }
          
        public String getUrl()
          { return _url; }
        
        public void setType(String t) {
          _type = t;
        }
        
        public String getType() {
          return _type;
        }
        
        public String getUser() {
          return null;
        }
        
        
        public Item (String i, LatLng r, long sc, String ic, String url, String txt)
          { super(r);  _id = i; _maxScale = sc; _icon = ic; _url = url; _description = txt; 
            if (_url==null || _url.matches("-|null|none")) 
                 _url = null; }   
    }
    
    
    private BufferedReader  _rd;
    private StringTokenizer _next;
    private List<Item> _list = new ArrayList<Item>();
    private Map<String, Item> _list2 = new HashMap<String, Item>();
    
    private ExtDb _extdb = null;
    private int localId = -1;
    
    
    /* Source methods 
     * Note that as a source this is mostly a dummy.
     */
     public String getShortDescr() 
         { return "SI0"; }
    
    
    /**
     * Read signs file.
     * Format of each line: 
     * lat, long, max-scale, icon-filename, URL, description.
     */  
    protected Signs(AprsServerAPI api, String file) 
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
                    if (!x[0].matches("[0-9\\.]+") || 
                        !x[1].matches("[0-9\\.]+") ||
                        !x[2].matches("[0-9]+"))
                        continue;    
                        
                    double lat = Double.parseDouble(x[0]);
                    double lng = Double.parseDouble(x[1]);
                    long scale = Long.parseLong(x[2]);
                    LatLng pos = new LatLng(lat, lng);
                    if (x.length > 6)
                       x[5] = x[5] + " "+ x[6];
                     
                     /* NOTE: signs from local file get suffix @local */
                    Item it = new Item(""+(localId++)+"@local", pos, scale, x[3], x[4], x[5]);
                    if (it.getUrl() != null && it.getUrl().charAt(0) == 'P')
                        it.setType("picture");
                    _list.add(it);
                }
            }     
        }
        catch (FileNotFoundException  e) 
            { _api.log().info("Signs", "No signs file present."); }
        catch (Exception  e) 
            { _api.log().warn("Signs", ""+e); }    
    }     
    
    
    public static void clear() {
       _signs._list2.clear();
    }
    
    
    public static void add(Item it) {
       _signs._list2.put(it.getId(), it);
    }
    
    
    public static void setExtDb(ExtDb x)
       { _signs._extdb = x; }
       
       
    
    public static synchronized Iterable<Item>
          search(String uid, String group, long scale, LatLng uleft, LatLng lright)
    {
         LinkedList<Item> result = new LinkedList<Item>();
         if (_signs == null)
            return result; 
         if (uleft==null || lright==null)
            return _signs._list;
          
         for (Item s: _signs._list)
            if (s.visible(scale) && s.isInside(uleft, lright, 0.1, 0.1))
                result.add(s);
         for (Item s: _signs._list2.values())
            if (s.visible(scale) && s.isInside(uleft, lright, 0.1, 0.1))
                result.add(s);
         
         if (_signs._extdb != null) {
            /* Copy items from database resultset. This may be a little
             * inefficient, so consider to support returning the resultset directly. 
             * However, this allows us to mix it with signs from static file rather easily.
             */
            for (Item s: _signs._extdb.search(uid, group, scale, uleft, lright)) 
               result.add(s);
            _signs._extdb.close();   
         }
         return result;
    }

    
    public static List<Item> getList() 
        { return _signs._list; }
    

}
