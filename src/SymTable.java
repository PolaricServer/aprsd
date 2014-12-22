package no.polaric.aprsd;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import java.io.*;

/**
 * Symbol to icon mapping.
 */
public class SymTable 
{
    private BufferedReader  _rd;
    private StringTokenizer _next;
    private Map<String, String> _stab = new HashMap(); 
    private ArrayList<Exp> _rtab = new ArrayList();
    
    protected class Exp {
        public String exp; 
        public String file; 
        public Exp(String e, String f) {
           exp=e; 
           file=f; 
        }
    }
    
    
    public SymTable(String file) 
    {
        try {
           _rd = new BufferedReader(new FileReader(file));
           while (_rd.ready())
           {
               String line = _rd.readLine();
               if (!line.startsWith("#")) 
               {               
                   String[] x = line.split("\\s+");  
                   if (x[0].length() < 2) 
                       continue;
                   _rtab.add(new Exp(x[0], x[1]));    
               }
            }     
        }
        catch (Exception e) 
            { System.out.println("SYMLIST WARNING: "+e); }
    } 



    public String getIcon(char sym, char alt)
    {
         String key = "" + alt + sym;
         String icon = _stab.get(key);
         if (icon==null)
            for (Exp e: _rtab)
               if (key.matches(e.exp)) {
                   _stab.put(key, e.file);
                   return e.file; 
               }
         return (icon==null ? null: icon);         
    }
}
