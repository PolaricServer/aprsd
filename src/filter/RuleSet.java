/* 
 * Copyright (C) 2016-2021 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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

package no.polaric.aprsd.filter;

import java.util.*;
import no.polaric.aprsd.*;


/**
 * A sequence of rules that are executed in order. 
 * Resulting actions are combined by using the merge method.
 */

public class RuleSet implements Rule
{
    private List<Rule> rlist = new LinkedList<Rule>(); 
    private Set<String> _groups = new HashSet<String>();
    private boolean _public = false; 
    private boolean _nologin = false;
    private boolean _all = false; 
    private String _descr; 
    private int _line; 
    
    /*
     * Set ruleset to be publicly accessible.
     */
    public void setPublic()
       { _public = true; }
       
    public boolean isPublic()
       { return _public; }
       
    public void setNologin()
       { _nologin = true; }
       
    public boolean isNologin()
       { return _nologin; }
       
    /* Visible for all logged in users */
    public void setAll()
       { _all= true; }
       
    public boolean isAll()
       { return _all; }   
       
       
    public void setDescr(String x) 
       { _descr = x; }
       
    public String getDescr() 
       { return _descr; }
       
    public boolean isExported() 
       { return _descr != null; }
       
       
    public int getLine()
       { return _line; }
       
    public void setLine(int l)
       { _line = l; }
       
       
    public void addGroup(String g) { 
        if (g.equals("NOLOGIN"))
           setNologin();
        _groups.add(g); 
    }
       
    public boolean isGroup(String g) 
       {  return _groups.contains(g); }
       
       
    /**
     * Add a rule to the ruleset.
     * @param r rule to be added. 
     */
    public void add(Rule r)
       { if (r != null) 
           rlist.add(r); }
       
    /**
     * Create and add a rule to the ruleset. 
     * @param p The predicate of the rule.
     * @param a Action of the rule.
     */
    public void add(Pred p, Action a) {
        Rule r = new Rule.Single(p, a); 
        add(r);
    }

    /**
     * Apply the ruleset to a point.
     * @param p TrackerPoint object.  
     * @param scale Current map scale
     * @return action - object that tells how the argument is going to be displayed. 
     */
    public Action apply(TrackerPoint p, long scale) {
       /* Start with a null action.  An action that changes nothing. */
       Action a = Action.NULL(); 

       /*  Actions are additive */
       for (Rule r: rlist)
          a.merge(r.apply(p, scale)); 
       
       return a; 
    }
    
}

