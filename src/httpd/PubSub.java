/* 
 * Copyright (C) 2017 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import no.polaric.aprsd.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.io.IOException;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import java.net.*;
import java.util.function.*;


/**
 * Generic publish/subscribe service using websocket. 
 */
@WebSocket(maxIdleTime=360000)
public class PubSub extends WsNotifier
{

    public class Client extends WsNotifier.Client 
    {   
        public Client(Session conn) 
            { super(conn); }
             
       
        @Override synchronized public void onTextFrame(String text) {
            _api.log().debug("MapUpdater", "Client "+_uid+": " + text);
            String[] parms = text.split(",", 2);
            switch (parms[0]) {
                case "SUBSCRIBE": 
                    subscribe(this, parms[1]);
                    break;
                    
                case "UNSUBSCRIBE": 
                    unsubscribe(this, parms[1]);
                    break;
                    
                case "POST": 
                    String[] arg = parms[1].split(",", 2);
                    post(arg[0], arg[1]);
                    break;
            
                default: 
                    break;
            }
        }
    }
   
   
    /** Message content to be exchanged */
    public static class Subscribe { 
        public String room;
    }


   
    private Map<String, Room> _rooms = new HashMap(); 
    
    
    
    /**
     * Room. 
     */
    public static class Room {
        public Class msgClass;
        public Set<String> cset = new HashSet<String>();
        
        public Room(Class cl)
            { msgClass = cl; }
          
        public boolean addClient(Client c)
            { return cset.add(c.getUid()); }
          
        public void removeClient(Client c)
            { cset.remove(c.getUid()); }
            
        public boolean hasClient(Client c)
            { return cset.contains(c.getUid()); }
    }
    
    
    /**
     * Room which is only for clients having a specific username. 
     */
    public static class UserRoom extends Room {
        public String userid; 
        
        public UserRoom(String user, Class cl) {
            super(cl);
            userid = user;
        }
        
        @Override public boolean addClient(Client c) {
            if (c.getUsername().equals(userid))
                return false;
            return super.addClient(c);
        }
    }
    
    
    
    /**
     * subscribe a client to a room. 
     */
    protected void subscribe(Client c, String rid) {
        Room room = _rooms.get(rid);
        if (room == null)
            return;
        room.addClient(c);
    }
    
    
    /**
     * unsubscribe a client from a room. 
     */
    protected void unsubscribe(Client c, String rid) {
        Room room = _rooms.get(rid);
        if (room == null)
            return;
        room.removeClient(c);
    }
    
    
    
    public void createRoom(String name, Class cl) 
        { _rooms.put(name, new Room(cl)); }
    
    
    public void createUserRoom(String name, String userid, Class cl)
        { _rooms.put(name, new UserRoom(userid, cl)); }

    
    public void removeRoom(String name) 
        { _rooms.remove(name); }
    
    
    
    protected void post(Room rm, String msg) {
        postText(msg, c-> (rm != null && rm.hasClient((PubSub.Client) c)) );
    }
    
          
    public void post (String rid, String msg)
        { post(_rooms.get(rid), msg); }
    
    
    
    public void post(String rid, Object obj) 
        { post(rid, toJson(obj)); }
    

        
        
    public PubSub(ServerAPI api, boolean trusted)
        { super(api, trusted); }  
   
   
    
    /** Factory method. */
    @Override public WsNotifier.Client newClient(Session conn) 
        { return new Client(conn); }


}
