/* 
 * Copyright (C) 2025 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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

package no.polaric.aprsd.channel;
import no.polaric.aprsd.*;
import java.util.*;
import java.io.*;


/**
 * Stored filters - filters that can be read from a file and looked up by name.
 */
public class StoredFilter 
{
    private AprsServerConfig _conf;

    /**
     * A stored filter is actually combined filter (from AprsFilter class)
     */
    public class Filt extends AprsFilter.Combined
    {
        public Filt(String fspec, String userid) {
            super(fspec, userid);
        }
    }
    
    
    private Map<String, Filt> _filtmap; 
    
    
    /**
     * Initialize the map of filter reading and parsing filter specs from a file.
     * A filter spec is as described in AprsFilter.java, Each filter is stored in the
     * _filtmap so that it can quickly be looked up by name. If there is a syntax error 
     * in the filter spec, put out a warning in the log and continue to the next. 
     *
     * A line in the file is: 
     * <name> <filterspec>
     * or 
     * # comment (to be ignored)
     */
    public StoredFilter(AprsServerConfig conf, String filename) {
        _conf = conf;
        _filtmap = new HashMap<String, Filt>();
        
        try {
            BufferedReader rd = new BufferedReader(new FileReader(filename));
            while (rd.ready()) {
                String line = rd.readLine();
                if (line == null)
                    break;
                    
                // Trim whitespace
                line = line.trim();
                
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#"))
                    continue;
                
                // Parse the line: <name> <filterspec>
                // The name is the first word, everything else is the filter spec
                int firstSpace = line.indexOf(' ');
                if (firstSpace == -1) {
                    _conf.log().warn("StoredFilter", "Invalid filter line (missing filter spec): " + line);
                    continue;
                }
                
                String name = line.substring(0, firstSpace).trim();
                String fspec = line.substring(firstSpace + 1).trim();
                
                if (name.isEmpty() || fspec.isEmpty()) {
                    _conf.log().warn("StoredFilter", "Invalid filter line (empty name or spec): " + line);
                    continue;
                }
                
                try {
                    Filt filter = new Filt(fspec, null);
                    _filtmap.put(name, filter);
                } catch (Exception e) {
                    _conf.log().warn("StoredFilter", "Error parsing filter '" + name + "': " + e.getMessage());
                }
            }
            rd.close();
            _conf.log().info("StoredFilter", "Loaded " + _filtmap.size() + " filters from " + filename);
        } catch (IOException e) {
            _conf.log().error("StoredFilter", "Error reading filter file '" + filename + "': " + e.getMessage());
        }
    }
    


    /**
     * Get a stored filter by name. 
     * If not found, return null.
     */
    public AprsFilter get(String name) {
        return _filtmap.get(name);
    }
}
