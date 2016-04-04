/* 
 * Copyright (C) 2016 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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

/**
 * A rule is something that results in an action when applied to a point and a scale. 
 * It is a recursive structure meaning that a rule can contain other rules (see RuleSet). 
 */
public interface Rule
{
   public Action apply(TrackerPoint obj, long scale); 
   
   
   public class Single implements Rule {
      private Pred pred; 
      private Action action;
        
        
      public Single(Pred p, Action a) {
         pred = p; 
         action = a; 
      }
   
   
      /**
       * Apply the rule.
       * @param obj TrackerPoint object.  
       * @param scale Map scale
       * @return action - object that tells how the argument is going to be displayed. 
       */
      public Action apply(TrackerPoint obj, long scale) {
          if (pred.eval(obj, scale))
             return action;
          else
             return null;
      }
   }
}

