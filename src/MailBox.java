/* 
 * Copyright (C) 2020-2024 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import no.arctic.core.*;
import no.arctic.core.httpd.*;
import java.util.*;
import java.io.*;
import java.util.concurrent.*;

  
  
 /**
  * Mailbox manager and mailboxes for short messages between users.
  * See subclasses (within this class): 
  *
  *   MailBox.User - for individual users. Instances are created and put on the user 
  *   login session. See AuthInfo.java.
  *
  *   Mailbox.Group - Proxy for a group of mailboxes.
  *
  * This class provides several static methods for managing mailboxes and addresses, 
  * and for posting messages to some address.
  * 
  */
  
  
public abstract class MailBox {  
  
    /* Expire times in minutes */
    private static final int MSG_EXPIRE = 60*24*7;
    private static final int NOT_EXPIRE = 60; 
  
    private static String _file;
    private static long _lastMsgId = 0;
    private static long nextMsgId()
        { return _lastMsgId = (_lastMsgId+1) % 2000000000; }
        
    private static ScheduledExecutorService gc = Executors.newScheduledThreadPool(5);
  
  
  
    /* Message content */
    public static class Message implements Serializable {
        public long msgId;
        
        // -1=failure, 0=unknown, 1=success
        // For incoming messages 1 means that user is notified. 
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
 
 
    protected static AprsServerAPI _api;
    private List<String> _addr = new LinkedList<String>();
    private static Map<String, MailBox> _addressMapping = new HashMap<String, MailBox>();
    private static int _nuserboxes = 0;
 
    public abstract void put(Message m); 
        
    protected void archiveSetReceived(String userid) {}
    public void notifyNewMessages() {} 
    
    

    
    /** 
     *  Mailbox for a single user. 
     *  Instances are created and put on the user login session. See AuthInfo.java. 
     */
    
    public static class User extends MailBox {
    
        private List<Message> _messages = new ArrayList<Message>();
        private String _uid;
        private PubSub _psub; 
    
        public User(ServerAPI api, String uid) {
            _uid = uid;
            _nuserboxes++;
            _psub = (PubSub) _api.getWebserver().pubSub();
            _psub.createRoom("messages:"+uid, Message.class); 
            _psub.createRoom("msgstatus:"+uid, Status.class);
            _psub.createRoom("msgdelete:"+uid, null);
        }


        public int size() { 
            return _messages.size();
        }
    
        /** Set status and report it to client */
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
            _psub.put("msgdelete:"+_uid, null);
        }
    
    
        /** Return all messages in mailbox */
        public List<Message> getMessages() 
            {clean(); return _messages;}
    
    
        /** Add a message to the mailbox. Notify user if logged in */
        public void put(Message m) {
            if (((WebServer) _api.getWebserver()).hasLoginUser(_uid)) {
                if (!m.outgoing) { 
                    m.status = 1;
                    _api.getWebserver().notifyUser(_uid, 
                        new ServerAPI.Notification("chat", m.from, m.text, m.time, NOT_EXPIRE));
                }
                _psub.put("messages:"+_uid, m); 
            }
            _messages.add(m);
            clean();
        }
        
    
        /* Notify user of any new messages */
        public void notifyNewMessages() {
            for (Message m: getMessages())
                if (!m.outgoing && m.status == 0) {
                    m.status = 1;
                    _api.getWebserver().notifyUser(_uid, 
                        new ServerAPI.Notification("chat", m.from, m.text, m.time, NOT_EXPIRE));
                }
        }
        
    
        public String getUid() {
            return _uid; 
        }
    
    
       /**
        * Check archive of outgoing messages. Set status from waiting to delivered if 
        * user has logged in on destination server. 
        */
        @Override protected void archiveSetReceived(String userid) {
            for (Message m : getMessages())
                if (m.to.equals(userid) && m.outgoing)
                    setStatus(m, 1, "Delivery confirmed: "+userid);
        }
    }
    
    
    
    /** Proxy for a group of mailboxes */
    
    public static class Group extends MailBox implements Serializable {
        private List<MailBox> _boxes = new ArrayList<MailBox>(); 
        
        public Group(AprsServerAPI api) {
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
        if (!addr.matches(".*@(aprs|APRS|Aprs)")) {
            return;
        }
            
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
        _nuserboxes--;
        for (String addr: _addr) {
            _addressMapping.remove(addr);
            if (addr.matches(".*@(aprs|APRS|Aprs)"))
               _api.getMsgProcessor().unsubscribe( addr.split("@")[0].toUpperCase() );
        }
        _addr.clear();
    }
    
    
    
    /*******************************************************
     * STATIC METHODS 
     *******************************************************/
    
    public static void init(AprsServerAPI api) {
        _api = api;
        
        _file = api.getProperty("mailbox.file", "mailbox.dat");
        if (_file.charAt(0) != '/')
           _file = System.getProperties().getProperty("datadir", ".")+"/"+_file;   
        restore();
        
        if (api.getRemoteCtl() == null)
            return;
            
        /* Handler for incoming messages */    
        api.getRemoteCtl().setMailbox( (sender, recipient, text) -> {
            String[] tc = text.split(" ", 2); 
            String[] addr = tc[0].split(">", 2);
            return putMessage(new Message(addr[0], addr[1], tc[1]));
        });
        
        /* Handler for user-logins on other nodes */
        api.getRemoteCtl().setUserCallback( (node, user) -> {
            for (MailBox box: _addressMapping.values())
                box.archiveSetReceived(user);
        });  
        
        /* Handler for user-logins */
        ((WebServer) api.getWebserver()).onLogin( (user) -> {
            /* Notify other user's mailboxes that user has received messages (if any) */
            for (MailBox box: _addressMapping.values())
                box.archiveSetReceived(user);
                
            /* Notify this user about any new incoming messsages */
            final MailBox mybox = _addressMapping.get(user); 
            if (mybox != null)
                gc.schedule( ()->
                    mybox.notifyNewMessages(), 20, TimeUnit.SECONDS );
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
    public static boolean postMessage(AprsServerAPI api, String from, String to, String txt) {
        return postMessage(api, new Message(from, to, txt));
    }

    
    
    /**
     * Send a message. 
     * Return false if message could not be delivered (unknown to-address). 
     */
    public static boolean postMessage(AprsServerAPI api, Message msg) {
        if (msg.time==null)
            msg.time = new Date();
            
        /* If there is a @-part of the address, it is remote */
        String[] addr = msg.to.split("@", 2);
        if (addr.length > 1 && null!= api.getRemoteCtl() && addr[1].toUpperCase().equals(api.getRemoteCtl().getMycall())) {
            addr[1]=null;
            msg.to = addr[0];
        }
            
        if (addr.length > 1 && addr[1] != null) {
            if (!postRemoteMessage(api, addr, msg)) 
                return false;
        }
        else if (!putMessage(msg))
            return false;
            
        int status = (((WebServer) api.getWebserver()).hasLoginUser(addr[0]) ? 1 : 2);
        archiveSent(_addressMapping.get(msg.from), msg, status);
        return true;
    }
    
    
    
    /** 
     * Put message into a local mailbox. 
     */
    public static boolean putMessage(Message msg) {
        /* 
         * Find recipients mailbox and put the message there.
         * If not found AND the recipient is a valid user on this node, 
         * create it
         */
        MailBox box = _addressMapping.get(msg.to);
        
        if (box == null && _api.getWebserver().userDb().hasUser(msg.to)) {
            box = new User(_api, msg.to); 
            box.addAddress(msg.to);
        }    
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
    private static boolean postRemoteMessage(AprsServerAPI api, String[] addr, Message msg) {
    
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
            if (api.getRemoteCtl() == null)
                return false;

            /* 
             * @-part of address is another Polaric Server instance. 
             * Message should be authenticated and acked. 
             */
            addr[1] = addr[1].toUpperCase(); 
            msg.to = addr[0]+"@"+addr[1];
            MailBox.User mb = (MailBox.User) _addressMapping.get(msg.from);
            Message m2 = archiveSent(mb, msg, 0); 
            msg.from = msg.from+"@"+api.getRemoteCtl().getMycall();
            
            api.getMsgProcessor().sendMessage(addr[1], "MSG "+msg.from+">"+addr[0]+" "+msg.text, 
                true, true, new MessageProcessor.Notification() {
                    MailBox.User mb_ = mb;
                    Message m2_ = m2;
                    
                    /* Handler to be called if failure (a REJ is received) */
                    public void reportFailure(String id, String mtext) {
                        api.log().info("MailBox","Delivery failed: msgid="+msg.msgId+", node="+id);
                        mb_.setStatus(m2_, -1, "Delivery failed: "+id);
                    }
                
                    /* 
                     * Handler to be called if success (an ACK is received). If recipient is 
                     * logged in we assume that the message is deliered to him/her 
                     */
                    public void reportSuccess(String id, String mtext) {
                        int status = (api.getRemoteCtl().hasUser(addr[1], msg.to) ? 1 : 2);
                        api.log().info("MailBox", "Delivery confirmed: msgid="+msg.msgId+", to="+msg.to+", status="+status);
                        mb_.setStatus(m2_, status, (status == 1 ? "Delivery confirmed: " : "Awaiting user login: ")+id);
                    }
                });
            return true; 
        }    
    }
    
    
    /**
     * Shutdown. May save state, etc.. 
     */
     
    public static void shutdown()
       { save(); }
    
    
    
    private static synchronized void save() {
        try {
            FileOutputStream fs = new FileOutputStream(_file); 
            ObjectOutputStream ofs = new ObjectOutputStream(fs);
            Set<MailBox> boxes = new HashSet<MailBox>(); 
            
            ofs.writeInt(_nuserboxes);
            for (MailBox mb : _addressMapping.values())
                if (mb instanceof MailBox.User mbu && !boxes.contains(mb)) {
                    boxes.add(mb);
                    ofs.writeObject(mbu.getUid());
                    ofs.writeInt(mbu.size());
                    _api.log().info("MailBox", "Saving mailbox: "+mbu.getUid()+", "+mbu.size()+" messages");
                    for (Message x: mbu.getMessages())
                        ofs.writeObject(x);
                }
            
            ofs.close();
        }
        catch (Exception e) {
            e.printStackTrace();
            _api.log().warn("MailBox", "Cannot save data: "+e);
        } 
    }
    
    
    
    private static synchronized void restore() {
        int i=0;
        ObjectInputStream ifs = null;
        try {
            FileInputStream fs = new FileInputStream(_file);
            ifs = new ObjectInputStream(fs);
            int boxes = ifs.readInt();
            for (i=0; i < boxes; i++) {
                String uid = (String) ifs.readObject();
                int nmsgs = ifs.readInt();
                MailBox.User mb = new MailBox.User(_api, uid);
                _api.log().info("MailBox", "Restoring mailbox: "+uid+", "+nmsgs+" messages");
                _addressMapping.put(uid, mb);
                for (int j=0; j<nmsgs; j++)
                    mb._messages.add((Message) ifs.readObject());
            }
            ifs.close();
        }
        catch (EOFException e) {
            _api.log().warn("MailBox", "Unexpected EOF. _nuserboxes is wrong?");
            try {ifs.close();} catch (Exception x) {};
        }
        catch (Exception e) {
            _api.log().warn("MailBox", "Cannot restore data: "+e);
            e.printStackTrace();
        }
    }
    
    
}



