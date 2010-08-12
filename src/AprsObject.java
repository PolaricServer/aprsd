/* 
 * Copyright (C) 2002 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
 
package aprs;
import uk.me.jstott.jcoord.*; 
import java.util.*;
import java.io.Serializable;
  
  
  
public class AprsObject extends AprsPoint implements Serializable
{

    /*
     * Attributes of object/item record (APRS data)
     */
    private String      _ident; 
    private Station     _owner;
    private boolean     _killed = false;
       
    public AprsObject(Station owner, String id)
       { 
         _owner = owner;
         _ident = id;        
         _updated = new Date(); 
       }
        

    public synchronized void reset()
    {
       _killed = true;
       super.reset();
    }
    
    
    
    public String getIdent()
       { return _ident; }
       
    public void setOwner(Station o)
       { _owner = o; }
       
    public Station getOwner()
       { return _owner; }
       
       
    public synchronized void update()
    {  
        if (!_killed)
            setChanging();
        _killed = false;  
    }
    
    
    public synchronized void update(Date ts, Reference newpos, int crs, int sp, int alt, 
                                    String descr, char sym, boolean altsym)
    { 
         /* Should try to share code with Station class ?*/
         if (_symbol != sym || _altsym != altsym || 
              (_position == null && newpos != null))
            setChanging();

          if (_position != null && _updated != null) {
              long distance = Math.round(_position.toLatLng().distance(newpos.toLatLng()) * 1000);  
              if (distance > 10)
                 setChanging();
          }
          
         updatePosition(ts, newpos);        
         setDescr(descr); 
         _symbol = sym; 
         _altsym = altsym;
         _killed = false;  
    }
    
    
    public synchronized boolean expired() 
       { return _owner.expired(); }
       
    public synchronized boolean visible()
       { return !_killed && !expired(); }
       
    public void kill()
    {
        _killed = true;
        setChanging();
    }
}
