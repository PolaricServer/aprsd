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
 
package no.polaric.aprsd.point;
import no.arctic.core.*;
import java.util.*;
import java.util.regex.*;
import java.io.*;
import java.text.*;



/**
 * Channel for sending/receiving APRS data. 
 */
public abstract class Source implements Serializable
{
     
     protected boolean _restrict = false;
     protected String _tag = null;
     protected String _ident = null; 
     transient protected ServerAPI _api;
        
     
     public enum Type {inet, radio, local};
     
     
     protected void _init (ServerAPI config, String id, boolean restrict, String tag) 
     {
        _api = config;
        _ident = id; 
        _restrict = restrict; 
        _tag = tag; 
        if (tag != null) 
            _tag.trim();
     }
     
     
     
     protected void _init(ServerAPI config, String prefix, String id) 
     {
        boolean restrict = config.getBoolProperty(prefix+"."+id+".restrict", false);
        String tag = config.getProperty(prefix+"."+id+".tag", _tag); 
        _init(config, id, restrict, tag); 
     }
     
     
     
     /**
      * Return an identifier name. 
      */
     public String getIdent()
        { return _ident; }
        
        
     /**
      * Return a string to tag point objects with. 
      */
     public String getTag() 
        { return _tag; }
        
        
     /**
      * Return true if channel has the x.x.restrict property set to true in config.
      */
     public boolean isRestricted()
        { return _restrict; }
        

    
}

