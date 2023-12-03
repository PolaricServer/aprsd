/* 
 * Copyright (C) 2023 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
 
package no.polaric.aprsd.http;
import no.polaric.aprsd.*;
import java.text.*;
import java.util.*;
import java.io.*;



/*
 * Sample and log some data to a file. 
 */
public class LStatLogger implements Runnable {

    private DateFormat df = new SimpleDateFormat("dd MMM yyyy HH:mm:ss",
       new DateFormatSymbols(new Locale("no")));
    
    private PrintWriter _out; 
    private ServerAPI   _api;
    
    private Map<String, Long> _data = new HashMap<String, Long>();
    
    
    
    public void count(String layer) {
        if (layer==null)
            return;
        if (_data.get(layer) == null)
            _data.put(layer, (long) 1);
        else {
            Long cnt = _data.get(layer);
            cnt++;
            _data.replace(layer, cnt);
        }
    }
    
    
    
    
    
    public LStatLogger(ServerAPI api, String configname, String logfile) 
    {     
       try {
           String f = System.getProperties().getProperty("logdir", ".")+"/"+logfile;
           _out = new PrintWriter(new FileOutputStream(f, true));
           _api = api;
           Thread t = new Thread(this, "LStatLogger");
           t.start(); 
       }
       catch (Exception e) { _api.log().error("LStatLogger", ""+e); }
    }
    
    
    
    public void run()
    {
        try {
           _api.log().debug("LStatLogger", "Starting layer-usage statistics logger");
           _out.flush();
           long period = 1000 * 60 * 60 * 6;           // 6 hours

           while(true) { 
               Thread.sleep(period); 
               _out.println( df.format(new Date()) + ":" ); 
                var elements = _data.entrySet();
                for (Map.Entry<String, Long> x : elements)
                    _out.println("   "+x.getKey()+": "+x.getValue());
               _out.println("=====================================");
               _data.clear();
               _out.flush();
           }
        }
        catch (Exception e)
            { _api.log().error("StatLogger", "Thread: "+e); 
               e.printStackTrace(System.out); }                       
    }   
}

