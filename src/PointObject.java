/* 
 * Copyright (C) 2010 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import java.io.Serializable;
  

/**
 * Object at a single location.
 * Every point has a location, icon and description. 
 */
 
public abstract class PointObject extends Point
{             
    protected String      _icon; 
    protected String      _description;    
    
       
    public PointObject(Reference p)
       { super(p); }
       
    public boolean iconIsNull()
       { return _icon == null; }
    
       
    /*  Redefined in AprsPoint */   
    public String getIcon()
    { 
       if (_icon != null)
          return _icon; 
       return null;
    }           
    
    public String getDescr()
       { return (_description == null ? "" : _description); }
       
    public boolean visible() {return true;}
}
