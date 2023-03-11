/* 
 * Copyright (C) 2015-2020 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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

public class TagRuleSet 
{
    private List<TagRule> rlist = new ArrayList<TagRule>(); 
    
       
    /**
     * Add a rule to the ruleset.
     * @param r rule to be added. 
     */
    public void add(TagRule r)
       { rlist.add(r); }
       


    /**
     * Apply the ruleset to a point.
     * @param p TrackerPoint object.  
     */
    public void apply(TrackerPoint p) {
        /* First, remove tags for rules that evaluates to false */
        for (TagRule r: rlist)
            r.applyRemove(p);
            
        /* Then, add tags for rules that evaluates to true */
        for (TagRule r: rlist)
            r.apply(p); 
    }
    
}

