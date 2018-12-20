 
/* 
 * Copyright (C) 2018 by Øyvind Hanssen (ohanssen@acm.org)
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



public abstract class User {

    
    /** 
     * Area. Can be stored and serialized as JSON and sent to client. 
     */
    public static class Area implements Serializable {
        public int index;
        public int baseLayer;
        public boolean[] oLayers;
        public String name;
        public double[] extent;
    }


    private String userid; 
       
    public String  getIdent()
        { return userid; }
    
    public abstract Date    getLastUsed();
    public abstract void    updateTime();
    public abstract boolean isActive();
    public abstract void    setActive();
    
    public abstract Collection<Area> getAreas();
    public abstract int  addArea(Area a);
    public abstract void removeArea(int i);
    
    
    protected User(String id)
        { userid=id; }
        
        
        
        
        
}
