  
/* 
 * Copyright (C) 2021- by Øyvind Hanssen (ohanssen@acm.org)
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




public interface GroupDb 
{
    public Group get(String gid); 
    public Collection<Group> getAll();
    
    public User add (String gid);
    public User add (String userid, String name, boolean sar);
    public void remove(String gid);
}

