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
import java.text.*;
import java.util.*;
import java.io.*;



/*
 * Sample and log some data to a CSV file each hour. 
 */
public class StatLogger implements Runnable {

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
           Thread t = new Thread(this, "StatLogger");
           t.start(); 
       }
       catch (Exception e) { _api.log().error("StatLogger", ""+e); }
    }
    
    
    
    public void run()
    {
        try {
           _api.log().debug("StatLogger", "Starting statistics logger");
           _out.println("time, hits, visits, clients, pos-updates, map-updates");
           _out.flush();
                       
           ServerAPI.Web ws = _api.getWebserver();        
           long period = 1000 * 60 * 60;           // 60 minute
           long _posUpd = TrackerPoint.getPosUpdates();
           long _aprsPosUpd = TrackerPoint.getAprsPosUpdates();
           long _req = ws.nHttpReq(); 
           long _visits = ws.nVisits();  
           long _mupdates = ws.nMapUpdates();
           
           while(true) { 
               Thread.sleep(period); 
               _out.println(df.format(new Date()) + "," + (ws.nHttpReq() - _req) + "," + 
                    (ws.nVisits() - _visits) + "," + ws.nClients() + "," + (TrackerPoint.getPosUpdates() - _posUpd) + "," +
                    (TrackerPoint.getAprsPosUpdates() - _aprsPosUpd) + "," +
                    (ws.nMapUpdates() - _mupdates) ); 
               _req = ws.nHttpReq();
               _visits = ws.nVisits();
               _mupdates = ws.nMapUpdates();
               _posUpd = TrackerPoint.getPosUpdates();
               _aprsPosUpd = TrackerPoint.getAprsPosUpdates();
               _out.flush();
           }
        }
        catch (Exception e)
            { _api.log().error("StatLogger", "Thread: "+e); 
               e.printStackTrace(System.out); }                       
    }   
}

