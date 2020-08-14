/* 
 * Copyright (C) 2020 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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

  
  
 
  
  
public abstract class MailBox {  
  
    /* Expire times in minutes */
    private static final int MSG_EXPIRE = 60*24*7;
    private static final int NOT_EXPIRE = 60; 
  
  
    public static class Message {
    //    public long msgId;
        public Date time; 
        public String from, to; 
        public boolean read;
        public boolean outgoing;
        public String text;
        
        public Message() {}
        public Message(String f, String t, String txt) {
            from=f; to=t; text=txt;
            time = new Date(); 
            read = false;
        }
        public Message(Message m) {
            from=m.from; 
            to=m.to;
            time=m.time;
            read=m.read;
            text=m.text;
            outgoing=m.outgoing;
        }
    }
 
    protected ServerAPI _api;
    private List<String> _addr = new LinkedList<String>();
    private static Map<String, MailBox> _addressMapping = new HashMap<String,MailBox>();

    
    public abstract void put(Message m); 
        
        
        
    /** Mailbox for a single user. 
     *  Instances are created and put on the user login session. See AuthInfo.java. 
     */
    
    public static class User extends MailBox {
    
        private List<Message> _messages = new ArrayList<Message>();
        private String _uid;
        private PubSub _psub; 
    
        public User(ServerAPI api, String uid) {
            _api = api;
            _uid = uid;
            _psub = (no.polaric.aprsd.http.PubSub) _api.getWebserver().getPubSub();
            _psub.createRoom("messages:"+uid, Message.class); 
        }

    
        /** Cleanup. Remove outdated messages */
        private void clean() {
            long now = (new Date()).getTime(); 
            long limit = 1000 * 60 * MSG_EXPIRE; 
            _messages.removeIf( m -> (m.time.getTime() < now - limit));
        }
    
    
        /** Return all messages in mailbox */
        public List<Message> getMessages() 
            {clean(); return _messages;}
    
    
        /** Add a message to the mailbox */
        public void put(Message m) {
            if (!m.outgoing) { 
                _api.getWebserver().notifyUser(_uid, 
                    new ServerAPI.Notification("chat", m.from, m.text, m.time, NOT_EXPIRE));
            }
            _psub.put("messages:"+_uid, m); 
            _messages.add(m);
            clean();
        }
        
        public String getUid() {
            return _uid; 
        }

    }
    
    
    
    /** Proxy for a group of mailboxes */
    
    public static class Group extends MailBox {
        private List<MailBox> _boxes = new ArrayList(); 
        
        public Group(ServerAPI api) {
            _api = api;
        }
        
        /** Add user's box to group */
        public void addBox(MailBox box) {
            _boxes.add(box);
        }
        
        /** Add message to mailboxes */
        public void put(Message m) {
            _boxes.forEach( bx -> bx.put(m) );
        }
    }
    

    
            
    /** 
     * Register a mapping from an address to this mailbox. 
     */
    public void addAddress(String addr) {
        _addr.add(addr);
        _addressMapping.put(addr, this);
        
        /* Aprs message handler. Return if not APRS user */
        if (!addr.matches(".*@(aprs|APRS)"))
            return;
            
        /* 
         * Subscribe to APRS messages addressed to user 
         * (registered as callsign@APRS). 
         * FIXME: Need to send a packet to anounce...
         */
        addr = addr.split("@")[0].toUpperCase();
        _api.getMsgProcessor().subscribe(
            addr, (sender, recipient, text) -> {
                put(new Message(sender.getIdent(), recipient, text)); 
                return true;
            }, false );
    }
        
    
    
    /**
     * Remove all address-mappings associated with this mailbox. 
     */    
    public void removeAddresses() {
        for (String addr: _addr) 
            _addressMapping.remove(addr);
        _addr.clear();
    }
    
    

    public static MailBox get(String u) {
        return _addressMapping.get(u);
    }
    

    
    /**
     * Send a message. 
     */
    public static void postMessage(String from, String to, String txt) {
        postMessage(new Message(from, to, txt));
    }

    
    
    /**
     * Send a message. 
     * Return false if message could not be delivered (unknown to-address). 
     */
    public static boolean postMessage(Message msg) {
        if (msg.time==null)
            msg.time = new Date();
            
        /* Find recipients mailbox and put it there */
        MailBox box = _addressMapping.get(msg.to);
        if (box != null)
            box.put(msg);
        else return false;
        
        /* Now, archive it senders mailbox as well (as outgoing) */
        box = _addressMapping.get(msg.from);
        Message m2 = new Message(msg);
        m2.outgoing = true;
        if (box != null)
            box.put(m2);
        
        return true;
            
        /*
         * TODO: If to address is aprs-address message could be sent
         * as an aprs message.  
         */
    }
    
    
}



