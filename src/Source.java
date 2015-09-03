 
/* 
 * Copyright (C) 2014 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import java.util.regex.*;
import java.io.*;
import se.raek.charset.*;
import java.text.*;



/**
 * Channel for sending/receiving APRS data. 
 */
public abstract class Source implements Serializable
{
     
     protected boolean _restrict = false;
     protected String _tag = null;
     protected String _ident = null; 
        
     
     public enum Type {inet, radio, local};
     
     
     protected void _init (String id, boolean restrict, String tag) 
     {
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
        _init(id, restrict, tag); 
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
        
        
    /** 
     * Return a short (3 character) descriptor for channel. 
     * The first two should normally indicate the type of the channel (e.g. RF), the last character 
     * should typically be a number.
     */
    public abstract String getShortDescr(); 
    
}

