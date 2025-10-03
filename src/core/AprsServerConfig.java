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
 */
 
 
package no.polaric.aprsd;
import no.polaric.aprsd.aprs.*;
import no.polaric.core.*;
import no.polaric.aprsd.channel.*;
import java.util.*;



/**
 * Server config interface for polaric-aprsd. Extends the ServerConfig interface. 
 */

public interface AprsServerConfig extends ServerConfig
{
    
       
    /** Get software version. */
    public String getVersion();
   
   
    /** Get data interface. */
    public StationDB getDB();

    
    /** Set data interface */
    public void setDB(StationDB db);

   
    /** Get handler for own position */
    public OwnPosition getOwnPos(); 
   
   
    /** Get handler for own objects */
    public OwnObjects getOwnObjects(); 
    
   
    /** Get channel manager interface. */
    public Channel.Manager getChanManager(); 
   
         
    /** Get message processor interface. */
    public MessageProcessor getMsgProcessor(); 
      
      
    /** Get bulletin board */
    public BullBoard getBullBoard(); 
      
    
    /** Get handler for remote control. */
    public RemoteCtl getRemoteCtl(); 
   
   
    /** Get igate. */
    public Igate getIgate();
    
    
    /** Replace current internet channel in igate, etc.. */   
    public void setInetChannel(AprsChannel ch);
      
      
    /** Return current internet channel in igate, etc.. */
    public AprsChannel getInetChannel();
    

    /** Replace current RF channel in igate, etc.. */   
    public void setRfChannel(AprsChannel ch);
      
      
    /** Return current RF channel in igate, etc.. */
    public AprsChannel getRfChannel();
   
   
    /** Get default to-address for APRS packet to be sent. */
    public String getToAddr();
   
   
    /** Get APRS parser */
    public AprsParser getAprsParser();
    
    
    /** Save config. Note that this is destructive and should only be called
     * at shutdown. 
     */
    public void saveConfig();
    

}

