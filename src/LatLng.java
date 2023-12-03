 

package no.polaric.aprsd;
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
