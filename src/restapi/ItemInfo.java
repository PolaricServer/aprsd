

package no.polaric.aprsd;
 
public interface ItemInfo {

    public static class Alias {
        public String alias; 
        public String icon; 
        
        public Alias() 
          {}
        public Alias(String a, String ic)
          {alias=a; icon=ic; }
    }
    
    
    public static class Item {
        public String ident;
        public String alias;
        public String description;
        public long[] pos;
    }
}
