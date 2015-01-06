/* 
 * Copyright (C) 2012 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import uk.me.jstott.jcoord.*; 
import java.util.*;
import java.io.Serializable;
  

/**
 * Geographic position.
 * Every point has a location
 */
 
public class Point implements Serializable,  Cloneable
{             
    public static final float KNOTS2KMH = (float) 1.853;
    public static final float KNOTS2MPS = (float) 0.5148;
    public static final float FEET2M = (float) 3.2898;

    protected Reference   _position;
      
    public Point (Reference p)
       { _position = p; }
          
    
    /**
     * Test if position is inside of a rectangular area. The area is defined by uleft (upper left corner)
     * and lright (lower right corner). 
     
     * @param uleft Upper left corner of the area. 
     * @param lright Lower right corner of the area. 
     * @param xext Percentage with which to extend the search area in x direction.
     * @param yext Percentage with which to extend the search area in y direction.
     */          
    public boolean isInside(Reference ul, Reference lr, double xext, double yext)
    {   
        LatLng uleft = ul.toLatLng();
        LatLng lright = lr.toLatLng(); 
        
        if (uleft == null || lright == null)
           return false; 
        
        double xoff  = xext * (lright.getLng() - uleft.getLng());
        double yoff = yext * (uleft.getLat() - lright.getLat());
        
        if (_position == null)
           return false;
        try {
           LatLng ref = _position.toLatLng();
           if (lright.getLng() < uleft.getLng()) {
              double xleft = uleft.getLng();
              double xright = 360 + lright.getLng(); 
              double x = ref.getLng() < xleft-xoff ? 360+ref.getLng() : ref.getLng();
              xoff = xext * (xright - xleft); 

              return (x >= xleft-xoff && ref.getLat() >= lright.getLat()-yoff &&
                      x <= xright+xoff  && ref.getLat() <= uleft.getLat()+yoff );
           }
           else
              return ( ref.getLng() >= uleft.getLng()-xoff && ref.getLat() >= lright.getLat()-yoff &&
                       ref.getLng() <= lright.getLng()+xoff && ref.getLat() <= uleft.getLat()+yoff ); 
        }
        catch (Exception e) { return false; }
    }
      
      
     /**
     * Test if position is inside of a rectangular area. The area is defined by uleft (upper left corner)
     * and lright (lower right corner).  
     * @param uleft Upper left corner of the area. 
     * @param lright Lower right corner of the area. 
     */
    public boolean isInside(Reference uleft, Reference lright)
       { return isInside(uleft, lright, 0, 0); }

       
    /** 
     * Return distance (in meters) from another point. 
     */
    public long distance (Point p)
      { return distance(p._position); }
      
      
     /** 
     * Return distance (in meters) from a reference. 
     */
    public long distance(Reference p)
       { return Math.round(_position.toLatLng().distance(p.toLatLng()) * 1000); }
    

       
    public Reference getPosition ()   
       { return _position; } 


    /**
     * Return true if the difference in course between crs and prev is greater than limit. 
     * All arguments given in degrees 
     */
    public static boolean course_change(int crs, int prev, int limit)
    {
        return ( (Math.abs(crs - prev) > limit) &&
           Math.min (Math.abs((crs-360) - prev), Math.abs(crs - (prev-360))) > limit);
    }

}


