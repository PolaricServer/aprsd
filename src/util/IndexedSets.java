 
package no.polaric.aprsd; 
import java.io.*;
import java.util.*;
 
 
class IndexedSets {
    private HashMap<String, Set<String>> _map = new HashMap();
    
    public void add(String key, String val) {
        Set<String> s = _map.get(key); 
        if (s==null) {
            s = new HashSet<String>();
            _map.put(key, s);
        }
        s.add(val);
    }

    public void remove(String key, String val) {
        Set<String> s = _map.get(key);
        if (s==null)
            return;
        s.remove(s);
        if (s.isEmpty())
        _map.remove(key);
    }
    
    public void removeAll(String key) {
        _map.remove(key);
    }
    
    public void removeMatching(String key, String ex) {
        Set<String> s = _map.get(key);
        s.removeIf( (x)-> x.matches(ex) );
    }
    
    
    public Set<String> get(String key) {
        Set<String> s = _map.get(key);
        return s;
    }
    
    
    public List<String> getAll() {
        List<String> res = new ArrayList();
        for (Set<String> s : _map.values())
            for (String x : s)
                res.add(x); 
        return res;
    }
    
    
    public boolean contains(String key, String val) {
        Set<String> s = _map.get(key);
        return s.contains(val);
    }
    
    
}

