/* 
 * Copyright (C) 2016-2025 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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

package no.polaric.aprsd;
import no.polaric.core.*;
import java.net.*;
import java.util.*;
import java.io.*;

/**
 * Colour alternatives for trails.
 */
public class ColourTable 
{
    BufferedReader  _rd;
    StringTokenizer _next;
    List<String> list = new ArrayList<String>();
    Iterator<String> it = null;
    
    
    public ColourTable(ServerConfig api, String file) 
    {
        try {
           _rd = new BufferedReader(new FileReader(file));
           while (_rd.ready())
           {
               String line = _rd.readLine();     
               if (line.matches("([0-9a-fA-F]{6}) ([0-9a-fA-F]{6})"))           
                  list.add(line); 
            }   
           it = list.iterator(); 
        }
        catch (Exception e) { 
           api.log().error("ColourTable", ""+e); }
    } 



    public String[] nextColour()
    {
         if (list.isEmpty()) 
             return new String[] {"000000","ff0000"}; 
         if (! it.hasNext()) 
             it = list.iterator();
         String x = it.next();
         if (x==null)
            return new String[] {"000000","ff0000"}; 
         else
            return x.split(" ");
    }
}
