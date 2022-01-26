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
     * All points can have tags, implemented using a set of strings. 
     * There is also a static map to keep track of what tag-names are 
     * used an how many point-objects using each. 
     */
    protected Set<String> _tags = new HashSet<String>();
    protected static SortedMap<String, Integer> _tagUse = new TreeMap<String, Integer>();
    
    
    /** 
     * Save tags on file.
     */
    public static void saveTags(ObjectOutput ofs) 
      throws IOException
      { ofs.writeObject(_tagUse); }
      
      
    /**
     * Restore tags from file. 
     */
    public static void restoreTags(ObjectInput ifs) 
     throws IOException,ClassNotFoundException
      { _tagUse = (SortedMap<String,Integer>) ifs.readObject(); }
    
        
    /**
     * Increment the count for the given tag. 
     */
    private static void _incrementTag(String tag) {
        synchronized(_tagUse) {
            Integer x = _tagUse.get(tag); 
            if (x != null) {
                x++;
                _tagUse.replace(tag, x);
            }
            else 
                _tagUse.put(tag, new Integer(1));
        }
    }
    
    
    /**
     * Decrement the count for the given tag. 
     */
    private static void _decrementTag(String tag) {
        synchronized(_tagUse) {
            Integer x = _tagUse.get(tag);
            if (x == null) return; 
            x--;
            if (x <= 0)
                _tagUse.remove(tag);
            else 
                _tagUse.replace(tag, x);
        }
    }
    
    
    /** 
     * Return the set of used tags. 
     */
    public static Set<String> getUsedTags() 
       { return _tagUse.keySet(); }
    
    public static boolean tagIsUsed(String tag)
       { return _tagUse.get(tag) != null; }
       
    
    
    /* Class for Json encoding info about a point. This is subclassed in AprsPoint */
    public static class JsInfo {
        public String type = "PointObject";
        public String ident, descr; 
        public String source;
        public double pos[]; /* lon, lat */
        
        public JsInfo(PointObject p) {
            ident = p.getIdent();
            descr = p.getDescr(); 
            source = p.getSourceId(); 
            LatLng ref = p.getPosition().toLatLng();
            pos = new double[] {ref.getLng(), ref.getLat()};
        }
    }
    
    
    public JsInfo getJsInfo() {
        return new JsInfo(this);
    }
        
        
    
    public Set<String> getTags() {
        return _tags;
    }
    
     
    /**
     * Set tag on this object. 
     */
    public void setTag(String tag) {
       if (tag == null || tag.equals("")) 
          return; 
       _incrementTag(tag);
       _tags.add(tag); 
    }
    
    
    /**
     * Remove tag. 
     */
    public void removeTag(String tag) { 
        if (tag == null || !_tags.contains(tag))
            return;
        _decrementTag(tag);
        _tags.remove(tag); 
    }
    
    
    
    /**
     * Remove all tags associated with this object. 
     */
    public void removeAllTags() {
        for (String x : _tags)
           _decrementTag(x);
        _tags.clear();
    }
    
    
    /**
     * Return true if regex matches any tag on this object. 
     * Potential security risk! It expects a regex. If input from user which is not expected to be a regex,
     * input should be properly sanitized using SecUtils.escape4regex()
     */
    public boolean hasTag(String tag) { 
        if (tag==null)
            return false; 
       
        for (String x: _tags) {
            if (x.matches("("+tag+")(\\..*)?")) {
                return true;
            }
        }
        return false;
    }
    
    
    public boolean _tagIsOn(String tag) {
        return ( hasTag( "\\+?(" + tag + ")") && !hasTag("\\-"+tag) );
    }
    
    
    public boolean tagIsOn(String tag) {
        if (tag.charAt(0)=='+')
            return _tagIsOn(tag.substring(1, tag.length()));
        else
            return _tagIsOn(tag);
    }
    
    
    
    
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
    
    public String getSourceId() { 
        Source s = getSource(); 
        return (s==null ? "(unknown)" : s.getIdent()); 
    }
    
    public abstract Source getSource(); 
}
