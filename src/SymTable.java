package no.polaric.aprsd;
import java.net.*;
import java.util.*;
import java.io.*;

/**
 * Symbol to icon mapping.
 */
public class SymTable 
{
    private BufferedReader  _rd;
    private StringTokenizer _next;
    private Map<String, String> _stab = new HashMap(); 
  
    
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
                   _stab.put(x[0], x[1]);
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
         return (icon==null ? _stab.get(""+"\\"+sym) : icon);
              
    }
}
