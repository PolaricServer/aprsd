package no.polaric.aprsd;
import java.net.*;
import java.util.*;
import java.io.*;

/**
 * Colour alternatives for trails.
 */
public class ColourTable 
{
    BufferedReader  _rd;
    StringTokenizer _next;
    List<String> list = new ArrayList();
    Iterator<String> it = null;
    
    
    public ColourTable(String file) 
    {
        try {
           _rd = new BufferedReader(new FileReader(file));
           while (_rd.ready())
           {
               String line = _rd.readLine();     
               if (line.matches("([0-9a-fA-F]{6}) ([0-9a-fA-F]{6})"))           
                  list.add(line); 
            }   
           it = list.iterator(); 
        }
        catch (Exception e) { System.out.println("COLOURLIST WARNING: "+e); }
    } 



    public String[] nextColour()
    {
         if (list.isEmpty()) 
             return new String[] {"000000","ff0000"}; 
         if (! it.hasNext()) 
             it = list.iterator();
         return it.next().split(" ");
    }
}
