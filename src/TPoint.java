 
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
import uk.me.jstott.jcoord.*; 
import java.util.*;
import java.util.function.*;



/**
 * Geographic point with timestamp
 */
public class TPoint extends Point
{
    protected Date _time; 
    protected String _path;
    
    public Date getTS()
        { return _time; }
       
    public String getPath()
        { return _path; }
    
//    public TPoint (Date t, Reference p)
//        { super(p);  _time = t;}
        
    public TPoint (Date t, Reference p, String pt)
        { super(p);  _time = t; _path=pt;}
}
