/* 
 * Copyright (C) 2015 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import java.text.*;
import java.util.*;
import java.io.*;



/*
 * Sample and log some data to a CSV file each minute. 
 */
public class StatLogger extends Thread {

    private DateFormat df = new SimpleDateFormat("dd MMM yyyy HH:mm:ss",
       new DateFormatSymbols(new Locale("no")));
    
    private PrintWriter _out; 
    private ServerAPI   _api;
    
    
    
    public StatLogger(ServerAPI api, String configname, String logfile) 
    {     
       try {
           String f = System.getProperties().getProperty("logdir", ".")+"/"+logfile;
           _out = new PrintWriter(new FileOutputStream(f, true));
           _api = api;
           start();
       }
       catch (Exception e) { System.out.println("STATS LOGFILE: "+e); }
    }
    
    
    public void run()
    {
        System.out.println("*** Starting statistics logger");
        _out.println("time, hits, client-threads, pos-updates");
        long period = 1000 * 60;           // 1 minute
        long _posUpd = TrackerPoint.getPosUpdates();
        long _req = _api.getHttps().getReq(); 
        long _clients; 
        
        while(true) {
           try {
                Thread.sleep(period); 
                ServerAPI.ServerStats st = _api.getHttps();
                _out.println(df.format(new Date()) + "," + (st.getReq() - _req) + "," + st.getClients() + "," + 
                                  (TrackerPoint.getPosUpdates() - _posUpd)); 
                _req = st.getReq();
                _posUpd = TrackerPoint.getPosUpdates();
                _out.flush();

           }
           catch (Exception e)
               { System.out.println("*** StatLogger thread: "+e); e.printStackTrace(System.out);}                  
        }  
    }
    
}

