package no.polaric.aprsd;
import java.util.*;

/* experimental. Consider creating an API subpackage */

/**
 * Manage plugins (identified by class name). 
 */
public class PluginManager
{

   public static class PluginError extends RuntimeException 
   { 
      public PluginError(String msg, Throwable cause) 
        {super(msg, cause);} 
   }
     
     

   /**
    * Plugin start/stop interface. All plugins should implement this. 
    */
   public interface Plugin extends ManagedObject 
   {
      /** Return an array of other component (class names) this plugin depends on. */
       public String[] getDependencies();
       
       /** Return a description of plugin */
       public String getDescr();
   }
   
  

    /* Classname-Plugin map */
   private static Map<String, Plugin> _plugins = new HashMap<String, Plugin>();
    
    /* Name-Object map (objects posted by plugin to be used by core or other plugins) */
   private static Map<String, Object> _properties = new HashMap<String, Object>();
   
   private static ServerAPI api;

   /* FIXME: Shorter path to get/put methods */
   public static Map<String, Object> properties() 
     { return _properties; }
      
      
   public static boolean isEmpty() {
      return _plugins.isEmpty();
   }
   
   /** Get interfaces of all registered plugins. */
   public static Plugin[] getPlugins()
      { Object[] x = _plugins.values().toArray(); 
        Plugin[] y = new Plugin[x.length];
        int i = 0;
        for (Object p: x)
          y[i] = (Plugin) x[i++];
        return y;
      }

      
      
   /**
    * set the api. Must be done at startup of server. 
    * @param a the server interface. 
    */    
   public static void setServerApi(ServerAPI a)
     { api = a; }  
     

     
   /**
    *  Register a plugin. 
    * <ul>
    *  <li>Instantiate plugin class.</li> 
    *  <li> Check dependencies. A plugin declares what other plugins it depends on. 
    *     Use identifiers or simply class  name? Check if those are loaded and
    *     add and activate if not. throw an exception if not found.</li>
    * </ul>
    * 
    *  @param cn Name of plugin. Java class. 
    */
    public static void add(String cn) throws PluginError
    {
        cn = cn.trim();
        try {
          Plugin p = _plugins.get(cn);
          if (p != null)
             return;
           
          /* Load Plugin class and instantiate */
          Class cls = Class.forName(cn); 
          p = (Plugin) cls.newInstance();
        
          for (String cx : p.getDependencies())
             add(cx);
          p.activate(api);
          _plugins.put(cn, p);
        }
        catch (Exception e)
          { throw new PluginError("Cannot activate plugin: "+cn, e); }
    }
    
 
 
    /**
     * Register multiple plugins. 
     * @param cn Comma separated list of plugin names (Java class names).
     */
    public static void addList(String cn) throws PluginError
    {
        if ("".equals(cn))
           return;
        String[] plugins = cn.split(",(\\s)*");
        for (String x : plugins)
           add(x);
    }
 
 
 
    public static void deactivateAll()
    {
       for (Plugin p : _plugins.values())
         p.deActivate();
    }
    
 
}

