/* 
 * Copyright (C) 2015-2025 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
 */

package no.polaric.aprsd;
import no.polaric.aprsd.point.*;
import java.util.regex.*;
import java.util.Date;


/**
 *  APRS packet.
 */ 
public class AprsPacket implements Cloneable {
    
    private static Pattern _ppat = Pattern.compile
       ("([\\w\\-]+)>([\\w\\-]+)(((,[\\w\\-]+\\*?))*):(.*)");
       
       
    /* If packet is gated or routed elsewhere, the original via
     * can be saved in via_orig. If it is a thirdparty packet, more
     * info about source packet is in from_orig and to_orig.
     */
    public Date time; 
    public Source source;
    public char type;
    public String from, to, msgto, via, report; 
    public String from_orig, to_orig, via_orig; 
    public boolean thirdparty = false; 

    
    public AprsPacket() 
       { time = new Date(); }
    

    
    @Override public AprsPacket clone() 
        { try { return (AprsPacket) super.clone();}
          catch (Exception e) {return null; } 
        }
    
        
    
     
    /**
     * Convert text string to packet structure. 
     */
    public static AprsPacket fromString(String packet)
    {
        packet = packet.replace('\uffff', ' ');
        if (packet == null || packet.length() < 10)
           return null;
        Matcher m = _ppat.matcher(packet);
        if (m.matches())
        {
            AprsPacket p = new AprsPacket();
            p.from_orig = p.from = m.group(1).trim().toUpperCase();
            p.to_orig = p.to = m.group(2).trim().toUpperCase();
            
            p.via  = m.group(3);
            p.report  = m.group(m.groupCount());
                
            /* Remove first comma in path */
            if (p.via != null) 
                  p.via = p.via.trim();
            while (p.via != null && p.via.length() > 0 && p.via.charAt(0) == ',')
                  p.via = p.via.substring(1);
            return p;
        }
        return null;
    }

    
    /**
     * Get Q-code from via part 
     */
    public String[] getQcode() {
        return AprsUtil.getQcode(this);
    }
    
    
    /**
     * Set q-code in via part. 
     * if one exists, replace it. 
     */
    public void setQcode(String q, String call) {
        via = via.replaceAll("qA[CXUoOSrRZI](,(\\s)*.+)?", "");
        via += ((via.equals("") ? "" : ",") + q + ","+call);
    }
    
    
    /**
     * Get position as point. 
     */
    public Point getPos() {
        return AprsUtil.getPos(this); 
    }
    
    
    public String toString() {  
        String msg = from+">"+to +
            ((via != null && via.length()>0) ? ","+via : "") + ":" + report;
                  
        if (thirdparty)
            return from_orig+">"+to_orig +
               ((via_orig != null && via_orig.length()>0) ? ","+via_orig : "") + ":}" + msg;
        else     
            return msg; 
    }
}
    
