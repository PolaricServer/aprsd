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
import no.polaric.aprsd.*;
import java.util.*;


public class TagRule
{
    private Pred pred; 
    private List<String> action;
   
    public TagRule(Pred p, List<String> a) {
        pred = p; 
        action = a; 
    }
   
   
   /**
    * Apply the rule.
    * @param obj TrackerPoint object.  
    */
    public void apply(TrackerPoint obj) {
        if (pred.eval(obj, 0))
            for (String t: action)
                obj.setTag(t);
    }
    
    
    public void applyRemove(TrackerPoint obj) {
        if (!pred.eval(obj, 0))
            for (String t: action)
                obj.removeTag(t);
    }
}

