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
@WebSocket(maxIdleTime=600000)
public class PubSub extends WsNotifier implements ServerAPI.PubSub
{

    public class Client extends WsNotifier.Client
    {   
        public Client(Session conn) { 
            super(conn); 
        }
             
       
        @Override synchronized public void onTextFrame(String text) {
            _api.log().debug("PubSub", "Client "+_uid+", userid="+getUsername()+" : " + text);
            String[] parms = text.split(",", 2);
            switch (parms[0]) {
                /* subscribe, room */
                case "SUBSCRIBE": 
                    subscribe(this, parms[1]);
                    break;
                   
                /* unsubscribe, room */   
                case "UNSUBSCRIBE": 
                    unsubscribe(this, parms[1]);
                    break;
                    
                /* post, room, message */
                // FIXME: Maybe there should be a specific permission for posting to rooms? 
                case "PUT": 
                    String[] arg = parms[1].split(",", 2);
                    
                    /* Only subscribers are allowed to post */
                    Room rm = _rooms.get(arg[0]);
                    if (rm != null && rm.canPost(this) && rm.hasClient(this))
                        putText(arg[0], arg[1]);
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
        public boolean login=false, sar=false, admin=false; 
          // true means that authorization is required 
          
        public boolean allowPost=false; 
          // false means that only admin can post. True means that authorized users can post
          
        public Room(Class cl)
            { msgClass = cl; }
        
        public Room(boolean lg, boolean s, boolean a, boolean ap, Class cl)
            { login=lg; sar=s; admin=a; allowPost=ap; msgClass=cl; }
          
        public boolean canPost(Client c) {
            return allowPost || c._auth.admin;
        }
          
        public boolean authorized(Client c) {
            return ((!login || c.login()) &&
                (!sar || c._auth.sar) &&
                (!admin || c._auth.admin));
        }
        
        public boolean addClient(Client c) { 
            if (authorized(c))
                return cset.add(c.getUid()); 
            else 
                return false;
        }
          
        public void removeClient(Client c)
            { cset.remove(c.getUid()); }
            
        public boolean hasClient(Client c)
            { return cset.contains(c.getUid()); }
            
        public String toString() {return "Room["+cset.size()+"]"; }
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
            if (!c.getUsername().equals(userid))
                return false;
            return super.addClient(c);
        }
    }
    
    
    
    @Override public boolean subscribe(String uid, WsNotifier.Client client) 
    {
        if (client.login())
            createUserRoom("notify:"+client.getUsername(), 
               client.getUsername(), ServerAPI.Notification.class);
        return true; 
    }
   
   
    /**
     * Websocket close handler.
     */
    @OnWebSocketClose
    public void onClose(Session conn, int statusCode, String reason) {
       String user = _getUid(conn);
       closeSes(conn);
    }
   
    /**
     * subscribe a client to a room. 
     */
    protected void subscribe(Client c, String rid) {
        Room room = _rooms.get(rid);
        if (room == null)
            return;
        if (!room.addClient(c))
            _api.log().warn("PubSub", "Client "+c.getUid()+" denied access to room: "+rid);
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
    
    
    
    /** Create a room */
    public void createRoom(String name, Class cl) { 
        if (!_rooms.containsKey(name))
            _rooms.put(name, new Room(cl)); 
    }
    
    
    
    /** Create a room with restricted access */
    public void createRoom(String name, boolean lg, boolean sar, boolean adm, boolean post, Class cl) { 
        if (!_rooms.containsKey(name))
            _rooms.put(name, new Room(lg, sar, adm, post, cl)); 
    }
    
    
    
    /** Create a room for a given userid */
    public void createUserRoom(String name, String userid, Class cl) { 
        if (!_rooms.containsKey(name))
            _rooms.put(name, new UserRoom(userid, cl)); 
    }

    
    
    /** Remove a room */
    public void removeRoom(String name) 
        { _rooms.remove(name); }
    
    
    
    /** Check if a room exists */
    public boolean hasRoom(String name) 
        { return _rooms.containsKey(name); }
    
    
    
    /** 
      * Post a message to members of a room. If uname is given, 
      * the message will be posted only to the named member
      */
    private void _put(Room rm, String msg, String uname) {
        postText(msg, 
           c-> (rm != null && rm.hasClient((PubSub.Client) c) && 
                    (uname==null || uname.equals(((PubSub.Client) c).getUsername()))
               )
        );
    }
    
    
    
    /** Post a message to a room (text is prefixed with the room name) */
    public void putText (String rid, String msg, String uname) { 
        if (hasRoom(rid))
            _put(_rooms.get(rid), rid+","+msg, uname); 
    }
    
    
    public void putText (String rid, String msg)
        { putText(rid, msg, null); }
    
    
    /** Post a object to a room (JSON encoded) */
    public void put(String rid, Object obj, String uname) 
        { putText(rid, toJson(obj), uname); }
    
    
    public void put(String rid, Object obj)
        { put(rid, obj, null); }
        
        
    public PubSub(ServerAPI api, boolean trusted)
        { super(api, trusted); }  
   
   
    
    /** Factory method. */
    @Override public WsNotifier.Client newClient(Session conn) 
        { return new Client(conn); }


}
