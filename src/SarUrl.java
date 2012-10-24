/* 
 * Copyright (C) 2011 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
 * SAR URL
 */
public class SarUrl implements Runnable
{
    private String     _file, _proto;
    private boolean   _hasChanged = false; 
    private List<Item> _mapping = new ArrayList();
    private long _timeLimit = 1000  * 60 * 60  * 24;
    
    
    private static class Item {
        String key, target; 
        Date time;
        public Item(String k, String tg, Date t)
         { key=k; target=tg; time=t; }
    }
    
    
    public SarUrl(Properties config)
    {
        _file = config.getProperty("sarurl.file", "sarurl.txt");
        _proto = config.getProperty("sarurl.protocol", "https");
        if (_file.charAt(0) != '/')
           _file =  System.getProperties().getProperty("datadir", ".") + "/" + _file;
           
        Thread t = new Thread(this, "SarUrlWriter");
        t.start(); 
    }
    
    
    public static String getKey(String url)
    {
       String[] s = url.split("sar-");
       return s[1];
    }
    
    
    public String create(String target)
    {
       try {
          /* A 24 bit key in hexadecimal */
          String key = SecUtils.b2hex((SecUtils.getKey(3)));
          String host = target.replaceFirst("http(s?)://", ""); 
          if (host.indexOf('/') == -1)
              host = "localhost";
          else
              host = host.substring(0, host.indexOf('/'));
          target = target.replaceFirst("http(s?)://[a-zA-Z0-9\\.\\-]+/", "");
          synchronized(this) {
             _mapping.add( new Item(key, target, new Date()) );  
          }
          saveMap();
          return _proto + "://"+host+"/sar-"+key; 
       } 
       catch (IOException e)
        {return null;}
    }
    
       
    
    private synchronized void saveMap()
        throws IOException
    {
       if (_mapping.isEmpty()) 
           return;
       System.out.println("*** SarUrl: Save/garbagecollect mappings");
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
     * Thread to periodically checkpoint mapping data to file. 
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
               { System.out.println("*** SarUrl writer thread: "+e); e.printStackTrace(System.out);}                  
        }  
    }   

}
