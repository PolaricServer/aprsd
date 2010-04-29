/* 
 * Copyright (C) 2009 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
 
package aprs;
import java.util.regex.*;
import java.io.*;
import java.net.*;
import java.util.*;



public class MessageProcessor implements Runnable
{

   public interface MessageHandler {
      public boolean handleMessage(Station sender, String text);
   }

   public interface Notification {
      public void reportFailure(String id);
   }

   /* Keep record of unacked outgoing messages */
   private static class OutMessage
   {
      String msgid;
      String recipient;
      String message;
      Date time;
      Notification not;
      int retry_cnt = 0;
      OutMessage(String mid, String rec, String msg, Notification n)
        { msgid = mid; recipient = rec; message = msg; time = new Date(); not=n; }
   }

   private static class Subscriber
   {
       MessageHandler recipient;
       boolean verify;
       
       private LinkedHashMap<String, Boolean> recMessages =
       new LinkedHashMap()
       {
           protected boolean removeEldestEntry(Map.Entry e)
               { return size() > 100; }
       };
       Subscriber(MessageHandler rec, boolean ver)
         {recipient = rec; verify = ver;}
   }
   

   private static final int _MSG_INTERVAL = 20;
   private static final int _MSG_MAX_RETRY = 3; 
   
   private Map<String, Subscriber> _subscribers = new HashMap();
   private Map<String, OutMessage> _outgoing = new HashMap();
   private static int _msgno = 0;
   private Channel    _inetChan, _rfChan;
   private String     _myCall; /* Move this ? */
   private Thread     _thread;
   private String     _key;

    
   private static String getNextId()
   {
      _msgno = (_msgno + 1) % 1000;
      return ""+_msgno;
   }

    
   public MessageProcessor(Properties config)
   {
       _myCall = config.getProperty("message.mycall", "N0CALL").trim();
       _key = config.getProperty("message.auth.key", "NOKEY").trim();
       _thread = new Thread(this);
       _thread.start();
   }  
       
           
   /* Share with igate.java ?? */        
   public void setChannels(Channel rf, Channel inet)
   {
       _inetChan = inet;
       _rfChan = rf;
   }

    
   /**
    * Process incoming message.
    * Called by APRS Parser
    */
   public synchronized void incomingMessage
      (Station sender, String recipient, String text, String msgid)
   {
      if (_myCall.equals(recipient) && text.matches("(ack|rej).+")) {
         msgid = text.substring(3, text.length());
         _outgoing.remove(msgid);
         /* FIXME: notify recipient about result? */
         return;
      } 
      Subscriber subs = _subscribers.get(recipient);
      if (subs != null) {
         if (!subs.recMessages.containsKey(msgid)) {
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
            result = result && subs.recipient.handleMessage(sender,text);
            subs.recMessages.put(msgid, result);
         }     
         if (msgid != null)
            sendAck(sender.getIdent(), msgid, subs.recMessages.get(msgid));
      }      
   }
   


   /**
    * Subscribe to message delivery service.
    */
   public void subscribe(String recipient, MessageHandler handler, boolean verify)
      { _subscribers.put(recipient, new Subscriber(handler, verify)); }

      

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
    * If authenticated is true, generate a MAC based on the secret key, the sender-id,
    * recipient-id, message and the message-id. The MAC is prefixed with a # and is
    * placed at the end of the message (before the message id). We Base64 encode the MAC
    * and use the first 8 bytes of it.
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
         _outgoing.put(msgid, new OutMessage(msgid, recipient, message, not));
                             
      sendPacket(message);       
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
                       + msgid );
   }



   /**
    * Encode and send an APRS message packet.
    */
   private void sendPacket(String message)
   {
       Channel.Packet p = new Channel.Packet();
       p.from = _myCall;
       p.to = "APRS";
       /* Need to set p.via, and differently for the two channels */
       p.type = ':';
       p.report = ":"+message;
       
       boolean sentOnRf = false;
       if (_rfChan != null) {
          /* Note, if myCall does not match TNC call, it will 
           * be sent as third party packet 
           */
          p.via = "";
          _rfChan.sendPacket(p);
          sentOnRf = true;
       }
       if (_inetChan != null) {
          /* If already sent on rf, emulate a igate. Perhaps we
           * should not send it on internet. FIXME: _myCall.
           */
          p.via = sentOnRf ? "qAR,"+_myCall : "TCPIP";
          _inetChan.sendPacket(p);
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
       while (true) {
         try {
            Thread.sleep(5000);
            synchronized(this) {
              for (OutMessage m: _outgoing.values()) {
                 Date t = new Date();
                 long tdiff = t.getTime() - m.time.getTime();
                 
                 if (tdiff >= _MSG_INTERVAL*1000) {
                   if (m.retry_cnt >= _MSG_MAX_RETRY) {
                      if (m.not != null)
                         m.not.reportFailure(m.recipient);
                      _outgoing.remove(m.msgid);
                   }
                   else {
                      sendPacket(m.message);
                      m.time = t;
                      m.retry_cnt++;
                   } 
                }
              }
            }

         } catch (Exception e) { System.out.println("*** MSGPROCESSOR WARNING: "+e); }
       }
    }

   
}
