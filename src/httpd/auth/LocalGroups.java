  
/* 
 * Copyright (C) 2021- by Øyvind Hanssen (ohanssen@acm.org)
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
import java.util.*; 
import no.polaric.aprsd.*;
import java.io.*;



public class LocalGroups implements GroupDb
{
    private Map<String, Group> _map = new HashMap();

    public Group get(String gid) { 
        return _map.get(gid);
    }
        
    public Collection<Group> getAll()
        { return _map.values(); }

        
    public LocalGroups(ServerAPI api, String file) 
    {
        try {
            _map.put("DEFAULT", Group.DEFAULT);
            BufferedReader rd = new BufferedReader(new FileReader(file));
            while (rd.ready())
            {
                String line = rd.readLine();
                if (!line.startsWith("#")) 
                {               
                    String[] x = line.split(",");  
                    if (x.length < 4) 
                        continue;
                       
                    String gid = x[0].trim();
                    String name = x[1].trim();   
                    String tags = x[2].trim();
                    boolean sar = ("true".equals(x[3].trim()));
                    Group g = new Group(gid, name, tags, sar);
                    _map.put(gid, g);
                }
            }     
        }
        catch (FileNotFoundException  e) 
            { api.log().error("LocalGroups", "No groups file present."); }
        catch (Exception  e) 
            { api.log().error("LocalGroups", ""+e); }        
    }
    
}

