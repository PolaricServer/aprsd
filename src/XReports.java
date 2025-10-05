
package no.polaric.aprsd;
import no.polaric.aprsd.point.*;
import java.util.*;
import java.io.*;
import java.text.*;




/**
 * Extra reports to be added to APRS pos reports. 
 */
 
public class XReports 
{
    public static class XRep {
        long timestamp;
        double latitude;
        double longitude;
        
        public XRep(Date ts, double lat, double lon) {
            timestamp=ts.getTime();
            latitude=lat;
            longitude=lon;
        }
        public XRep(Date ts, LatLng pos) {
            timestamp=ts.getTime(); 
            latitude = pos.getLat();
            longitude = pos.getLng();
        }
    }
    
    
    private static final char[] b64tab =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
	
	
	
    /*******************************************************
     * Posreport buffer - a queue of positions. 
     *  
     *   - putPos - add a position and remove the 
     *     oldest if necessary.
     *   - getPos - remove and return the oldest position
     *   - empty  - return true if empty
     *******************************************************/

    private static final int MAX_BUFFERPOS = 5;
    private static final int NBUFFERS = 10;

    public static class PosBuf {
        private XRep buf[] = new XRep[MAX_BUFFERPOS];
        private int nPos = 0; 
        private int nextPos = 0;
        
        public void putPos(XRep p) {
            if (nPos < MAX_BUFFERPOS)
                nPos++;
            buf[nextPos] = p;
            nextPos = (nextPos + 1) % MAX_BUFFERPOS;
        }


        public XRep getPos() {
            int i = nPos <= nextPos ? 
                nextPos - nPos : MAX_BUFFERPOS + (nextPos - nPos);
            nPos--;
            return buf[i];
        }


        public int nPos() {
            return nPos;
        }

        public boolean empty() {
            return (nPos == 0);
        }
    } 

    
        
    private PosBuf[] buffers = new PosBuf[NBUFFERS];
    private int firstbuf = 0;
    
    
    
    public XReports() {
        for (int i=0; i<NBUFFERS; i++)
            buffers[i] = new PosBuf();
    }


    public void rotateBuf() {
        firstbuf = (firstbuf+1) % NBUFFERS;
    }

    
    public PosBuf getBuf(int i) {
        return buffers[ (i+firstbuf) % NBUFFERS ];
    }



    /********************************************************************
    * Queue extra-reports for later transmissions 
    * n is the how long to wait (number of transmissions) to send it
    * 0 means next packet, 1 means the packet after the next packet, etc
    ********************************************************************/

    public void enqueue(XRep pos, int n) {
        PosBuf buf = getBuf(n);
        buf.putPos(pos);
    }




   /*********************************************************************
    * Add extra-reports onto this transmission 
    *********************************************************************/

    public String encode(XRep prev) {
        PosBuf buf = getBuf(0);
        String res = "";
       /* 
        * Use deltas for timestamp (12 bit unsigned), latitude and longitude 
        * (18 bit signed number). Base64 encode these numbers. 
        * This generates 8 characters per record. 
        */
        if (!buf.empty()) 
            res += "/*";
        while (!buf.empty()) {
            XRep pos = buf.getPos();
            int ts_delta  = (int) pos.timestamp/1000 - (int) prev.timestamp/1000;
            int lat_delta = (int) Math.round((pos.latitude - prev.latitude) * 100000); 
            int lng_delta = (int) Math.round((pos.longitude - prev.longitude) * 100000);
            res += b64from12bit(ts_delta); 
            res += b64from18bit(signed18bit(lat_delta));
            res += b64from18bit(signed18bit(lng_delta));
        }
        rotateBuf();
        return res;
    }

    
    
    
    /********************************************************************
     * Create a 18 bit signed number
     ********************************************************************/

    public int signed18bit(int x) {
        if (x < 0)
            return (x & 0x1ffff) | 0x20000;
        else 
            return x & 0x1ffff;
    }


    /********************************************************************
     * Base64 encode a 12 bit binary value
     ********************************************************************/

    public String b64from12bit(int x) {
        String res = "";
        byte ls = (byte) (x & 0x003f); 
        byte ms = (byte) (x>>6 & 0x003f);
        res += b64tab[ms];
        res += b64tab[ls];
        return res;
    }


    /********************************************************************
     * Base64 encode a 18 bit binary value (no padding - returns 3 chars)
     ********************************************************************/

    public String b64from18bit(int x) {
        String res = "";
        byte ms = (byte) (x>>12 & 0x003f);
        res += b64tab[ms];
        res += b64from12bit(x);
        return res;
    }
    

}
