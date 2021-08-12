/* 
 * Copyright (C) 2018-19 by Øyvind Hanssen (ohanssen@acm.org)
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
import java.text.*;
 
 
 
/**
 * User info (profile) stored locally. 
 * Currently this is used for keeping track of usage. May add more later. 
 * Note that there may be users that have not an associated registration here. 
 * There may also be a database and/or users authorized by an external service!!!
 */
 
public class LocalUsers {
    
    
    /**
     * User info class. Can be serialized and stored in a file. 
     */
    public class User extends no.polaric.aprsd.http.User implements Serializable {
     
        private String name = "";
        private String callsign = "";
        private String trackerAllowed = "";
        private Date lastused; 
        private boolean suspended = false; 
        transient private boolean active = false;

        public Date    getLastUsed()           { return lastused; }
        public void    updateTime()            { lastused = new Date(); }
        public boolean isActive()              { return active; }
        public void    setActive()             { active = true; }
        public boolean isSuspended()           { return suspended; }
        public void    setSuspended(boolean s) { suspended = s; }
        public void    setName(String n)       { name = n; }
        public String  getName()               { return name; }
        public void    setCallsign(String c)   { callsign = c.toUpperCase(); }
        public String  getCallsign()           { return callsign; }
        public void    setPasswd(String pw)    { updatePasswd(getIdent(), pw); }
        public String  getAllowedTrackers()    { return trackerAllowed; }
        
        /* 
         * These flags are now stored in this class in addition to 
         * using a regular expression in the config. The config regex will go away. 
         */
        private boolean sar=false, admin=false;
        
        @Override public boolean isSar()      { return sar; }
        @Override public boolean isAdmin()    { return admin; }
        public final void setSar(boolean s)   { sar=s; }
        public final void setAdmin(boolean a) { admin=a; }

    
        public void setTrackerAllowed(String expr) {
            trackerAllowed = expr;
        }
        
        
        
        public User(String id, Date d) {
            super(id); 
            lastused=d;
            sar = isSar(); 
            admin = isAdmin();
        } 
            
    }
    
    
    
    
    private ServerAPI _api;
    private SortedMap<String, User> _users = new TreeMap<String, User>();
    private String _filename; 

    
    
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
    public User add(String userid) {
        if (!_users.containsKey(userid)) {
            var x = new User(userid, null);
            _users.put(userid, x);
            return x;
        }
        return null;
    }
      
    
    public User add(String userid, String name, boolean sar, boolean admin, boolean suspend, String passwd, String atr) {
        User u = add(userid); 
        if (u==null) {
            _api.log().info("LocalUsers", "add: user '"+userid+"' already exists");
            return null;
        }
        if (!updatePasswd(userid, passwd)) {
            remove(userid); 
            return null; 
        }
        u.setName(name);
        u.setSar(sar);
        u.setAdmin(admin);
        u.setSuspended(suspend);
        u.setTrackerAllowed(atr);
        return u;
    }
    
    
    
    /**
     * Remove a user. Also remove entry in password-file. 
     */
    public void remove(String username) {
        _users.remove(username);
        _api.log().debug("LocalUsers", "remove: user '"+username+"'");
         var cmd = "/usr/bin/sudo /usr/bin/htpasswd -D /etc/polaric-aprsd/passwd "+username;
         try {
            var p = Runtime.getRuntime().exec(cmd);
            var res = p.waitFor();
             
            if (res == 0) {
                ((WebServer) _api.getWebserver()).getAuthConfig().reloadPasswds();
                _api.log().info("LocalUsers", "Password deleted for user: '"+username+"'"); 
                return;
            }
            else 
                _api.log().warn("LocalUsers", "Couldn't delete passwd: error="+res);
        } catch (IOException e) {
            _api.log().warn("LocalUsers", "Couldn't delete passwd: "+e.getMessage());
        } catch (InterruptedException e) {}
        
    }
    
    
    
    
    public boolean updatePasswd(String username, String passwd) {            
        var cmd = "/usr/bin/sudo /usr/bin/htpasswd -b /etc/polaric-aprsd/passwd "+username+" "+passwd;
        try {
            var p = Runtime.getRuntime().exec(cmd);
            var res = p.waitFor();
             
            if (res == 0) {
                ((WebServer) _api.getWebserver()).getAuthConfig().reloadPasswds();
                _api.log().info("LocalUsers", "Password updated for user: '"+username+"'"); 
                return true;
            }
            else if (res == 5)
                _api.log().warn("LocalUsers", "Couldn't update passwd: Input is too long");
            else if (res == 6)
                _api.log().warn("LocalUsers", "Couldn't update passwd: Input contains illegal characters");
            else 
                _api.log().warn("LocalUsers", "Couldn't update passwd: Internal server problem");
        } catch (IOException e) {
            _api.log().warn("LocalUsers", "Couldn't update passwd: "+e.getMessage());
        } catch (InterruptedException e) {}
        return false; 
    }
    
    
    
    private String rmComma(String x) {
        return x.replaceAll("\\,", ""); 
    }
    

    /**
     * Store everything in a file. 
     */
    public void save() {
        try {
            _api.log().info("LocalUsers", "Saving user data...");

            /* Try saving to a text file instead */
            FileOutputStream fs = new FileOutputStream(_filename);
            PrintWriter out = new PrintWriter(fs); 
            
            for (User x : _users.values()) {
                out.println(x.getIdent() + "," + 
                    (x.getLastUsed() == null ? "null" : ServerBase.xf.format(x.getLastUsed())) +"," 
                    + x.isSar() + ","
                    + x.isAdmin() + ","
                    + rmComma( x.getName()) + "," 
                    + rmComma( x.getCallsign()) + ","
                    + rmComma( x.getAllowedTrackers()) + "," 
                    + x.isSuspended() )
                    ;
            }
            out.flush(); 
            out.close();
        }
        catch (Exception e) {
            _api.log().warn("LocalUsers", "Cannot save data: "+e);
            e.printStackTrace(System.out);
        } 
    }
    
    
    /**
     * Restore from a file. 
     */
    public void restore() {
        try {
            _api.log().info("LocalUsers", "Restoring user data...");
            BufferedReader rd = new BufferedReader(new FileReader(_filename));
            while (rd.ready() )
            {
                /* Read a line */
                String line = rd.readLine();
                if (!line.startsWith("#") && line.length() > 1) {
                    String[] x = line.split(",");  
                    String userid = x[0].trim();
                    String lu = x[1].trim();
                    boolean sar, admin, suspend=false;
                    String name  = "", callsign = "", tallow = ""; 

                    sar = ("true".equals(x[2].trim()));
                    admin = ("true".equals(x[3].trim()));
                    if (x.length > 4)
                        name = x[4].trim();
                    if (x.length > 5)
                        callsign = x[5].trim();
                    if (x.length > 6)
                        tallow = x[6].trim();
                    if (x.length > 7)
                        suspend = ("true".equals(x[7].trim()));
                        
                    Date   lastupd = ("null".equals(lu) ? null : ServerBase.xf.parse(x[1].trim()));
                    User u = new User(userid, lastupd); 
                    u.setSar(sar);
                    u.setAdmin(admin);
                    u.setName(name);
                    u.setCallsign(callsign);
                    u.setTrackerAllowed(tallow);
                    u.setSuspended(suspend); 
                    _users.put(userid,u);
                }
            }
        }
        catch (EOFException e) { }
        catch (Exception e) {
            _api.log().warn("LocalUsers", "Cannot restore data: "+e);
            _users.clear();
        }
    }
    
}
