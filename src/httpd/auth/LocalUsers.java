/* 
 * Copyright (C) 2017 by Øyvind Hanssen (ohanssen@acm.org)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
 
package no.polaric.aprsd.http;

import java.util.*; 
import java.io.Serializable;
import no.polaric.aprsd.*;
import java.io.*;
 
 
 
/**
 * User info (profile) stored locally. 
 * Currently this is used for keeping track of usage. May add more later. 
 */
 
public class LocalUsers {
    
    
    /**
     * User info class. Can be serialized and stored in a file. 
     */
    public static class User implements Serializable {
       private String userid; 
       private Date lastused; 
       transient private boolean active = false;

       public String  getIdent()    { return userid; }
       public Date    getLastUsed() { return lastused; }
       public void    updateTime()  { lastused = new Date(); }
       public boolean isActive()    { return active; }
       public void    setActive()   { active = true; }
       
       public User(String id, Date d)
         {userid=id; lastused=d;} 
    }
    
    
    /** 
     * Area. Can be serialized as JSON and sent to client. 
     */
    public static class Area implements Serializable {
        public int index;
        public int baseLayer;
        public boolean[] oLayers;
        public String name;
        public double[] extent;
    }
    
    
    
    private ServerAPI _api;
    private SortedMap<String, User> _users = new TreeMap<String, User>();
    private Map<String, SortedMap<Integer, Area>> _areas = new HashMap();
    private String _filename; 
    
    private int _nextAreaId = 0;
    
    
    public LocalUsers(ServerAPI api, String fname) {
       _api = api;
       _filename = fname; 
       restore();
    }
  
  
    /**
     * Get a single user. 
     */
    public User get(String id) {
       return _users.get(id); 
    }
  
  
    /**
     * Get all users as a collection.
     */
    public Collection<User> getAll() {
       return _users.values();
    }
    
    
    /**
     * Add a user.
     * @param user - user id. 
     */
    public void add(String user) {
        if (!_users.containsKey(user))
            _users.put(user, new User(user, null));
    }
      
  
    /**
     * Get the areas belonging to a given user.
     * @param id - user id.
     * @return collection of areas. 
     */
    public Collection<Area> getAreas(String id) {
        if (!_areas.containsKey(id)) 
            _areas.put(id, new TreeMap<Integer,Area>());
        return _areas.get(id).values(); 
    }
    
    
    /**
     * Add an area to a given user. 
     * @param id - user id. 
     * @param a - area to be added.
     * @return index of added area. 
     */
    public int addArea(String id, Area a) {
        if (!_areas.containsKey(id)) 
            _areas.put(id, new TreeMap<Integer,Area>());
        SortedMap<Integer,Area> l = _areas.get(id);
        a.index = _nextAreaId; 
        _nextAreaId = (_nextAreaId + 1) % Integer.MAX_VALUE;
        l.put(a.index, a);   
        return a.index;
    }
    
  
    /**
     * Remove an area. 
     * @param id - user id.
     * @param i - index of area. 
     */
    public void removeArea(String id, int i) {
        if (_areas.containsKey(id))
            _areas.get(id).remove(i);
    }
    
    
    /**
     * Store everything in a file. 
     */
    public void save() {
        try {
            _api.log().info("LocalUsers", "Saving user data...");
            FileOutputStream fs = new FileOutputStream(_filename);
            ObjectOutput ofs = new ObjectOutputStream(fs);
            ofs.writeObject(_users); 
            ofs.writeObject(_areas); 
            ofs.writeObject(_nextAreaId);
        }
        catch (Exception e) {
            _api.log().warn("LocalUsers", "Cannot save data: "+e);
        } 
    }
    
    
    /**
     * Restore from a file. 
     */
    public void restore() {
        try {
            _api.log().info("LocalUsers", "Restoring user data...");
            FileInputStream fs = new FileInputStream(_filename);
            ObjectInput ifs = new ObjectInputStream(fs);
            _users = (SortedMap) ifs.readObject();
            _areas = (Map) ifs.readObject();
            _nextAreaId = (Integer) ifs.readObject();
        }
        catch (EOFException e) { }
        catch (Exception e) {
            _api.log().warn("StationDBImp", "Cannot restore data: "+e);
            _users.clear();
        }
    }
    
}
