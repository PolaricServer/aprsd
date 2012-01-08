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
 
package no.polaric.aprsd;
import uk.me.jstott.jcoord.*; 
import java.util.*;   
import java.io.Serializable;  
  
  
/**
 * Movement history of APRS stations. A history has a certain maximum length with
 * respect to time span. 
 */  
public class Trail implements Iterable<Trail.Item>, Serializable
{
    /**
     * History item. It is a geographical point with timestamp and some additional info.
     */
    public static class Item extends Point {
       public Date time; 
       public int speed;
       public int course; 
       public String pathinfo;
       public Item(Date t, Reference p, int sp, int crs, String path)
         { super(p); time = t; speed = sp; course = crs; pathinfo = path;} 
    }
    
    private static long _maxAge = 1000 * 60 * 15;          // Max age of a history item (default 30 minutes) 
    private static long _maxPause = 1000 * 60 * 10;        // History removed if no movement within this time (15 minutes) 
    private static long _maxAge_ext = 1000 * 60 * 30;      // Max age of a history item - extended
    private static long _maxPause_ext = 1000 * 60 * 20;     
        
    
    private LinkedList<Item> _items = new LinkedList();
    private boolean _extended;
    private int _sum_speed = 0;
    
    public static void setMaxAge(long a)
       { _maxAge = a; }
    public static void setMaxPause(long p)
       { _maxPause = p; }
    public static void setMaxAge_Ext(long a)
       { _maxAge_ext = a; }
    public static void setMaxPause_Ext(long p)
       { _maxPause_ext = p; }
        
    
    public boolean isEmpty() 
        { return (_items.size() == 0); }
        
    public int length() 
        { return _items.size(); }
    
    public void clear()
        { _items.clear(); }
        
    /**
     * Get the time of the oldest item in history.
     */
    public Date oldestPoint()
    {
       Item it =_items.peekLast();
       if (it == null) return null;
       else return it.time;
    }
    
    
    public static final long mindist = 15;
    
    /**
     * Add a position report to history.
     */
    public synchronized boolean add(Date t, Reference p, int sp, int crs, String path)
    { 
        Date now = new Date(); 
        _sum_speed += sp;
        boolean added = false;
         
        /* New report is newer than the last report - put it first */
        if ( _items.size() == 0 || 
            (t.getTime() >= getFirst().time.getTime() && getFirst().distance(p) > mindist)) {
            _items.addFirst(new Item(t, p, sp, crs, path)); 
            added = true;
        }
        else {    
           Item x=null, prev=null; 
           /* New report is older than the last report - find the right place and put it there */
           ListIterator<Item> it = _items.listIterator();
           while (it.hasNext()) {
              x = it.next();
              prev = x;
              if (x.time.getTime() < t.getTime())
                 break;
           }
          it.previous();
          if (x.distance(p) > mindist  && prev != null && prev.distance(p) > mindist) {
             it.add(new Item(t, p, sp, crs, path));
             added = true;
          }
       }
       cleanUp(now);
       return added;
    }
    

    public List<Item> items()
       { return _items; }
       
       
    public Item getFirst()
       { return _items.getFirst(); }  


    public Item getPoint(int index)
       { return _items.get(index-1); }
    

    public Iterator<Item> iterator()
    {
       cleanUp(new Date()); 
       return _items.iterator();
    }
    

    /**
     * Remove the oldest entries in history.
     */
    protected synchronized void cleanUp(Date now)
    {
        if (isEmpty())
           return;
        boolean ext = ((_sum_speed / _items.size()) < 15);
        
        if ((_items.getFirst().time.getTime() + (ext ? _maxPause_ext : _maxPause)) < now.getTime() )
        { 
            _items.clear(); 
            _sum_speed = 0;
            return; 
        }
        while (!isEmpty() && (_items.getLast().time.getTime() + (ext ? _maxAge_ext : _maxAge)) < now.getTime()) {
           _sum_speed -= _items.getLast().speed;
           _items.removeLast();
        }
    }
    
}
