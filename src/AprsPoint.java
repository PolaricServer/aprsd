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
import java.io.Serializable;
  

/**
 * APRS geographical point.
 */
public abstract class AprsPoint extends TrackerPoint implements Serializable, Cloneable
{  
    private   static SymTable  _symTab;
    
    protected char        _symbol; 
    protected char        _altsym; 
    protected int         _ambiguity = 0;
    
    
    public static void setApi(ServerAPI api) {
        _symTab = new SymTable (api, System.getProperties().getProperty("confdir", ".")+"/symbols");
    }
    
    
    public AprsPoint(Reference p)
      { super(p); }

    

    public boolean isInfra() 
       { return false; }
   
    public char getSymbol()
       { return _symbol; }
       
   
   public char getSymtab()
       { return _altsym;}
   
   
    public void setSymbol(char s)
       { _symbol = s; }
   
   
    public void setSymtab(char s)
       { _altsym = s; }
    
    
    public String getIcon(boolean override)
    { 
       if (override && _icon != null)
          return _icon; 
       return _symTab.getIcon(_symbol, _altsym);
    }           
           

    public int getAmbiguity()
       { return _ambiguity; }
       
              
    
    /**
     * Update position of the object. 
     *
     * @param ts Timestamp (time of update). If null, the object will be timeless/permanent.
     * @param newpos Position coordinates.
     * @param ambiguity 
     */    
    public void updatePosition(Date ts, Reference newpos, int ambiguity)
    {
         updatePosition(ts, newpos);
         _ambiguity = ambiguity;
    }
        
        
    /**
     * Manual update of position. 
     *
     * @param ts Timestamp (time of update). If null, the object will be timeless/permanent.
     * @param pd Position data (position, symbol, ambiguity, etc..)
     * @param descr Comment field. 
     * @param path Digipeater path of containing packet. 
     */    
    public abstract void update(Date ts, AprsHandler.PosData pd, String descr, String path);        
        

}
