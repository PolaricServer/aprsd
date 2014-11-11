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

package no.polaric.aprsd.filter;

import java.util.*;
import no.polaric.aprsd.*;


/**
 * A sequence of rules that are executed in order. 
 * Resulting actions are combined by using the merge method.
 */

public class RuleSet 
{
    private List<Rule> rlist = new LinkedList<Rule>(); 
    private boolean _public = false; 
    
    
    /**
     * Set ruleset to be publicly accessible.
     */
    public void setPublic()
       { _public = true; }
       
    public boolean isPublic()
       { return _public; }
       
       
    /**
     * Add a rule to the ruleset.
     * @param r rule to be added. 
     */
    public void add(Rule r)
       { rlist.add(r); }
       
    /**
     * Create and add a rule to the ruleset. 
     * @param p The predicate of the rule.
     * @param a Action of the rule.
     */
    public void add(Pred p, Action a) {
        Rule r = new Rule(p, a); 
        add(r);
    }

    /**
     * Apply the ruleset to a point.
     * @param p AprsPoint object.  
     * @param scale Current map scale
     * @return action - object that tells how the argument is going to be displayed. 
     */
    public Action apply(AprsPoint p, long scale) {
       /* Start with a null action.  An action that changes nothing. */
       Action a = Action.NULL(); 
       
       /*  Actions are additive */
       for (Rule r: rlist)
          a.merge(r.apply(p, scale)); 
       
       return a; 
    }
    
}

