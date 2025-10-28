/* 
 * Copyright (C) 2016-2023 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
 
package no.polaric.aprsd.aprs;
import no.polaric.aprsd.*;
import no.polaric.core.util.*;
import no.polaric.aprsd.point.*;
import no.polaric.aprsd.channel.*;
import java.util.regex.*;
import java.io.*;
import java.net.*;
import java.util.*;


/**
 * Implement APRS messaging.
 * We also add an option to use a Message Authentication scheme (MAC field). 
 */
 
public class MessageProcessor implements Runnable, Serializable
{
    
   /** 
    * Interface for message subscribers. 
    */
   public interface MessageHandler {
      public boolean handleMessage(Station sender, String recipient, String text);
   }


   /**
    * Interface for reporting success or failure in delivering messages.
    */
   public interface Notification {
      public void reportFailure(String id, String msg);
      public void reportSuccess(String id, String msg);
   }
   
   
   /**
    * Keep record of unacked outgoing messages. 
    */
   private static class OutMessage
   {
      String msgid;
      String recipient;
      String message;
      String msgtext;
      Date time;
      Notification not;
      int retry_cnt = 0;
      boolean deleted = false;
      OutMessage(String mid, String rec, String mtext, String msg, Notification n)
        { msgid = mid; recipient = rec; msgtext = mtext; message = msg; time = new Date(); not=n; }
   }

   
   /**
    * Keep record of subscribers to this service.
    */
   private static class Subscriber
   {
       MessageHandler recipient;
       boolean verify;

       Subscriber(MessageHandler rec, boolean ver)
         {recipient = rec; verify = ver;}
   }
   

   private static final int _MSG_INTERVAL = 30;
   private static final int _MSG_MAX_RETRY = 4; 
   private static final int _MAX_MSGID = 10000; 
   private static final int _MSGID_STORE_SIZE = 1024;
   private static final int _MSGID_TX_STORE_SIZE =  256;
   
   
   /**
    * The last nn received messages by sender-callsign + # + msgid 
    */
   private static class MsgRec implements Serializable {
        public boolean accepted; 
        public Date time;
        public MsgRec(boolean a) 
            {accepted=a; time = new Date();}
   }
   
   private static class RecMessages extends LinkedHashMap<String, MsgRec> {
       protected boolean removeEldestEntry(Map.Entry e)
            { return  (new Date()).getTime() > ((MsgRec)e.getValue()).time.getTime() + 1000*60*60 
              || size() > _MSGID_STORE_SIZE; }
   }
   
   /**
    * The last nn sent messages by # + msgid 
    */
   private static class TxMessages extends LinkedHashMap<Integer, Boolean> {
       protected boolean removeEldestEntry(Map.Entry e)
            { return size() > _MSGID_TX_STORE_SIZE; }
   }
   
   
   private static RecMessages recMessages = new RecMessages();
   private static TxMessages  txMessages = new TxMessages(); 
   
   private Date recMsg_ts = new Date(); 
       
   private Map<String, Subscriber> _subscribers = new HashMap<String, Subscriber>();
   private Map<String, OutMessage> _outgoing = new HashMap<String, OutMessage>();
   private static int     _msgno = 0;
   private AprsChannel    _inetChan, _rfChan;
   private String         _myCall; /* Move this ? */
   private Thread         _thread;
   private String         _key;
   private String         _defaultPath;
   private String         _alwaysRf;
   private String         _file;
   private AprsServerConfig  _api; 
    
    
   private static String getNextId()
   {
      do {
        _msgno = (_msgno + 1) % _MAX_MSGID;
      } while (txMessages.containsKey(_msgno));
      txMessages.put(_msgno, true);
      return ""+_msgno;
   }

   
   
   
   private int threadid=0;
   public MessageProcessor(AprsServerConfig api)
   {
       _file = api.getProperty("message.file", "messages.dat");
       if (_file.charAt(0) != '/')
           _file = System.getProperties().getProperty("datadir", ".")+"/"+_file; 
   
       _myCall = api.getProperty("message.mycall", "").toUpperCase();
       if (_myCall.length() == 0)
           _myCall = api.getProperty("default.mycall", "NOCALL").toUpperCase();
     
       _key         = api.getProperty("message.auth.key", "NOKEY");
       _defaultPath = api.getProperty("message.rfpath", "WIDE1-1");
       _alwaysRf    = api.getProperty("message.alwaysRf", "");
       _thread  = new Thread(this, "MessageProcessor-"+(threadid++));
       _thread.start();
       _api = api;
   }  
       
           
    public String getMycall() {
       return _myCall;
    }
      
    public void setChannels(AprsChannel rf, AprsChannel inet)
    {
        setRfChan(rf);
        setInetChan(inet);
    }
    public void setRfChan(AprsChannel rf) {
      if (rf != null && !rf.isRf())
         _api.log().warn("MessageProcessor", "Non-RF channel used as RF channel");
      _rfChan = rf;
    }
    public void setInetChan(AprsChannel inet) {
      if (inet != null && inet.isRf())
         _api.log().warn("MessageProcessor", "RF channel used as internet channel");
      _inetChan = inet;
    }
    
    
    
   /**
    * Process incoming message.
    * Called by APRS Parser
    * @param sender Station that sent the message.
    * @param recipient Destination address.
    * @param text Content of the message.
    * @param msgid Message ident.
    */
    public synchronized void incomingMessage
        (Station sender, String recipient, String text, String msgid)
    {
       /* Is it an ACK or REJ message? */
        if (_myCall.equals(recipient) && text.matches("(ack|rej).+")) {
            msgid = text.substring(3, text.length());

            /* notify recipient about result? */
            OutMessage m = _outgoing.get(msgid);
            if (m != null) {
                if (m.not != null) {
                    if (text.matches("(rej).+")) 
                        m.not.reportFailure(m.recipient, m.msgtext);
                    else 
                        m.not.reportSuccess(m.recipient, m.msgtext);
                }
                _outgoing.remove(msgid);
            }
            else {
                _api.log().debug("MessageProc", "Received ACK/REJ for unknown message: msgid="+msgid);
            }
            return;
        } 
      
        /* Any subscriber to pass message to? */
        Subscriber subs = _subscribers.get(recipient);
        if (subs == null && recipient.matches("BLN.*")) 
            subs = _subscribers.get("BLN");

            
        /* If there is a subscriber to the messasge */ 
        if (subs != null) {
        
            /* Have we seen this message-id before? */
            if (!recMessages.containsKey(sender.getIdent()+"#"+msgid)) { 
                boolean result = !subs.verify;
                if (subs.verify &&
                    text.length() > 9 && text.charAt(text.length()-9) == '#')
                {
                   /* Verify message by extracting MAC field and comparing it
                    * with a computed MAC
                    */
                    String mac = text.substring(text.length()-8, text.length());
                    text = text.substring(0, text.length()-9);
                    result = mac.equals
                        (SecUtils.digestB64(_key+sender.getIdent()+recipient+text+msgid, 8));
                }
            
                result = result && subs.recipient.handleMessage(sender, recipient, text);
                if (!result && msgid != null) 
                    _api.log().info("MessageProc", "Message authentication or processing failed. msgid="+msgid);
                    
                if (msgid != null) {
                    recMessages.put( sender.getIdent()+"#"+msgid, new MsgRec(result));
                    recMsg_ts = new Date();
                }
            }
            else {
                _api.log().debug("MessageProc", "Duplicate message from "+sender.getIdent()+" msgid="+msgid);
                msgid=null;
                // If duplicate, just ignore message (don't ack it)
            }
            
            
            /* 
             * Send ACK or REJ. Assume that message is in recMessages if accepted. 
             */
            if (msgid != null && (!recipient.matches("BLN.*") ) )
                sendAck(sender.getIdent(), msgid, recMessages.get(sender.getIdent()+"#"+msgid).accepted);
        } /* if subs */     
    }
   
   


   /**
    * Subscribe to message delivery service.
    * @param recipient Ident of the recipient. 
    * @param handler The message handler interface of the recipient (used to deliver message)
    * @param verify If true - verify authenticicy of message by using MAC scheme. 
    */
   public void subscribe(String recipient, MessageHandler handler, boolean verify) { 
        _subscribers.put(recipient, new Subscriber(handler, verify)); 
    }

      

   /**
    * Unsubscribe to message delivery service.
    */
   public void unsubscribe(String recipient)
      { _subscribers.remove(recipient); }

   

   /**
    * Send a message.
    * If acked is true, it will wait for an ack message and retry until such a message
    * arrives or timeout.
    *
    * If authenticated is true, generate a HMAC based on the secret key, the sender-id,
    * recipient-id, message and the message-id. The HMAC is prefixed with a # and is
    * placed at the end of the message (before the message id). We Base64 encode the HMAC
    * and use the first 8 bytes of it.
    *
    * @param recipient Destination address of message
    * @param text Content of message
    * @param acked Set to true to indicate that we expect an ack message
    * @param authenticated Set to true to generate a MAC (see above)
    * @param not Interface to which to send a notification of success/failure.
    */  
   public synchronized void sendMessage(String recipient, String text,
                       boolean acked, boolean authenticated, Notification not)
   {
      String msgid = null;
      String mac = "";
      if (acked) {
         msgid = getNextId();
         if (authenticated)
            mac = "#" + SecUtils.digestB64(_key+_myCall+recipient+text+msgid, 8);
      }
        
      /* Should type character be part of message?? */
      String message = (recipient+ "         ").substring(0,9) + ":" + text + mac
                       + (msgid != null ? "{"+msgid : "");
      if (acked)
         _outgoing.put(msgid, new OutMessage(msgid, recipient, text, message, not));                      
      sendPacket(message, recipient);       
   }
   
   
   
   public void sendMessage(String recipient, String text,
                       boolean acked, boolean authenticated)
     { sendMessage(recipient, text, acked, authenticated, null); }




  /**
   * Send acknowledgement (or reject) message.
   */
   private void sendAck(String recipient, String msgid, boolean accept)
   {
      sendPacket( (recipient+ "         ").substring(0,9) + ":"
                       + (accept ? "ack" : "rej")
                       + msgid, recipient );
   }


   
   public void sendRawMessage(String from, String text, String recipient) {
        String message = (recipient+ "         ").substring(0,9) + ":" + text;
        sendPacketFrom(from, message, recipient);
   }
   
   

   /**
    * Encode and send an APRS message packet.
    */
   public void sendPacket(String message, String recipient)
      { sendPacketFrom(_myCall, message, recipient); }
      
   public void sendPacketFrom(String from, String message, String recipient)
   {
       AprsPacket p = new AprsPacket();
       p.from = from;
       p.to = "APRS";
       
       p.msgto = recipient;
       /* Need to set p.via, and differently for the two channels */
       p.type = ':';
       p.report = ":"+message;
       
       /* 
        * Send on RF
        */
       boolean sentOnRf = false;
       if ( _rfChan != null && _rfChan.isActive() && (
           /* if recipient is heard on RF and NOT on the internet */
           ((_rfChan.heard(recipient)   &&
            !_inetChan.heard(recipient))) || 
            recipient.matches(_alwaysRf) || 
            _inetChan == null || !_inetChan.isActive() ||
            recipient.equals("TEST") ) )
       {
          /* Now, get a proper path for the packet. 
           * If possible, a reverse of the path last heard from the recipient.
           */
          String path = _rfChan.heardPath(recipient); 
          if (path == null)
             p.via = _defaultPath;
          else
             p.via = AprsChannel.getReversePath(path); 
             
          _api.log().debug("MessageProc", "Sending message to "+recipient+" on RF: "+p.via);
          if (_rfChan != null && _rfChan.isRf()) 
            _rfChan.sendPacket(p);
          sentOnRf = true;
       }
   
       /*
        * Send on internet (aprs/is)
        */
       if (_inetChan != null && _inetChan.isActive()) {
          /* 
           * If already sent on rf, emulate a igate.
           * Otherwise, use qAC
           */
          p.via = sentOnRf ? "qAR,"+_myCall : "qAC,"+_myCall;
          _api.log().debug("MessageProc", "Sending message to "+recipient+" on INET: "+p.via);
          if (_inetChan != null && !_inetChan.isRf())
            _inetChan.sendPacket(p);
       }  
    
       if ((_inetChan == null || !_inetChan.isActive()) && 
           (_rfChan == null || !_rfChan.isActive()))
          _api.log().warn("MessageProc", "Cannot send message. No channel.");
   }

   
   

    public void save()
    { 
       try { 
           _api.log().debug("MessageProc", "Saving message data...");
           FileOutputStream fs = new FileOutputStream(_file);
           ObjectOutput ofs = new ObjectOutputStream(fs);
             
           ofs.writeObject(_msgno);
           ofs.writeObject(recMessages); 
           ofs.writeObject(txMessages);
       } catch (Exception e) {
           _api.log().warn("MessageProc", "Cannot save: "+e);
       } 
    }


    public void restore()
     {        
         try {     
             _api.log().debug("MessageProc", "Restoring message data...");
             FileInputStream fs = new FileInputStream(_file);
             ObjectInput ifs = new ObjectInputStream(fs);
          
             _msgno = (Integer) ifs.readObject(); 
             recMessages = (RecMessages) ifs.readObject(); 
             txMessages = (TxMessages) ifs.readObject();
         } catch (Exception e) {
             _api.log().warn("MessageProc", "Cannot restore: "+e);
         } 
     }
     

    /**
     * Main thread.
     * Runs periodically and checks outgoing messages, if they need to
     * be re-sent (if not acknowledged within a time-interval) or removed
     * from the outgoing list (if timeout).
     */
    public void run()
    {       
       int n = 0;
       while (true) {
         try {
            Thread.sleep(5000);
            
            /* Every 4 hours, send a message to tell APRS-IS that we are here. 
             */
            if (n % 2880 == 5) {
                n=5;
                sendMessage("TEST", "", false, false);
            }
            n++;
            
        
            Iterator<OutMessage> iter = _outgoing.values().iterator(); 
            iter.forEachRemaining(m -> {
                Date t = new Date();
                long tdiff = t.getTime() - m.time.getTime();
                if (tdiff >= _MSG_INTERVAL*1000 && !m.deleted) {
                    if (m.retry_cnt >= _MSG_MAX_RETRY) {
                        /* After max number of retries report failure */
                        if (m.not != null)
                            m.not.reportFailure(m.recipient, m.msgtext);
                        m.deleted = true; 
                    }
                    else {
                        /* Re-send message */
                        sendPacket(m.message, m.recipient);
                        m.time = t;
                        m.retry_cnt++;
                    } 
                }
            }); 
           
            synchronized(this) {
                _outgoing.values().removeIf((m) -> {return m !=null && m.deleted;}); 
            }

         } catch (Exception e) 
             { _api.log().warn("MessageProc", ""+e);
               e.printStackTrace(System.out);}
       }
    }

   
}
