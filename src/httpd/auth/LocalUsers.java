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
import java.util.concurrent.*;
import static java.util.concurrent.TimeUnit.*;
 
 
/**
 * User info (profile) stored locally. 
 * Currently this is used for keeping track of usage. May add more later. 
 * Note that there may be users that have not an associated registration here. 
 * There may also be a database and/or users authorized by an external service!!!
 */
 
public class LocalUsers implements UserDb 
{
    
    
    /**
     * User info class. Can be serialized and stored in a file. 
     */
    public class User extends no.polaric.aprsd.http.User {
     
        private Date lastused; 

        @Override public Date    getLastUsed()     { return lastused; }
        @Override public void    updateTime()      { lastused = new Date(); }
        @Override public void setPasswd(String pw) { updatePasswd(getIdent(), pw); }

        
        public User(String id, Date d) {
            super(id); 
            lastused=d;
        } 
            
    }
    
    
    private ServerAPI _api;
    private SortedMap<String, User> _users = new TreeMap<String, User>();
    private String _filename; 
    private GroupDb _groups;
    
    
    // FIXME: Move this to ServerAPI ?
    private final ScheduledExecutorService scheduler =
       Executors.newScheduledThreadPool(1);
    
    
    public LocalUsers(ServerAPI api, String fname, GroupDb gr) {
        _api = api;
        _filename = fname; 
        _groups = gr; 
        restore();
       
        scheduler.scheduleAtFixedRate( () -> 
            {
                try {
                    save();
                }
                catch (Exception e) {
                    _api.log().warn("LocalUsers", "Exception in scheduled action: "+e);
                    e.printStackTrace(System.out);
                }
            } ,2, 2, HOURS);
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
    public Collection<no.polaric.aprsd.http.User> getAll() {
        List<no.polaric.aprsd.http.User> xl = new ArrayList();
        for (no.polaric.aprsd.http.User x: _users.values())
            xl.add(x);
        return xl;
    }
    
    
    
    /**
     * Add a user.
     * @param user - user id. 
     */
    public synchronized User add(String userid) {
        if (!_users.containsKey(userid)) {
            var x = new User(userid, null);
            _users.put(userid, x);
            return x;
        }
        return null;
    }
      
    
    public synchronized 
    User add (String userid, String name, boolean sar, boolean admin, boolean suspend, String passwd, String atr) {
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
        u.setAllowedTrackers(atr);
        return u;
    }
    
    
    
    /**
     * Remove a user. Also remove entry in password-file. 
     */
    public synchronized void remove(String username) {
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
    
    
    
    
    public synchronized boolean updatePasswd(String username, String passwd) {            
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
            else if (res == 7)
                _api.log().warn("LocalUsers", "Couldn't update passwd: Invalid password file");
            else {
                _api.log().warn("LocalUsers", "Couldn't update passwd: Internal server problem");
                BufferedReader bri = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = bri.readLine()) != null)
                    System.out.println("  "+line);
            }
                
                
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
    public synchronized void save() {
        try {
            _api.log().info("LocalUsers", "Saving user data...");

                
            /* Try saving to a text file instead */
            FileOutputStream fs = new FileOutputStream(_filename);
            PrintWriter out = new PrintWriter(fs); 
            out.println("#");
            out.println("# Users file. Saved "+ServerBase.isodf.format(new Date()));
            out.println("#");
            for (User x : _users.values()) {
                out.println(x.getIdent() + "," + 
                    (x.getLastUsed() == null ? "null" : ServerBase.xf.format(x.getLastUsed())) +"," 
                    + false + "," // For compatibility
                    + x.isAdmin() + ","
                    + rmComma( x.getName()) + "," 
                    + rmComma( x.getCallsign()) + ","
                    + rmComma( x.getGroup().getIdent() ) + "," 
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
    public synchronized void restore() {
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
                    String name  = "", callsign = "", group = ""; 

                    sar = ("true".equals(x[2].trim()));
                    admin = ("true".equals(x[3].trim()));
                    if (x.length > 4)
                        name = x[4].trim();
                    if (x.length > 5)
                        callsign = x[5].trim();
                    if (x.length > 6)
                        group = x[6].trim();
                    if (x.length > 7)
                        suspend = ("true".equals(x[7].trim()));
                        
                    Date   lastupd = ("null".equals(lu) ? null : ServerBase.xf.parse(x[1].trim()));
                    User u = new User(userid, lastupd); 
                    u.setAdmin(admin);
                    u.setName(name);
                    u.setCallsign(callsign);
                    u.setGroup(_groups.get(group));
                    u.setSuspended(suspend); 
                    _users.put(userid,u);
                }
            }
        }
        catch (EOFException e) { }
        catch (Exception e) {
            _api.log().warn("LocalUsers", "Cannot restore data: "+e);
            e.printStackTrace(System.out);
            _users.clear();
        }
    }
    
}
