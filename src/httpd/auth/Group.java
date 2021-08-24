 
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
        
    public String  getIdent()             { return _groupid; }
    public void    setName(String n)      { _name = n; }
    public String  getName()              { return _name; }
    
    public boolean isSar()                { return _sar; }
    public final void setSar(boolean s)   { _sar=s; }
    
    public Group(String id, String n, boolean s){
        _groupid = id; 
        _name = n;
        _sar = s; 
    }
    
    /* Instance "SAR" and DEFAULT */
    public static Group SAR = new Group("SAR", "SAR Group", true);
    public static Group DEFAULT = new Group("DEFAULT", "Default group", false);
}
