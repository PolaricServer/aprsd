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

package no.polaric.aprsd.http;
import no.polaric.aprsd.*;
import java.util.*;


public class ViewFilter {
 
 protected boolean isSymbol(AprsPoint x, char sym, char symtab)
     { return x.getSymbol() == sym && (symtab == '*' || x.getSymtab() == symtab); }
  
  protected boolean isNumberSym(AprsPoint x)
     { return x.getSymbol() >= '0' && x.getSymbol() <= '9' && x.getSymtab() == '/'; }
     
     
  public boolean useObject(AprsPoint x)
       { return true; } 
       
  public boolean showPath(AprsPoint x)
       { return false; }  
  
  public boolean showIdent(AprsPoint x)
       { return !x.isLabelHidden(); }


  public static class Callsign extends ViewFilter
  {      
      protected String _call;
       
      public Callsign(String c)
         { _call = c; }
         
        public boolean useObject(AprsPoint x)
          { return x.getIdent().matches(_call); }
  }
  
  
  
  public static class Tracking2 extends ViewFilter
  {  
       public boolean showIdent(AprsPoint x)
           { return !(x instanceof AprsObject) || 
                    ( !isSymbol(x, 'j', '\\')    &&
                      !isSymbol(x, 'n', '\\'))   
                      ||
                    ( !((AprsObject)x).getOwner().getIdent().equals("LA1FTA-14") && 
                      !((AprsObject)x).getOwner().getIdent().equals("LA4JAA-2")) ;   }
              
           
  }
  

   
  public static class Tracking extends ViewFilter
  {  
       public boolean showIdent(AprsPoint x)
           { return !x.isLabelHidden() &&                       /* Not explicitly hidden AND */
                    ( _map.get("moving").useObject(x) ||        /* moving, OR ... */
                      isSymbol(x, '!', '*') || isSymbol(x, '\'', '*') || isSymbol(x, 'R', '/') ||
                      isSymbol(x, '(', '*') || isSymbol(x, '*', '/')  || isSymbol(x, '+', '/') ||
                      isSymbol(x, ',', '/') || isSymbol(x, ':', '/')  || isSymbol(x, '<', '/') ||
                      isSymbol(x, '>', '*') || isSymbol(x, 'F', '/')  || isSymbol(x, 'P', '/') ||
                      isSymbol(x, 'U', '*') || isSymbol(x, 'X', '*')  || isSymbol(x, 'Y', '/') ||
                      isSymbol(x, '[', '/') || isSymbol(x, '^', '*')  || isSymbol(x, 'a', '/') ||
                      isSymbol(x, 'b', '/') || isSymbol(x, 'c', '/')  || isSymbol(x, 'e', '/') ||
                      isSymbol(x, 'f', '/') || isSymbol(x, 'f', '/')  || isSymbol(x, 'k', '*') ||
                      isSymbol(x, 'p', '/') || isSymbol(x, 's', '/')  || isSymbol(x, 'u', '*') ||
                      isSymbol(x, 'v', '*') ); }
  }
  
  
  public static class Infra extends ViewFilter 
  {
      private String _call;
      
      public Infra(String c)
         { _call = c; }
                
      public boolean showPath(AprsPoint x)
         { return (_call == null); }
          
      public boolean useObject(AprsPoint x)
         { return  x.isInfra() || 
             (_call == null ? false : x.getIdent().matches(_call) || x.getSymbol() == '#' ||
               (x.getSymbol() == 'r' && x.getSymtab() == '/')); }
  }       
  
  
  public static class Moving extends ViewFilter
  {
      public boolean useObject(AprsPoint x)
         { return ((x instanceof Station) ? !((Station) x).getHistory().isEmpty() : false); }
  }
    
    
  private static Map<String, ViewFilter> _map = new HashMap();
  public static ViewFilter getFilter(String id)
  {
     ViewFilter x  = _map.get(id);
     if (x==null) 
        return new ViewFilter();
     return x;
  }    
  
  static {
      _map.put("all", new ViewFilter());
      _map.put("track", new Tracking());
      _map.put("track2", new Tracking2());
      _map.put("le", new Callsign("LE.*"));
      _map.put("infra", new Infra("LD.*"));
      _map.put("ainfra", new Infra(null));
      _map.put("moving", new Moving());
  }
}
