/* 
 * Copyright (C) 2013 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import java.util.*;
import java.io.Serializable;
  
  
    /** 
     * Experimental statistics/info about connectivity.
     * This is essentially a graph where edges represent where packets have 
     * travelled. 
     */ 
       
public class RouteInfo implements Serializable
{
    protected static class Node implements Serializable {
       public Map<String, Edge> from = new HashMap();   // One of these can be Set<string> instead
       public Map<String, Edge> to = new HashMap();      
       public void addFrom(String id, Edge e) { from.put(id, e); }    
       public void addTo(String id, Edge e) { to.put(id, e); }  
    }
    
    public static class Edge implements Serializable {
       public Date ts;
       public boolean primary = false;
       public Edge() { ts = new Date(); }
       
       public void update(boolean p) 
       {
           Date now = new Date();
           if (primary && ts != null && ts.getTime() + 100*60*60*24*3 < now.getTime())
              primary = false;
           if (p)
              primary = true;
           ts = new Date(); 
       }
    }
    
        
   
     
    private Map<String, Node> _nodes = new HashMap();        
    private long nEdges;
    
    public long nItems() 
       { return nEdges; }

    
    public synchronized void clear()
        { _nodes.clear(); nEdges = 0; } 
        
        
    public synchronized void addEdge(String from, String to, boolean p)
    {     
        if (!_nodes.containsKey(from))
           _nodes.put(from, new Node());
        if (!_nodes.containsKey(to))
           _nodes.put(to, new Node());
      
        Edge e = _nodes.get(from).from.get(to);
        if (e==null) {
           e = new Edge();
           _nodes.get(from).addFrom(to, e); 
           _nodes.get(to).addTo(from, e);
           nEdges++;
        }
        else 
           e.update(p);
    }
       
       
    public synchronized void removeNode(String stn)
    { 
        _nodes.remove(stn); 
        for (Node n: _nodes.values()) {
            if (n.from.containsKey(stn)) {
                n.from.remove(stn); nEdges--;
            }
            if (n.to.containsKey(stn)) {
               n.to.remove(stn); nEdges--;
            }
        }
    }
                  
              
              
    private synchronized Set<String> clean(Set<String> s)
    {
       Iterator<String> it = s.iterator();   
       Set<String> x = new HashSet();
       for (String key: s)
            if (_nodes.containsKey(key))
              x.add(key);
       return x;   
    }
                
                
    public synchronized Set<String> getFromEdges(String stn)
       { return (_nodes.get(stn) !=null ? clean(_nodes.get(stn).from.keySet()) : null); }


    public synchronized Set<String> getToEdges(String stn)
       { return (_nodes.get(stn) != null ? clean(_nodes.get(stn).to.keySet()) : null); }
       
       
    public synchronized Edge getEdge(String from, String to)
       { return (_nodes.get(from) != null ? _nodes.get(from).from.get(to) : null); }
       
       
    public synchronized void removeEdge(String from, String to) 
    {  
        if (_nodes.get(from).from == null)
           return;
        _nodes.get(from).from.remove(to); 
        _nodes.get(to).to.remove(from);
        nEdges--;
    }
              
    /**
     * Remove edges older than the given date from/to the given stn.
     */          
    public synchronized void removeOldEdges(String stn, Date agelimit)
    {
       if (agelimit == null)
          return;
       if (_nodes.get(stn) == null)
          return; 
          
       Iterator<String> it = getFromEdges(stn).iterator();   
       while ( it.hasNext() ) {
            String x = it.next();
            Edge e = getEdge(stn, x);
            if (e != null && e.ts.getTime() < agelimit.getTime()) 
                removeEdge(stn, x);
       }    
       it = getToEdges(stn).iterator();
       while ( it.hasNext() ) {
           String x = it.next();
           Edge e = getEdge(x, stn);
           if (e != null && e.ts.getTime() < agelimit.getTime())  
               removeEdge(x, stn);
       }  
    }
    
    
    public void removeOldEdges(Date agelimit)
    {
        for (String x: _nodes.keySet())
            removeOldEdges(x, agelimit); 
    }
    
}
