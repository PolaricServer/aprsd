 
/* 
 * Copyright (C) 2018-21 by Øyvind Hanssen (ohanssen@acm.org)
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


/**
 * User info that can be stored on file. 
 */
public abstract class User {

    private String userid; 
    private String name = "";
    private String callsign = "";
    
    public String  getIdent()             { return userid; }
    public void    setName(String n)      { name = n; }
    public String  getName()              { return name; }
    public void    setCallsign(String c)  { callsign = c.toUpperCase(); }
    public String  getCallsign()          { return callsign; }
    
    public abstract Date    getLastUsed();
    public abstract void    updateTime();
    public abstract void    setPasswd(String pw);
    
    /* 
     * Group membership and authorisations
     * These flags are now stored in this class. 
     */
    private Group group = Group.DEFAULT;
    private boolean admin=false;
    private boolean suspended = false; 
    private String trackerAllowed = "";
    
    public boolean isSar()                      { return group.isSar(); }
    public final Group getGroup()               { return group; }
    public final void setGroup(Group g)         { group = g; } 
    public final void setSar(boolean s)         { } 
       /* SetSar will soon go away  - replace with setGroup */
       
    public boolean isAdmin()                    { return admin; }
    public final void setAdmin(boolean a)       { admin=a; }
    public final boolean isSuspended()          { return suspended; }
    public final void setSuspended(boolean s)   { suspended = s; }
    public String getAllowedTrackers()          { return trackerAllowed; }
    public void setAllowedTrackers(String expr) { trackerAllowed = expr; }
    
    protected User(String id)
        { userid=id; }
        
    public User() {}
    
        
}
