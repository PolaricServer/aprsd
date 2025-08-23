/* 
 * Copyright (C) 2016-2025 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 */
 
package no.polaric.aprsd.point;
import no.polaric.aprsd.Seq;
import java.util.*;   
import java.io.Serializable;  
import java.util.function.*;



  
/**
 * Movement history of APRS stations. A history has a certain maximum length with
 * respect to time span. 
 */  
public class Trail implements Iterable<Trail.Item>, Serializable
{
   
    /**
     * History item. It is a geographical point with timestamp and some additional info.
     */
    public static class Item extends TPoint {
       public int speed;
       public int course; 
       public Item(Date t, LatLng p, int sp, int crs, String path)
         { super(t, p, path); speed = sp; course = crs; } 
    }
       
       
    /**
     * Part of the trail, bounded by a max length (in minutes) and a 
     * max age. If trail contains no elements newer than the expire time. 
     * this results in an empty trail. 
     */
    public class SubTrail implements Seq<TPoint>
    {
        private long expire, length; 
        private Predicate<TPoint> _pred; 
       
        public SubTrail(long exp, long len, Predicate<TPoint> pred) { 
           expire  = (exp > -1 ? exp*60*1000 : _maxAge);
           length  = (len > -1 ? len*60*1000 : _maxAge);  
           _expire = (expire > _expire ? expire : _expire);
           _length = (length > _length ? length : _length);
           _pred = pred; 
        }
        
        public boolean isEmpty() 
          { return Trail.this.isEmpty(); }
          
          
        public void forEach(Consumer<TPoint> f) 
        { 
           if (isEmpty())
             return;

           long t = (new Date()).getTime();
           long tfirst = getFirst().getTS().getTime();
           
           if (tfirst + expire < t) 
              return;
              
           synchronized(Trail.this) {
             for (TPoint x: _items) {
               if ( x.getTS().getTime() < tfirst - length ||
                  !_pred.test(x) )
                 break;
               f.accept(x);   
             }
           }
        } 
    }
   
   
   
    private static long _maxAge = 1000 * 60 * 15;          // Max age of a history item (default 30 minutes) 
    private static long _maxPause = 1000 * 60 * 10;        // History removed if no movement within this time (15 minutes) 
    private static long _maxAge_ext = 1000 * 60 * 30;      // Max age of a history item - extended
    private static long _maxPause_ext = 1000 * 60 * 20;     
        
    
    private LinkedList<Item> _items = new LinkedList<Item>();
    private boolean _extended;
    private boolean _itemsExpired; 
    private long _expire, _length; 
    private int  _maxSpeed;    
    
    public static void setMaxAge(long a)
       { _maxAge = a; }
    public static void setMaxPause(long p)
       { _maxPause = p; }
    public static void setMaxAge_Ext(long a)
       { _maxAge_ext = a; }
    public static void setMaxPause_Ext(long p)
       { _maxPause_ext = p; }    
 

    public Trail() 
      { _expire=_maxPause; _length=_maxAge; }
    
    public boolean itemsExpired() 
        { cleanUp(new Date()); 
          if (_itemsExpired) {_itemsExpired = false; return true; }
          else { return false; } 
        } 
 
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
       TPoint it =_items.peekLast();
       if (it == null) return null;
       else return it.getTS();
    }
    
    
    /**
     * Get the point in the trail with the highest speed
     */
    public synchronized int getMaxSpeed() {
       int max = -1;
       for (Item x : _items) 
           max = (x.speed > max ? x.speed : max); 
       return max; 
    }
  
  
    public synchronized int getAvgSpeed() {
       int sum = 0;
       for (Item x : _items) 
          if (x.speed >= 0) sum += x.speed; 
       return (int) Math.round(sum / length());
    }
    
    
    /* minimum distance: 10 meters */
    public static final long mindist = 10;
    
    
    /**
     * Add a position report to history.
     */
    public synchronized boolean add(Date t, LatLng p, int sp, int crs, String path)
    { 
        Date now = new Date(); 
        boolean added = false;
       
        /* If position is from own GPS and course change is small and time isn't more than 
         * 30 seconds after last position, then we just ignore the update. 
         */
        if (length() > 0 && "(GPS)".equals(path) && Math.abs(crs - getFirst().course) < 20 &&
                 t.getTime() < getFirst().getTS().getTime() + 30000)
           return added;

      
        /* If new report is newer than the last report and distance from previous 
         * position is more than mindist, put it first 
         */
        if ( _items.size() == 0 || 
            (t.getTime() >= getFirst().getTS().getTime() && getFirst().distance(p) > mindist)) {
            _items.addFirst(new Item(t, p, sp, crs, path)); 
            added = true;
        }
        else {    
           TPoint x=null, prev=null; 
           /* New report is older than the last report - find the right place and put it there */
           ListIterator<Item> it = _items.listIterator();
           while (it.hasNext()) {
              x = it.next();
              if (x.getTS().getTime() < t.getTime())
                 break;
              prev = x; 
           }
          it.previous();
          if (x.distance(p) > mindist  && prev != null && (prev == null || prev.distance(p) > mindist)) {
             it.add(new Item(t, p, sp, crs, path));
             added = true;
          }
       }
       cleanUp(now);
       return added;
    }
    
    
    public List<Item> points()
       { return _items; }
       
    public Item getFirst()
       { return (Item) _items.getFirst(); }  

       
    public Item getLast()
       { return (Item) _items.getLast(); } 
       
       
    public Item getPoint(int index)
       { return (Item) _items.get(index-1); }
       
       
       
    public synchronized Item getPointAt(Date t)
    {
        ListIterator<Item> it = _items.listIterator();
        Item x; 
        while (it.hasNext()) {
            x = it.next();
            if (t.getTime()-1000 < x.getTS().getTime() && t.getTime()+1000 > x.getTS().getTime())
              return x; 
        }
        return null;
    }
    
    
    public Iterator<Item> iterator()
    {
       cleanUp(new Date()); 
       return _items.iterator();
    }  

 
    public Seq<TPoint> subTrail(long exp, long len, Predicate<TPoint> pred) {
        return new SubTrail(exp, len, pred);
    }
    
    
    /**
     * Remove the oldest entries in history.
     */
    protected synchronized void cleanUp(Date now)
    {
        if (isEmpty())
           return;
        
        if ((_items.getFirst().getTS().getTime() + _expire) < now.getTime() )
        { 
            _items.clear(); 
            _itemsExpired = true; 
            return; 
        }
        while (!isEmpty() && (_items.getLast().getTS().getTime() + _length) < now.getTime()) {
           _items.removeLast();
           _itemsExpired = true; 
        }
    }
    
}
