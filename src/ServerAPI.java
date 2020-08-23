/*
 * Copyright (C) 2016 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
 * This is the main API. The interface to the core server application - to be used by
 * plugins 
 */
public interface ServerAPI 
{ 

    @FunctionalInterface public interface SimpleCb {
        void cb(); 
    }
            
            
            
   /** 
    * Notifcation content class. 
    */
    public class Notification {
        public String type; 
        public String from;
        public String text; 
        public Date time; 
        public int ttl;
        public Notification (String t, String frm, String txt, Date tm, int tt)
            { type = t; from = frm; text=txt; time=tm; ttl = tt; }
    }
  
  
    /** 
     * Interface to web server. 
     * FIXME: Consider subtyping this in http package. 
     */
    public interface Web {
        public void corsEnable(String uri); 
        public long nVisits();
        public int  nClients();
        public int  nLoggedin();
        public long nHttpReq(); 
        public long nMapUpdates(); 
 
        public void notifyUser(String user, Notification not);
        public PubSub getPubSub();
        public void protectUrl(String prefix);
        
        public void start() throws Exception; 
        public void stop() throws Exception;
    }
  
    /* FIXME: Should we have a interface for user or for notifications? */
  
  
    public interface PubSub {
        /** Post a message to a room (text is prefixed with the room name) */
        public void putText (String rid, String msg);
        
        /** Post a object to a room (JSON encoded) */
        public void put(String rid, Object obj);
    }
  
  
    /* Now, what methods do we need here? Other interfaces.
     * Do we need StationDB? */
    
    public Logfile log();
   
   
    public Web getWebserver(); 
  
  
    /** Get data interface. */
    public StationDB getDB();


    /** Get APRS parser */
    public AprsParser getAprsParser();

   
    /** Get channel manager interface. */
    public Channel.Manager getChanManager(); 
   
   
    /** Get igate. */
    public Igate getIgate();
   
   
    /** Get message processor interface. */
    public MessageProcessor getMsgProcessor(); 
    
    
    /** Get bulletin board */
    public BullBoard getBullBoard(); 
      
      
    /** Replace current internet channel in igate, etc.. */   
    public void setInetChannel(AprsChannel ch);
      
      
    /** Return current internet channel in igate, etc.. */
    public AprsChannel getInetChannel();

   
   
    /** Add HTTP handlers (webservices).
     * A http handler is a class/object that offers a set of methods to handle
     * incoming HTTP requests. A method with prefix <code>handle_</code> that has a Request
     * and Response parameter are automatically assumed to be webservices. A method 
     * <code>handle_XXX</code> will then for instance be made available as a
     * webservice on <code>URL http://server:port/XXX</code>.
     *
     * @param obj    Object that offers a set of handler methods. 
     * @param prefix Optional URL prefix for services of this object. 
     */
    public void addHttpHandler(Object obj, String prefix);
   
   
    /** Add HTTP handlers (webservices).
     * @param cn  Class name. 
     * @param prefix Optional URL prefix for services of this object. 
     */
    public void addHttpHandlerCls(String cn, String prefix);
   
   
    /** Get handler for remote control. */
    public RemoteCtl getRemoteCtl(); 
   
   
    /** Get handler for own position */
    public OwnPosition getOwnPos(); 
   
   
    /** Get handler for own objects */
    public OwnObjects getOwnObjects(); 
    
    
    /** Get configuration properties. */
    public Properties getConfig();
   
   
    /** Save config. Note that this is destructive and should only be called
     * at shutdown. 
     */
    public void saveConfig();
      
      
    /** Get string configuration property. 
     * @param pn property name.
     * @param dval default value. 
     */
    public String getProperty (String pn, String dval); 
   
   
    /** Get boolean configuration property. 
     * @param pn property name.
     * @param dval default value. 
     */
    public boolean getBoolProperty (String pn, boolean dval);
   
   
    /** Get integer configuration property. 
     * @param pn property name.
     * @param dval default value. 
     */
    public int getIntProperty (String pn, int dval);
   
   
    /** Generic dictionary of objects. 
     * Plugins may use this to exchange references to interfaces to their services. 
     */
    public Map<String, Object> properties();
   
   
    /** Get software version. */
    public String getVersion();
   
   
    /** Get default to-address for APRS packet to be sent. */
    public String getToAddr();
   
   
    /** Get SAR URL handler. */
    public SarUrl getSarUrl();
   
   
    /** Get SAR mode info. */
    public SarMode getSar();


    /** Set SAR mode. 
     * @param src Callsign of the user requesting SAR mode. 
     * @param filt Regular expression for filtering source callsign of pos reports.
     * @param descr The reason why SAR mode is requested. 
     */
    public void setSar(String src, String filt, String descr, boolean h);


    /**
     * Clear SAR mode. 
     */
    public void clearSar();
    
    
    /**
     * Add shutdown handler function. 
     */
    public void addShutdownHandler(SimpleCb cb);
    
    
}

