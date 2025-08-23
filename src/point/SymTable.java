/*
 * Copyright (C) 2016-2023 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 */

package no.polaric.aprsd.point;
import no.arctic.core.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import java.io.*;

/**
 * Symbol to icon mapping.
 */
public class SymTable 
{
    private BufferedReader  _rd;
    private StringTokenizer _next;
    private Map<String, String> _stab = new HashMap<String,String>(); 
    private ArrayList<Exp> _rtab = new ArrayList<Exp>();
    
    protected class Exp {
        public String exp; 
        public String file; 
        public Exp(String e, String f) {
           exp=e; 
           file=f; 
        }
    }
    
    
    public SymTable(ServerAPI api, String file) 
    {
        try {
           _rd = new BufferedReader(new FileReader(file));
           while (_rd.ready())
           {
               String line = _rd.readLine();
               if (!line.startsWith("#")) 
               {               
                   String[] x = line.split("\\s+");  
                   if (x.length < 2) 
                       continue;
                   _rtab.add(new Exp(x[0], x[1]));    
               }
            }     
        }
        catch (FileNotFoundException  e) 
            { api.log().error("SymTable", "No symbols file present."); }
        catch (Exception  e) 
            { api.log().error("SymTable", ""+e); }        
    } 



    public String getIcon(char sym, char alt)
    {
         String key = "" + alt + sym;
         String icon = _stab.get(key);
         if (icon==null)
            for (Exp e: _rtab)
               if (key.matches(e.exp)) {
                   _stab.put(key, e.file);
                   return e.file; 
               }
         return (icon==null ? null: icon);         
    }
}
