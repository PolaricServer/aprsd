package no.polaric.aprsd;
import java.net.*;
import java.util.*;
import java.io.*;


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
                   if (x[0].length() < 2 || (x[0].charAt(0) != '\\' && x[0].charAt(0) != '/'))
                       continue;
                   _stab.put(x[0], x[1]);
               }
            }     
        }
        catch (Exception e) 
            { System.out.println("SYMLIST WARNING: "+e); }
    } 



    public String getIcon(char sym, boolean alt)
    {
         String key = "" + (alt ? '\\' : '/') + sym;
         return _stab.get(key);
    }
}
