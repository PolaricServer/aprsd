/* Simple LRU Cache */

package no.polaric.aprsd; 
import java.util.*;

public class LRUCache<T> {

    private int _capacity;
    private LinkedHashMap<String, T> _cache;
    
    
    public LRUCache(int capacity)
    {
        _capacity = capacity;
        _cache = new LinkedHashMap<String, T> (capacity + 10)  {
            @Override
            protected boolean removeEldestEntry(Map.Entry eldest) {
                return size() > _capacity;
            }
        };
    }


    public T get(String key)
    {
        T item = _cache.get(key);
        if (item != null) {
            _cache.remove(key);
            _cache.put(key, item);
        }
        return item;
    }
    
    
    public void remove(String key)
    {
        _cache.remove(key);
    }
    
    
    public void put(String key, T item)
         { _cache.put(key, item); }
 }

 
