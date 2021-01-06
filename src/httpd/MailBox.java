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
  
    private static long _lastMsgId = 0;
    private static long nextMsgId()
        { return _lastMsgId = (_lastMsgId+1) % 2000000000; }
  
  
    /* Message content */
    public static class Message {
        public long msgId;
        
        // -1=failure, 1=success
        public int status = 0; 
        public Date time; 
        public String from, to; 
        public boolean read;
        public boolean outgoing;
        public String text;
        
        public Message() {
            msgId = nextMsgId(); 
        }
        public Message(String f, String t, String txt) {
            msgId = nextMsgId(); 
            from=f; to=t; text=txt;
            time = new Date(); 
            read = false;
        }
        public Message(Message m) {
            msgId = nextMsgId(); 
            from=m.from; 
            to=m.to;
            time=m.time;
            read=m.read;
            text=m.text;
            outgoing=m.outgoing;
        }
    }
 
    /* Status message.  To report delivery success or failure */
    public static class Status {
        public long msgId; 
        public int status; 
        public String info; 
        
        public Status(long id, int st, String inf) {
            msgId=id; status=st; info=inf;
        }
    }
 
 
    protected ServerAPI _api;
    private List<String> _addr = new LinkedList<String>();
    private static Map<String, MailBox> _addressMapping = new HashMap<String,MailBox>();
 
    public abstract void put(Message m); 
        
        
        
    /** 
     *  Mailbox for a single user. 
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
            _psub.createRoom("msgstatus:"+uid, Status.class);
        }

    
        private void setStatus(Message m, int st, String info) {
            m.status = st;
            Status stm = new Status(m.msgId, st, info);
            _psub.put("msgstatus:"+_uid, stm); 
        }
    
    
    
        /** Cleanup. Remove outdated messages */
        private void clean() {
            long now = (new Date()).getTime(); 
            long limit = 1000 * 60 * MSG_EXPIRE; 
            _messages.removeIf( m -> (m.time.getTime() < now - limit));
        }
    
    
        public void remove(long id) {
            _messages.removeIf( m -> (m.msgId == id));
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
        if (!addr.matches(".*@(aprs|APRS|Aprs)"))
            return;
            
        /* 
         * Subscribe to APRS messages addressed to user 
         * (registered as callsign@APRS). 
         * FIXME: Need to send a packet to anounce...
         */
        addr = addr.split("@")[0].toUpperCase();
        _api.getMsgProcessor().subscribe(
            addr, (sender, recipient, text) -> {
                put(new Message(sender.getIdent()+"@aprs", recipient, text)); 
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
    
    
    
    public static void init(ServerAPI api) {
        if (api.getRemoteCtl() == null)
            return;
        api.getRemoteCtl().setMailbox( (sender, recipient, text) -> {
            String[] tc = text.split(" ", 2); 
            String[] addr = tc[0].split(">", 2);
            return putMessage(new Message(addr[0], addr[1], tc[1]));
        });
    }
    
    
    
    /** 
     * Get a mailbox for a given address.
     */
    public static MailBox get(String u) {
        return _addressMapping.get(u);
    }
    

    
    /**
     * Send a message. 
     */
    public static boolean postMessage(ServerAPI api, String from, String to, String txt) {
        return postMessage(api, new Message(from, to, txt));
    }

    
    
    /**
     * Send a message. 
     * Return false if message could not be delivered (unknown to-address). 
     */
    public static boolean postMessage(ServerAPI api, Message msg) {
        if (msg.time==null)
            msg.time = new Date();
            
        /* If there is a @-part of the address, it is remote */
        String[] addr = msg.to.split("@", 2);
        if (addr.length > 1 && addr[1].toUpperCase().equals(api.getRemoteCtl().getMycall())) {
            addr[1]=null;
            msg.to = addr[0];
        }
            
        if (addr.length > 1 && addr[1] != null) {
            if (!postRemoteMessage(api, addr, msg)) 
                return false;
        }
        else if (!putMessage(msg))
            return false;
            
        archiveSent(_addressMapping.get(msg.from), msg, 1);
        return true;
    }
    
    
    /** 
     * Put message into a local mailbox. 
     */
    public static boolean putMessage(Message msg) {
        /* Find recipients mailbox and put it there */
        MailBox box = _addressMapping.get(msg.to);
        if (box != null)
            box.put(msg);
        else return false; 
        return true;
    }
    
    
    
    /** 
     * Put a copy of the sent message in the mailbox. Mark as outgoing. 
     */
    private static Message archiveSent(MailBox box, Message msg, int status) {
        Message m2 = new Message(msg);
        m2.status = status; 
        m2.outgoing = true;
        if (box != null)
            box.put(m2);
        return m2;
    }
    
    
    /**
     * Send message to another server or elsewhere depending on the @-field. 
     */
    private static boolean postRemoteMessage(ServerAPI api, String[] addr, Message msg) {
        if (api.getRemoteCtl() == null)
            return false; 
    
        if (addr[1].matches("APRS|aprs|Aprs")) {
            /* 
             * Message should be sent as a raw APRS message 
             * FIXME: Check that from address is a valid callsign.
             * FIXME: Should we use sequence numbering and ack? 
             */ 
            MailBox.User mb = (MailBox.User) _addressMapping.get(msg.from+"@aprs");
            archiveSent(mb, msg, 0); 
            api.getMsgProcessor().sendRawMessage(msg.from, msg.text, addr[0].toUpperCase());  
            return true; 
        }
        else {
            /* 
             * @-part of address is another Polaric Server instance. 
             * Message should be authenticated and acked. 
             */
            addr[1]=addr[1].toUpperCase(); 
            msg.to = addr[0]+"@"+addr[1];
            MailBox.User mb = (MailBox.User) _addressMapping.get(msg.from);
            Message m2 = archiveSent(mb, msg, 0); 
            msg.from = msg.from+"@"+api.getRemoteCtl().getMycall();
            
            api.getMsgProcessor().sendMessage(addr[1], "MSG "+msg.from+">"+addr[0]+" "+msg.text, 
                true, true, new MessageProcessor.Notification() {
                
                    public void reportFailure(String id) {
                        api.log().info("MailBox","Delivery failed: msgid="+msg.msgId+", node="+id);
                        mb.setStatus(m2, -1, "Delivery failed: "+id);
                    }
                
                    public void reportSuccess(String id) {
                        api.log().info("MailBox", "Delivery confirmed: msgid="+msg.msgId+", node="+id);
                        mb.setStatus(m2, 1, "Delivery confirmed: "+id);
                    }
                });
            return true; 
        }    
    }
    
}



