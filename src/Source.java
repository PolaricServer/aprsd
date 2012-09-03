 
/* 
 * Copyright (C) 2012 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
     protected String _style = null;
     protected String _ident = null; 
        
     
     public enum Type {inet, radio, local};
     
     
     protected void _init(Properties config, String prefix, String id) 
     {
        _restrict = config.getProperty(prefix+"."+id+".restrict", "false").trim().matches("true|yes");
        _style = config.getProperty(prefix+"."+id+".style", _style); 
        _ident = id;
           
        if (_style==null)
           _style = _ident;
        else
           _style.trim();
     }
     
     
     public String getIdent()
        { return _ident; }
        
     public String getStyle() 
        { return _style; }
        
     public boolean isRestricted()
        { return _restrict; }
        
    public abstract String getShortDescr(); 
    
}

