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
import java.util.*;
import java.io.*;
import uk.me.jstott.jcoord.*; 
import java.util.concurrent.*;
 
/**
 * SAR key and SAR URL.
 * Generate keys that can be used to authenticate and authorize (limited) access
 * temporarly, to the system. Keys are saved to a file as URL's to allow a web 
 * server frontend to do authorization plus mapping to an url for a specific area. 
 */
 
public final class SarUrl implements Runnable
{
    private String             _file, _proto;
    private boolean            _hasChanged = false; 
    private List<Item>         _mapping = new ArrayList<>();
    private Item               _lastItem;
    private static final long  _timeLimit = 1000  * 60 * 60  * 25;
    private static final long  _cacheTime = 1000  * 60 * 60 * 2;
    private ServerAPI          _api;


    private static final class Item {
        String key, target; 
        Date time;
        public Item(String k, String tg, Date t)
         { key=k; target=tg; time=t; }
    }
    
    
    public SarUrl(ServerAPI api)
    {
        _api = api;
        _file = api.getProperty("sarurl.file", "sarurl.txt");
        _proto = api.getProperty("sarurl.protocol", "https");
        if (_file.charAt(0) != '/')
           _file =  System.getProperties().getProperty("datadir", ".") + "/" + _file;
           
        Thread t = new Thread(this, "SarUrlWriter");
        t.start(); 
    }

    
    public boolean validKey(String key)
    {
        for (Item it: _mapping)
           if (it.key.equals(key))
              return true;
        return false;
    }



    public static String getKey(String url)
    {
       String[] s = url.split("sar-");
       return s[1];
    }
    

    
    public String create(String target)
    {
       Date now = new Date(); 
       String host = target.replaceFirst("http(s?)://", ""); 
       if (host.indexOf('/') == -1)
          host = "localhost";
       else
          host = host.substring(0, host.indexOf('/'));
       target = target.replaceFirst("http(s?)://[a-zA-Z0-9\\.\\-]+/", "");
       
       // Consider sorting the list on time
       for (Item it: _mapping) {
           if (now.getTime() < it.time.getTime() + _cacheTime && it.target.equals(target))
              return _proto + "://"+host+"/sar-"+ it.key;
       }
       try {
           /* A 24 bit key in hexadecimal */
           String key = SecUtils.b2hex((SecUtils.getRandom(3)));
           synchronized(this) {
               _lastItem =  new Item(key, target, now);
               _mapping.add( _lastItem );  
           }
           saveMap();
       } 
       catch (IOException e)
            {return null;}
       return _proto + "://"+host+"/sar-"+_lastItem.key;
    }
    
    
    
    
    private synchronized void saveMap()
        throws IOException
    {
       if (_mapping.isEmpty()) 
           return;
       PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(_file, false)));
       Iterator<Item> it = _mapping.iterator();
       while (it.hasNext())
       {
           Item item = it.next();
           if (item.time.getTime() + _timeLimit > (new Date()).getTime())
               out.println(item.key + "  "+item.target);
           else
               it.remove();
       }
       out.close();
    }
    
    
    /**
     * Thread to periodically remove old keys and checkpoint mapping 
     * data to file. 
     */   
    public void run()
    {
        long period = 1000 * 60 * 60; // 1 hour
 
        while(true) {
           try {
              while (true) 
              {
                  Thread.sleep(period); 
                  saveMap();
              }
           }
           catch (Exception e)
               { _api.log().error("SarUrl", "Writer thread: "+e); 
                  e.printStackTrace(System.out); }                  
        }  
    }   

}
