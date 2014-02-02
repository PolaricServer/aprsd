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

package no.polaric.aprsd.http;
import no.polaric.aprsd.*;
import no.polaric.aprsd.filter.*;
import java.util.*;
import java.io.*; 



public class ViewFilter {
    
  private static Map<String, RuleSet> _map = new HashMap();
  
  
  public static RuleSet getFilter(String id)
  {
     RuleSet x  = _map.get(id);
     if (x==null) 
        return new RuleSet();
     return x;
  }    
  
  
  
  /* Action(hideid, hidetrail, hideall, showpath, style) */
  static {
      String filename = System.getProperties().getProperty("confdir", ".") + "/view.profiles";
      try {
         System.out.println("*** Compiling view profiles..");
         Parser parser = new Parser(new FileReader(filename));
         parser.parse();
         _map = parser.getProfiles();
      }
      catch (FileNotFoundException e)
        { System.out.println("ERROR: file not found '"+filename+"'"); }
  } 
}