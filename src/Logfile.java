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
import java.text.*;



public class Logfile
{
    public enum Level {
       DEBUG, INFO, WARNING, ERROR, NONE
    }

    private PrintWriter _out; 
    private boolean _log = false;
    private Level _level = Level.NONE;
    
    private DateFormat df = new SimpleDateFormat("MMM dd HH:mm:ss",
       new DateFormatSymbols(new Locale("no")));
    
        
        
    private void init(ServerAPI api, String configname, OutputStream logfile) 
    {     
       try {
          _log = api.getBoolProperty(configname + ".log.on", true);
          int lv = api.getIntProperty(configname + ".log.level", 1);
          if (lv > 4 || lv < 0) lv=4;
          _level = Level.values()[lv];
          if (_log) 
             _out = new PrintWriter(logfile);
       }
       catch (Exception e) { 
           System.out.println("ERROR (Logfile): "+e); 
           e.printStackTrace(System.out);
           _log = false; 
       }
    } 
    
    
    protected Logfile(ServerAPI api)
       { init(api, "aprsd", System.out); }
    

    
    public Logfile(ServerAPI api, String configname, String logfile) 
    {     
       try {
         init(api, configname, 
            new FileOutputStream(  
              System.getProperties().getProperty("logdir", ".")+"/"+logfile , true));
       }
       catch (Exception e) { System.out.println("ERROR (Logfile): "+e); 
                             _log = false; }
    } 


   public void error(String cls, String text)
      { log(Level.ERROR, cls, text);}
      
   
   public void warn(String cls, String text)
      { log(Level.WARNING, cls, text);}    
   
   
   public void info(String cls, String text)
      { log(Level.INFO, cls, text);}
      
      
   public void debug(String cls, String text)
      { log(Level.DEBUG, cls, text);}
      
      
   public void log(String cls, String text)
      { log(Level.NONE, cls, text);}
   
   
   public void log(String text)
      { log(Level.NONE, null, text);}
      
      
   public void log(Level lvl, String cls, String text)
   {
      if (lvl.ordinal() < _level.ordinal())
         return;
      String prefix = (lvl == Level.NONE ? "" : ""+lvl); 
      if (cls != null)
         prefix += " ("+cls+")"; 
      _log(prefix + ": "+text);
   }
   
    
   public void _log(String text)
   {
       if (_log) {
         _out.println(df.format(new Date()) +" "+ text);
         _out.flush();
       }
   }
   
   
   
   public void add(String text)
   {
       if (_log) {
         _out.println(text);
         _out.flush(); 
       }
   }
   
}
