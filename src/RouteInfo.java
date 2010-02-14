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
 
package aprs;
import java.util.*;
import java.io.Serializable;
  
  
  
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
       public Edge() { ts = new Date(); }
       public void update() { ts = new Date(); } 
    }
    
        
    /* Experimental statistics/info about connectivity. 
     * Should address: 
     *    - Remove old stuff (at least for mobile stations)
     *    - What should we do about mobile stations?
     *    - Should we report stations that are not shown? 
     */    
     
    private Map<String, Node> _nodes = new HashMap();        
    
    
    
    public synchronized void addEdge(String from, String to)
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
        }
        else
           e.update();
    }
       
       
    public synchronized void removeNode(String stn)
       { _nodes.remove(stn); }
              
              
    public Set<String> getFromEdges(String stn)
       { return (_nodes.get(stn) !=null ? _nodes.get(stn).from.keySet() : null); }


    public Set<String> getToEdges(String stn)
       { return (_nodes.get(stn) != null ? _nodes.get(stn).to.keySet() : null); }
       
       
    public Edge getEdge(String from, String to)
       { return (_nodes.get(from) != null ? _nodes.get(from).from.get(to) : null); }
       
       
    public synchronized void removeEdge(String from, String to) 
    {  
        _nodes.get(from).from.remove(to); 
        _nodes.get(to).to.remove(from);
    }
              
              
    public synchronized void removeOldEdges(String stn, Date agelimit)
    {
       if (agelimit == null)
          return;
       if (_nodes.get(stn) == null)
          return; 
       for (String to: getFromEdges(stn)) {
            Edge e = getEdge(stn, to);
            if (e.ts.getTime() < agelimit.getTime())
               removeEdge(stn, to);
          }    
       for (String from: getToEdges(stn)) {
           Edge e = getEdge(from, stn);
           if (e.ts.getTime() < agelimit.getTime())
              removeEdge(from, stn);
       }  
    }
    
}
