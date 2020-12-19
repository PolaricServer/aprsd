/* 
 * Copyright (C) 2017-2020 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import java.util.regex.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Remote control. Execute commands on associated servers by exchanging
 * APRS messages. 
 */

public class RemoteCtl implements Runnable, MessageProcessor.Notification
{

   /**
    * Subscriber - object that subscribes to messages.
    */
   protected class Subscriber implements MessageProcessor.MessageHandler
   {
      public boolean handleMessage(Station sender, String recipient, String text) {
          return processRequest(sender, text);
      } 
   }
   
   
   /**
    * Callback for user additions/removals. 
    * Suitable for lambda function.
    */
   public static interface UserCb {
        void cb(String node, String uname);
   }


   /*
    * The nodes will form a overlay network organised in a strict
    * hierarchy. Requests will be sent to parent, but also to
    * children. There is a scalability problem here. Therefore, we
    * may consider to allow filters, i.e. a child may request a
    * filter or area of interest. Based on callsign or geographical
    * position. 
    */
   private String  _myCall; 
   private Map<String, Date> _children = new HashMap<String, Date>();
   private String  _parent;
   private boolean _parentCon = false; 
   private int     _tryPause = 0;
   private Thread  _thread;
   private static ScheduledExecutorService gc = Executors.newScheduledThreadPool(5);
       
   private MessageProcessor _msg;
   private MessageProcessor.MessageHandler _pmsg;
   private ServerAPI _api;
   private Logfile   _log;
   private UserCb    _addUser, _removeUser, _nodeDown; 
    
   private LinkedHashMap<String, String> _cmds = new LinkedHashMap(); 
   
   public String getMycall()
       { return _myCall; }

   public String getParent()
       { return _parent; }
       
   public Set<String> getChildren()
       { return _children.keySet(); }
       
   public void setUserCallback(UserCb a, UserCb r) {
       _addUser=a; _removeUser=r; 
   }
   public void setNodeCallback(UserCb nd) {
       _nodeDown = nd;
   }
   
   
   
   private int threadid=0;    
   public RemoteCtl(ServerAPI api, MessageProcessor mp)
   {
       _myCall = api.getProperty("remotectl.mycall", "").toUpperCase();
       if (_myCall.length() == 0)
           _myCall = api.getProperty("default.mycall", "NOCALL").toUpperCase();
       _parent = api.getProperty("remotectl.connect", null);
       _log = new Logfile(api, "remotectl", "remotectl.log");
       if (_parent != null) 
          _parent = _parent.trim().toUpperCase();
       if ("".equals(_parent))
          _parent = null;
       mp.subscribe(_myCall, new Subscriber(), true);
       _msg = mp;
       _api = api;
       _thread = new Thread(this, "RemoteCtl-"+(threadid++));
       _thread.start();
   }
   
   
   public void setMailbox(MessageProcessor.MessageHandler mh) {
        _pmsg = mh;
   }


   private int _try_parent = 0;

   /* Implements: Interface MessageProcessor.Notification */
   public void reportFailure(String id)
   {
      _api.log().warn("RemoteCtl", "Failed to deliver message to: "+id);
      if (id.equals(_parent)) {
          _parentCon = false;
          _log.info(null, "Connection to parent: "+id+ " failed");
      
          /* Try max 3 times before taking a pause */
          if (_try_parent++ >= 3) {
              _log.info(null, "Giving up - pausing: "+id);
              _tryPause = 3; /* 1 hour */
          }
       }
       else {
         _log.info(null, "Message to child: "+id+ " failed - disconnect");
         disconnectChild(id);
       }
   }
   
   
   /* Implements: Interface MessageProcessor.Notification */
   public void reportSuccess(String id)
   {
        if (id.equals(_parent)) {
            if (!_parentCon) {
                _log.info(null, "Connection to parent: "+id+ " established");
                _parentCon = true;
            }
            _try_parent = 0;
      }
   }
   
   
   /** 
    * Send request to given destination.
    */
   public void sendRequest(String dest, String text)
   { 
      _msg.sendMessage(dest, text, true, true, this);
      _log.info(null, "Send > "+dest+": "+text);
   }
   
     
   /**
    * Send request to all. To parent and children servers. 
    * may specify an exception. 
    */  
   public void sendRequestAll(String text, String except)
   {
      int n = 0;
      if (_parent != null && !_parent.equals(except))
        { _msg.sendMessage(_parent, text, true, true, this); n++; }
      for (String r: getChildren() )
         if (!r.equals(except))
            { _msg.sendMessage(r, text, true, true, this); n++; }
    
      if (n>0)
         _log.info(null, "Send > ALL("+n+"): " + text + (except==null||n==0 ? "" : " -- (not to "+except+")" ));
      storeRequest(text);                
   }



   /**
    * Store request in log. 
    */
   private void storeRequest(String text)
   {
     /* 
      * Log last update to each item. 
      * Assume those updates are idempotent 
      */
     String[] arg = text.split("\\s+", 3);
     String prefix = arg[0];
  
     if (arg[0].matches("ALIAS|ICON|TAG|RMTAG|USER|RMUSER")) {
         if (arg[0].matches("RMTAG"))
            prefix = "TAG";
         if (arg[0].matches("RMUSER"))
            prefix = "USER";
         prefix = prefix + " " + arg[1];
         
         /* Remove earlier updates for the same item */
         _cmds.remove(prefix);
         
     }
     else if (!arg[0].matches("RMITEM"))
         _cmds.values().removeIf( (x)-> x.matches("(ALIAS|ICON|TAG) "+arg[1]));
     
     else if (!arg[0].matches("RMNODE"))
         /* Remove all USER entries with that particular node */
         _cmds.values().removeIf( (x)-> x.matches("USER .*@"+arg[1]));
  
     else if (!arg[0].matches("SAR"))
         return;
     
     /* Don't store deletions */
     if (!text.matches(".*(NULL|OFF)") && !text.matches("RM.*")) 
        _cmds.put(prefix, text);
     
   }
   


   private void playbackLog(String dest)
   {
        for (Map.Entry<String, String> entry: _cmds.entrySet())
            if (!entry.getValue().matches("USER .*@"+dest))
                sendRequest(dest, entry.getValue());
   }
   
   
   
   /**
    * Process the request.
    * Return false if request is rejected.
    */
   protected boolean processRequest(Station sender, String text)
   {    
      _api.log().debug("RemoteCtl", "processRequest - from "+sender.getIdent()+": "+text);
      String[] arg = text.split("\\s+", 2);
      if (arg.length == 0)
         return false;
         
      String args = (arg.length==1 ? null : arg[1]);
      boolean p = false, propagate = true;    
      
      /* Messaging is a special case. Just pass it on to mailbox */
      if (arg[0].equals("MSG")) {
          p = doMessage(sender, args);
          return p; 
      }
      if (arg[0].equals("CON")) {
         p = doConnect(sender, args);
         propagate = false;
      }
         
      /* Fail if not CON and not connected */
      else if ((!_parentCon || !sender.getIdent().equals(_parent)) 
            && !_children.containsKey(sender.getIdent()))
          p = false;      
      else if (arg[0].equals("ALIAS"))
          p = doSetAlias(sender, args);
      else if (arg[0].equals("ICON"))
          p = doSetIcon(sender, args);
      else if (arg[0].equals("TAG"))
          p = doSetTag(sender, args);
      else if (arg[0].equals("RMTAG"))
          p = doRemoveTag(sender, args);          
      else if (arg[0].equals("SAR"))
          p = doSetSarMode(sender, args);
      else if (arg[0].equals("USER"))
          p = doUser(sender, args, true);  
      else if (arg[0].equals("RMUSER"))
          p = doUser(sender, args, false);
      else if (arg[0].equals("RMNODE"))
          p = doRemoveNode(sender, args); 
      else if (arg[0].equals("RMITEM"))
          p = doRemoveItem(sender, args);
          
      /* If command returned true, propagate the request further 
        * to children and parent, except the originating node.
        */
      _log.info(null, "Recv < "+sender.getIdent()+": "+text+ (p ? " -- OK" : " -- FAILED"));
      if (!p)
         return false;
     
      if (propagate)
         sendRequestAll(text, sender.getIdent());
      
      return true;
   }
   
   
    /** 
     * Remove tags, aliases and icons associated with an item 
     */
    protected boolean doRemoveItem(Station sender, String arg) {
        _api.log().debug("RemoteCtl", "Got RMNODE from "+sender.getIdent()+": "+arg);
        _api.getDB().removeItem(arg.trim());
        return true;
    }
    
    
    
   
    protected boolean doRemoveNode(Station sender, String arg) {
        _api.log().debug("RemoteCtl", "Got RMNODE from "+sender.getIdent()+": "+arg);
        _nodeDown.cb(getMycall(), arg);
        return true;
    }
   
   
   
    /* 
     * Message to user. Just pass it on to a subscriber. 
     */
    protected boolean doMessage(Station sender, String arg)
    {
        _api.log().debug("RemoteCtl", "Got message from "+sender.getIdent()+": "+arg);
        if (_pmsg != null) 
            return _pmsg.handleMessage(sender, null, arg);
        return false;
    }

    
    
    /* 
     * Commands should perhaps be "plugin" modules or plugin-modules should
     * be allowed to add commands 
     */

    protected boolean doConnect(Station sender, String arg)
    {
       /* 
        * If not connected already, add sender to children list.
        */
        
        _api.log().debug("RemoteCtl", "Got CON from "+sender.getIdent()+": "+arg);
        
        if ((!_parentCon || !sender.getIdent().equals(_parent)) 
               && !_children.containsKey(sender.getIdent()))       
        {
            _log.info(null, "Connection from child: "+sender.getIdent()+" established");
            if (!_cmds.isEmpty())
                _log.info(null, "Playback command log");
                
            /* 
             * Play back log, but wait until this function has 
             * returned and reply is sent 
             */
            gc.schedule( ()->
                playbackLog(sender.getIdent()), 3, TimeUnit.SECONDS);
        }
        _children.put(sender.getIdent(), new Date());
        return true;
   }

   
   
   
   protected boolean doUser(Station sender, String args, boolean add)
   {
        if (args == null) {
            _api.log().warn("RemoteCtl", "Missing arguments to remote USER or RMUSER command");
            return false;
        }
        String u = args.trim();
        if (add)
            _addUser.cb(sender.getIdent(), u); 
        else
            _removeUser.cb(sender.getIdent(), u);
        return true;
    }
    
    
       
       
   /**
    * Set SAR mode. 
    * @param sender The station that sent the commmand.
    * @param args The arguments of the SAR command: ON or OFF.
    * @return true if success.
    */
   protected boolean doSetSarMode(Station sender, String args)
   {
       if (args == null) {
          _api.log().warn("RemoteCtl", "Missing arguments to remote SAR command");
          return false;
       }
       if ("OFF".equals(args.trim())) {
           _api.log().debug("RemoteCtl", "Clear SAR-mode from "+sender.getIdent());
           _api.clearSar();
       }
       else {      
           String[] arg = args.split("\\s+", 3);
           if (arg.length < 3)
           {
               _api.log().warn("RemoteCtl", "Remote SAR command syntax error");
               return false;
           }
           boolean nohide = arg[2].matches("\\[NOHIDE\\].*");
           
           _api.log().debug("RemoteCtl", "Set SAR-mode from "+sender.getIdent());
           String filter = ("NONE".equals(arg[1]) ? "" : arg[1]);
           _api.setSar(arg[2], arg[0], filter, !nohide);
       }
       return true;
   }
   
   
   /**
    * Set a tag on a point object. 
    * @param sender The station that sent the commmand.
    * @param args The arguments of the TAG command.
    * @return true if success.
    */
   protected boolean doSetTag(Station sender, String args)
   {
      if (args == null) {
          _api.log().warn("RemoteCtl", "Missing arguments to remote TAG command");
          return false;
      }
      
      _api.log().debug("RemoteCtl", "Set TAG from "+sender.getIdent());
      String[] arg = args.split("\\s+", 2);
      
      PointObject item = _api.getDB().getItem(arg[0].trim(), null);
      arg[1] = arg[1].trim();
      
      if (item == null)
          item = newItem(arg[0].trim());   
      item.setTag(arg[1]);
      return true;
   }
   
        
   /**
    * Remove a tag on a point object. 
    * @param sender The station that sent the commmand.
    * @param args The arguments of the RMTAG command.
    * @return true if success.
    */
   protected boolean doRemoveTag(Station sender, String args)
   {
      if (args == null) {
          _api.log().warn("RemoteCtl", "Missing arguments to remote TAG command");
          return false;
      }
      
      _api.log().debug("RemoteCtl", "Remove TAG from "+sender.getIdent());
      String[] arg = args.split("\\s+", 2);
      
      PointObject item = _api.getDB().getItem(arg[0].trim(), null);
      arg[1] = arg[1].trim();
      
      if (item == null)
          return false;  
      item.removeTag(arg[1]);
      return true;
   } 
   
   
   /**
    * Set an alias on a trackerpoint. 
    * @param sender The station that sent the commmand.
    * @param args The arguments of the ALIAS command.
    * @return true if success.
    */
   protected boolean doSetAlias(Station sender, String args)
   {
      if (args == null) {
          _api.log().warn("RemoteCtl", "Missing arguments to remote ALIAS command");
          return false;
      }
      
      _api.log().debug("RemoteCtl", "Set ALIAS from "+sender.getIdent());
      String[] arg = args.split("\\s+", 2);
      
      TrackerPoint item = _api.getDB().getItem(arg[0].trim(), null);
      arg[1] = arg[1].trim();
      if ("NULL".equals(arg[1]))
         arg[1] = null;
      
      if (item == null)
          item = newItem(arg[0].trim());   
      item.setAlias(arg[1]);
      return true;
   }
      


   /**
    * Set an icon on a trackerpoint. 
    * @param sender The station that sent the commmand.
    * @param args The arguments of the ICON command.
    * @return true if success.
    */
   protected boolean doSetIcon(Station sender, String args)
   {
      if (args == null) {
          _api.log().warn("RemoteCtl", "Missing arguments to remote ICON command");
          return false;
      }
      
      _api.log().debug("RemoteCtl", "Set ICON from "+sender.getIdent());
      String[] arg = args.split("\\s+", 2);
      
      TrackerPoint item = _api.getDB().getItem(arg[0].trim(), null);
      arg[1] = arg[1].trim();
      if ("NULL".equals(arg[1]))
         arg[1] = null;
      else 
         arg[1] = arg[1].trim();
         
      if (item == null)
          item = newItem(arg[0].trim());
      item.setIcon(arg[1]);
      return true;
   } 
       

       
   private void disconnectChild(String ident) 
   {
        _nodeDown.cb(getMycall(), ident);
        _children.remove(ident);
         sendRequestAll("RMNODE "+ident, ident);
   }
   
       
       
       
   private AprsPoint newItem(String ident)
   {
       String[] x = ident.split("@");
       if (x.length >= 2) {
           Station s = _api.getDB().getStation(x[1].trim(), null); 
           if (s == null)
              s = _api.getDB().newStation(x[1].trim());
           return _api.getDB().newObject(s, x[0].trim()); 
       }
       else
           return _api.getDB().newStation(ident);
   }
   


   public boolean isEmpty() 
       { return _parent == null && _children.size() == 0; }    


   
   public String toString() {
      String res = (_parent==null || !_parentCon ? "" : _parent);
      for (String x : getChildren())
          res += " "+x;  
      return res; 
   }
   


   /* 
    * Thread to refresh connection to "parent" every 20 minutes. 
    * Parent removes a child if its timestamp is older than 40 minutes.
    */
   public void run()
   {
      while (true) 
      try {
         Thread.sleep(35000);

         while (true) {
            if (_parent != null && _tryPause <= 0) {
               _api.log().debug("RemoteCtl", "Send CON: "+_parent);
               sendRequest(_parent, "CON");
            }
            if (_tryPause > 0)
                _tryPause--;
            
            for (String x : getChildren()) 
                if (_children.get(x).getTime() + 2400000 <= (new Date()).getTime()) {
                   _log.info(null, ""+x+" disconnected (timeout)");
                   disconnectChild(x);
                }
            Thread.sleep(1200000);
         }
      } catch (Exception e) {}
   } 
}



