 
package no.polaric.aprsd; 
import java.io.*;
import java.util.*;
 

/**
 * Keyed set. For each key (in the map) we have a set of strings. 
 */
 
class KeyedSet {
    private HashMap<String, Set<String>> _map = new HashMap<String, Set<String>>();
    
    /**
     * Add a value to a given key. 
     */
    public void add(String key, String val) {
        Set<String> s = _map.get(key); 
        if (s==null) {
            s = new HashSet<String>();
            _map.put(key, s);
        }
        s.add(val);
    }

    /**
     * Under a given key, remove a given value. 
     */
    public void remove(String key, String val) {
        Set<String> s = _map.get(key);
        if (s==null)
            return;
        s.remove(val);
        if (s.isEmpty())
            _map.remove(key);
    }
    
    
    /**
     * Remove everything.. 
     */
    public void removeAll(String key) {
        _map.remove(key);
    }
    
    
    /**
     * Under a given key, remoe all values that match. 
     */
    public void removeMatching(String key, String ex) {
        Set<String> s = _map.get(key);
        if (s==null)
            return; 
        s.removeIf( (x)-> x.matches(ex) );
    }
    
    
    /**
     * Return the set for a given key.
     */
    public Set<String> get(String key) {
        Set<String> s = _map.get(key);
        return s;
    }
    
    
    /**
     * Get all values. 
     */
    public List<String> getAll() {
        List<String> res = new ArrayList<String>();
        for (Set<String> s : _map.values())
            for (String x : s)
                res.add(x); 
        return res;
    }
    
    
    /**
     * Returns true if the set for key contains val. 
     */
    public boolean contains(String key, String val) {
        Set<String> s = _map.get(key);
        if (s==null)
            return false;
        return s.contains(val);
    }
    
    
}

