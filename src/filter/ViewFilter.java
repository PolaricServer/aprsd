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
import no.polaric.aprsd.*;
import java.util.*;
import java.io.*; 



public class ViewFilter {
    
  private static Map<String, RuleSet> _map = new HashMap();
  private static RuleSet _default = new RuleSet(); 
  private static TagRuleSet _tagrules; 
  
  
  public static RuleSet getFilter(String id, boolean loggedIn)
  {
     RuleSet x  = _map.get(id);
     if (x==null || (!x.isPublic() && !loggedIn)) 
        return _default;
     return x;
  }    
  
  
  public static TagRuleSet getTagRules() 
     { return _tagrules; }
     
  
  
  /* Action(hideid, hidetrail, hideall, showpath, style) */
  public static void init(ServerAPI api) 
  {
      String filename = System.getProperties().getProperty("confdir", ".") + "/view.profiles";
      try {
         // Default is to hide all
         _default.add(Pred.TRUE(), new Action(true,true,true,false,false,"",null));
         
         api.log().info("ViewFilter", "Compiling view profiles..");
         Parser parser = new Parser(api, new FileReader(filename), filename);
         parser.parse();
         _map = parser.getProfiles();
         _tagrules = parser.getTagRules();
      }
      catch (FileNotFoundException e)
        { api.log().error("ViewFilter", "File not found '"+filename+"'"); }
  } 
}