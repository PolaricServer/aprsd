/* 
 * Copyright (C) 2014-2025 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
 
package no.polaric.aprsd.filter;
import no.polaric.aprsd.*;
import no.polaric.aprsd.aprs.*;
import java.util.*;
import java.io.*; 




public class ViewFilter {
    
  private static Map<String, RuleSet> _map = new LinkedHashMap<String,RuleSet>();
  private static RuleSet _default = new RuleSet(); 
  private static TagRuleSet _tagrules; 
  
  
  
  /* Get a single filter (RuleSet) */
  public static RuleSet getFilter(String id, boolean loggedIn)
  {
     RuleSet x  = _map.get(id); 
     
     if (!loggedIn && x != null && x.isNologin()) 
         return x;
     if (x==null || (!x.isPublic() && !loggedIn) ) 
         return _default;
     return x;
  }    
  
  
  /* Get a list of available filters: name, description pairs */
  public static List<String[]> getFilterList(boolean loggedIn, String group) 
  {
     List<Map.Entry<String, RuleSet> > list
            = new ArrayList<Map.Entry<String, RuleSet> >(
                _map.entrySet());
     Collections.sort(list, Comparator.comparingInt(e -> e.getValue().getLine()));
            
     List<String[]> res = new ArrayList<String[]>();
     list.forEach( (Map.Entry<String, RuleSet> e) -> {
        if ( e.getValue().isPublic() 
                || (loggedIn && e.getValue().isAll())
                || (loggedIn && group != null && e.getValue().isGroup(group))
                || (!loggedIn && e.getValue().isGroup("NOLOGIN"))
        )
        {
            String[] x = {e.getKey(), e.getValue().getDescr()}; 
            res.add( x );
        }
     });
     return res;
  }
  

  
  
  public static TagRuleSet getTagRules() 
     { return _tagrules; }
     
  
  
  /* Action(hideid, hidetrail, hideall, showpath, style) */
  public static void init(AprsServerConfig api) 
  {
      String filename = System.getProperties().getProperty("confdir", ".") + "/view.profiles";
      try {
         // Default is to hide all
         _default.add(Pred.TRUE(), new Action(true,true,true,false,false,false,"",null,-1,-1));
         
         api.log().info("ViewFilter", "Compiling view profiles..");
         Parser parser = new Parser(api, new FileReader(filename), filename);
         parser.parse();
         _map = parser.getProfiles();
         _tagrules = parser.getTagRules();
         api.log().info("ViewFilter", "Done compiling view profiles.");
      }
      catch (FileNotFoundException e)
        { api.log().error("ViewFilter", "File not found '"+filename+"'"); }
  } 
  
  
}
