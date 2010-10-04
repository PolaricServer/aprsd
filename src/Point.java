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
  


public abstract class Point implements Serializable
{             
    protected Reference   _position;  
    protected String      _icon; 
    protected String      _description;    
    
    /**
     * Test if position is inside of the rectangular area defined by uleft (upper left corner)
     * and lright (lower right corner). Assume that uleft and lright are the same
     * UTM zone. 
     */          
    public boolean isInside(UTMRef uleft, UTMRef lright)
    {
         /* FIXME: Add lat zone as well */
        if (_position == null)
           return false;
        try {
           UTMRef ref = _position.toLatLng().toUTMRef(uleft.getLngZone());
           return ( ref.getEasting() >= uleft.getEasting() && ref.getNorthing() >= uleft.getNorthing() &&
                    ref.getEasting() <= lright.getEasting() && ref.getNorthing() <= lright.getNorthing() );
        }
        catch (Exception e) { return false; }
    }
    
    public Reference getPosition ()   
       { return _position; } 

       
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
