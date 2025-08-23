/* 
 * Copyright (C) 2025 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package no.polaric.aprsd.point;
import java.io.Serializable;


 /* Simple class for lat long coordinates */
 
 public class LatLng implements Serializable {
    private double _lat, _lng;
    public double getLat() {return _lat;}
    public double getLng() {return _lng;}
  
 
    public double distance(LatLng ll) {
        double er = 6366.707;

        double latFrom = Math.toRadians(getLat());
        double latTo = Math.toRadians(ll.getLat());
        double lngFrom = Math.toRadians(getLng());
        double lngTo = Math.toRadians(ll.getLng());

        double d =
            Math.acos(Math.sin(latFrom) * Math.sin(latTo) + Math.cos(latFrom)
                * Math.cos(latTo) * Math.cos(lngTo - lngFrom))
                * er;
        return d;
    }
 

  
  
    public LatLng(double lat, double lng) {
        _lat=lat; _lng=lng; 
    }
 }
