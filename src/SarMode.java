/* 
 * Copyright (C) 2010 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
package no.polaric.aprsd;
import java.util.*;

/**
 * Search and rescue mode.
 */
public class SarMode 
{ 
    private String _reason;
    private String _user;
    private Date   _time;
    
    public SarMode(String r, String u)
     { _reason = r; _user = u; _time = new Date(); }
     
    public String getReason()
       { return _reason; }
       
    public String getUser()
       { return _user; }
       
    public Date getTime() 
       { return _time; }
       
    public String toString() 
      { return "Aktivert av: "+_user; }
}
