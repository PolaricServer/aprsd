 
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
import java.io.Serializable;


/**
 * Group class. Implemented with local file or database
 */
public class Group implements Serializable {
 
    private String  _groupid; 
    private String  _name = "";
    private boolean _sar;
    private String  _tags; 
    
    // Add: 
    // - geo area where group authorisations to features or layers are valid
    // - write/add access for signs, etc..
    
        
    public String  getIdent()             { return _groupid; }
    public void    setName(String n)      { _name = n; }
    public String  getName()              { return _name; }
    public String  getTags()              { return _tags; }
    public void    setTags(String t)      { _tags = t; }
    
    public boolean isSar()                { return _sar; }
    public final void setSar(boolean s)   { _sar=s; }
    
    public Group(String id, String n, String t, boolean s){
        _groupid = id; 
        _name = n;
        _tags = t; 
        _sar = s; 
    }

    public static Group DEFAULT = new Group("DEFAULT", "No group", null, false);
}
