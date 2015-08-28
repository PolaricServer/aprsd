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
import uk.me.jstott.jcoord.*; 
import java.util.*;
import java.io.*;

/**
 * Object at a single location.
 * Every point has a location, icon and description. 
 */
 
public abstract class PointObject extends Point implements Cloneable
{             

    /* 
     * All points can have tags. This is implemented with a static
     * set where each tag is prefixed with the point identifier and a '#' 
     *
     * Note: This doesn't support getting all tags for a object. 
     * This may be implemented if we use a SortedSet instead. 
     */
    protected static Set<String> _tags = new HashSet<String>();
    
    public static void saveTags(ObjectOutput ofs) 
      throws IOException
      { ofs.writeObject(_tags); }
      
    public static void restoreTags(ObjectInput ifs) 
     throws IOException,ClassNotFoundException
      { _tags = (Set<String>) ifs.readObject(); }
    
    public void setTag(String tag) 
      { _tags.add(getIdent()+"#"+tag); }
    
    public void removeTag(String tag)
      { _tags.remove(getIdent()+"#"+tag); }
     
    public boolean hasTag(String tag) 
      { return _tags.contains(getIdent()+"#"+tag); }
    
    
    
    protected String      _icon; 
    protected String      _description;    
       
    public PointObject(Reference p)
       { super(p); }       
    

    
    
    public abstract String getIdent();
    
       
    public boolean iconIsNull()
       { return _icon == null; }
    
       
    /*  Redefined in AprsPoint */   
    public String getIcon()
    { 
       if (_icon != null)
          return _icon; 
       return null;
    }  
    
    public boolean hasDescr()
       { return _description != null; }
       
    public String getDescr()
       { return (_description == null ? "" : _description); }
       
    public boolean visible() {return true;}
    
    public abstract Source getSource(); 
}
