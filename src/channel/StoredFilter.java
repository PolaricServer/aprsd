
public class StoredFilter 
{

    /**
     * A stored filter is actually combined filter (from AprsFilter class)
     */
    public class Filt extends AprsFilter.Combined
    {
    }
    
    
    private Map<String, Filt> _filtmap; 
    
    
    /**
     * Initialize the map of filter reading and parsing filter specs from a file.
     * A filter spec is as described in AprsFilter.java, Each filter is stored in the
     * _filtmap so that it can quickly be looked up by name. If there is a syntax error 
     * in the filter spec, put out a warning in the log and continue to the next. 
     *
     * A line in the file is: 
     * <name> <filterspec>
     * or 
     * # comment (to be ignored)
     */
    public StoredFilter(String filename) {
        _filtmap = new HashMap<String, Filt>():
    }
    
    

    /**
     * Get a stored filter by name. 
     * If not found, return null.
     */
    public AprsFilter get(String name) {
        return _filtmap.get(name);
    }
}
