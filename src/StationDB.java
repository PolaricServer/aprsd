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
import java.util.List;  
import uk.me.jstott.jcoord.*;


public interface StationDB
{
    public int nItems(); 
    
    public RouteInfo getRoutes();
    
    public OwnObjects getOwnObjects(); 
    
    public MessageProcessor getMsgProcessor();
    
    public AprsPoint getItem(String id);
    
    public void removeItem(String id);

    public Station getStation(String id);
    
    public Station newStation(String id);
    
    public AprsObject newObject(Station owner, String id);
    
    
    public List<AprsPoint> getAll(String arg);
    
    public List<AprsPoint>
          search(Reference x1, Reference y1, Reference x2, Reference y2);
    
    public List<AprsPoint>
          search(UTMRef uleft, UTMRef lright);       
    
    public void save(); 
    public void garbageCollect();

}
