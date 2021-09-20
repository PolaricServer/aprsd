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
import uk.me.jstott.jcoord.*; 



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
   private int     _radius;
   private String  _parent;
   private boolean _parentCon = false; 
   private int     _tryPause = 0;
   private Thread  _thread;
   private static ScheduledExecutorService gc = Executors.newScheduledThreadPool(5);
       
   private MessageProcessor _msg;
   private MessageProcessor.MessageHandler _pmsg;
   private ServerAPI _api;
   private Logfile   _log;
    
   private LinkedHashMap<String, LogEntry> _cmds = new LinkedHashMap(); 
   private IndexedSets _users = new IndexedSets();
   private Map<String, Child> _children = new HashMap<String, Child>();
   
   private Encryption _crypt; 
   private String _encryptTo = ""; 
   private String _userInfo = ".*";
   
   
   public String getMycall()
       { return _myCall; }

   public String getParent()
       { return _parent; }
       
   public Set<String> getChildren()
       { return _children.keySet(); }

   public Child getChild(String id)
       { return _children.get(id); }
       
   public void addChild(String id, int r, Reference p) 
       { _children.put(id, new Child(r, p)); }
   
   public void updateChildTS(String id) {
        Child c = getChild(id);
        if (c != null)
            c.updateTS(); 
   }
   
   public void removeChild(String id) 
       { _children.remove(id); }
       
   public boolean hasChild(String id) 
       { return _children.containsKey(id); }
       
       
   public static class LogEntry {
        String origin; 
        String cmd;
        LogEntry(String o, String c) {
            origin=o; cmd=c; 
        }
   }
   
   public static class Child {
        Date date;
        int radius; 
        Reference pos;
        void updateTS() 
            { date = new Date(); }
        long getTime() 
            {return date.getTime();}
        Child(int r, Reference p)
            {radius=r; pos=p; date=new Date(); }
   }
   
   
   
   
   private int threadid=0;    
   public RemoteCtl(ServerAPI api, MessageProcessor mp)
   {
       _myCall = api.getProperty("remotectl.mycall", "").toUpperCase();
       if (_myCall.length() == 0)
           _myCall = api.getProperty("default.mycall", "NOCALL").toUpperCase();
       _radius = api.getIntProperty("remotectl.radius", -1); 
       _parent = api.getProperty("remotectl.connect", null);
       _log = new Logfile(api, "remotectl", "remotectl.log");
       _encryptTo = api.getProperty("remotectl.encrypt", "");
       _userInfo = api.getProperty("remotectl.userinfo", ".*");
       String key = api.getProperty("message.auth.key", "NOKEY");
       
       if (_parent != null) 
          _parent = _parent.trim().toUpperCase();
       if ("".equals(_parent))
          _parent = null;
       mp.subscribe(_myCall, new Subscriber(), true);
       
       _crypt = new Encryption( key );
       
       _msg = mp;
       _api = api;
       _thread = new Thread(this, "RemoteCtl-"+(threadid++));
       _thread.start();
   }
   
   
   public void setMailbox(MessageProcessor.MessageHandler mh) {
        _pmsg = mh;
   }

   
   public List<String> getUsers() {
        return _users.getAll();
   }
   
   
   
   private int _try_parent = 0;

   
   
   /**
    * Report failure. 
    * Implements: Interface MessageProcessor.Notification 
    */
    public void reportFailure(String id, String msg)
    {
        _api.log().debug("RemoteCtl", "Command or msg delivery failed: "+id+" ("+msg+")");
        if (!msg.matches("CON.*"))
            return;
      
        if (id.equals(_parent)) {
            disconnectParent();
            _log.info(null, "Connection to parent: "+id+ " failed: "+msg);
      
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
   
   
   /**
    * Report success. 
    * Implements: Interface MessageProcessor.Notification 
    */
    public void reportSuccess(String id, String msg)
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
     * Encode a command. Encrypt USER command if this is turned on.
     */
    public String encodeCmd(String dest, String cmd, String text) 
    {
        if (cmd==null)
            return text; 
        if (cmd.matches("USER|RMUSER")) {
            if (!dest.matches(_userInfo))
                return null;
            if (dest.matches(_encryptTo))
                text = _crypt.encrypt(text);
        }
        return cmd+" "+text;
    }
    
    
    /**
     * Decode user field. If it is encrypted, decrypt it. 
     */
    private String decodeUser(String u) {
        u = u.trim(); 
        if (u.length() == 24 || u.length() == 48)
            u = _crypt.decrypt(u);
        return u;
    }
    
   
   
   /** 
    * Send request to given destination.
    */
   public void sendRequest(String dest, String cmd, String text)
   { 
        var m = encodeCmd(dest, cmd, text);
        if (m==null)
            return; 
        _msg.sendMessage(dest, m, true, true, this);
        _log.info(null, "Send > "+dest+": "+m);
   }
   
     
   /**
    * Send request to all. To parent and children servers. 
    * may specify an exception. Exceptions are the origin of the request and nodes
    * that have no interest in the request. 
    */  
    public void sendRequestAll(String cmd, String text, String origin)
    {
        int n = 0;
        if (_parent != null && !_parent.equals(origin) && _parentCon) { 
            var m = encodeCmd(_parent, cmd, text);
            if (m != null)
                _msg.sendMessage(_parent, m, true, true, this); 
            n++; 
        }
        
        for (String r: getChildren() )
            if (!r.equals(origin) && isWithinInterest(r, text) ) {
                var m = encodeCmd(r, cmd, text);
                if (m != null)
                    _msg.sendMessage(r, m, true, true, this); 
                n++; 
            }
    
        if (n>0)
            _log.info(null, "Send > ALL("+n+"): " + text + (origin==null||n==0 ? "" : " -- (not to "+origin+")" ));
        storeRequest(cmd+" "+text, origin);                
    }


   /**
    * Return true if message is with child-node's area of interest. 
    */
   private boolean isWithinInterest(String node, String msg) {
        /* tbd */
        String[] arg = msg.split("\\s+", 3);
        if (arg.length < 2 || !arg[0].matches("ALIAS|ICON|TAG|RMTAG"))
            return true;
        Point x = _api.getDB().getItem(arg[1], null);
        Reference pos = _api.getOwnPos().getPosition();
        if (_radius <= 0 || pos == null) 
            return true; 
        if (x != null && x.distance(pos) > _radius*1000)
            return false;
        return true;
   }
   
   
   

   /**
    * Store request in log for later playback when new nodes connect. 
    */
   private void storeRequest(String text, String origin)
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
     /* This is currently not used and it may not be a good idea to store this */
     else if (!arg[0].matches("RMITEM") && _cmds != null)
        _cmds.values().removeIf( (x)-> {
            return x != null && x.cmd != null && x.cmd.matches("(ALIAS|ICON|TAG) "+arg[1]+" .*");
        });
    
     
     else if (!arg[0].matches("RMNODE"))
         /* Remove all USER entries with that particular node */
         _cmds.values().removeIf( (x)-> x.cmd.matches("USER .*@"+arg[1]));
  
     else if (!arg[0].matches("SAR"))
         return;
     
     /* Don't store deletions */
     if (!text.matches(".*(NULL|OFF)") && !text.matches("RM.*")) 
        _cmds.put(prefix, new LogEntry(text, origin));
     
   }
   

   /** 
    * Play back log entries when new node connect. 
    */
   private void playbackLog(String dest)
   {
        if (_cmds.isEmpty())
            return; 
        _log.info(null, "Playback command log");
        for (Map.Entry<String, LogEntry> entry: _cmds.entrySet())
            if (!entry.getValue().cmd.matches("USER .*@"+dest) && isWithinInterest(dest, entry.getValue().cmd) )
                sendRequest(dest, null, entry.getValue().cmd);
   }
   
   
   /**
    * To be called from (StationDBImp) when an item expires. 
    * Remove ALIAs, ICON, TAG entries for the given item from log. 
    */
   public void removeExpired(String id) {
        final var id_ = id.replaceAll("\\*", "\\\\*");
        if (_cmds !=null && !_cmds.isEmpty())
            _cmds.values().removeIf( (x)-> { 
                return x != null && x.cmd != null && x.cmd.matches("(ALIAS|ICON|TAG) "+id_+" .*");
            }
        );
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
      
      /* 
       * For some commands assume that we are connected if they come from parent. 
       * CON ack may be lost. 
       */
      if (!_parentCon && sender.getIdent().equals(_parent) && 
           arg[0].matches("ALIAS|ICON|TAG|USER|RMTAG|SAR|RMUSER|RMNODE|RMITEM")) {
           _parentCon = true; 
           _log.info(null, "Connection to parent: "+_parent+ " assumed (CON ACK may be lost)");
      }
      
      /* Connect command */
      if (arg[0].equals("CON")) {
         _api.log().debug("RemoteCtl", "Got CON from "+sender.getIdent());
         p = doConnect(sender, args);
         propagate = false;
      }
      else if (!sender.getIdent().equals(_parent) && !hasChild(sender.getIdent())) {
         _api.log().debug("RemoteCtl", "Got command from unconnected node: "+sender);
         return false; 
      }
      
      /* Fail if not CON and not connected */
      else if ((!_parentCon || !sender.getIdent().equals(_parent)) 
            && !hasChild(sender.getIdent()))
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
      else if (arg[0].equals("USER")) {
          args = decodeUser(args);
          p = doUser(sender, args, true);
      }
      else if (arg[0].equals("RMUSER")) {
          args = decodeUser(args);
          p = doUser(sender, args, false);
      }
      else if (arg[0].equals("RMNODE"))
          p = doRemoveNode(sender, args); 
      else if (arg[0].equals("RMITEM"))
          p = doRemoveItem(sender, args);
          
       /* If command returned true, propagate the request further 
        * to children and parent, except the originating node.
        */
      _log.info(null, "Recv < "+sender.getIdent()+": "+arg[0]+" "+args+ (p ? " -- OK" : " -- FAILED"));
      if (!p)
         return false;
     
      if (propagate)
         sendRequestAll(arg[0], args, sender.getIdent());
      
      return true;
   }
   
   
    /** 
     * Remove tags, aliases and icons associated with an item 
     */
    protected boolean doRemoveItem(Station sender, String arg) {
        _api.log().debug("RemoteCtl", "Got RMITEM from "+sender.getIdent()+": "+arg);
        _api.getDB().removeItem(arg.trim());
        return true;
    }
    
    
    
   
    protected boolean doRemoveNode(Station sender, String arg) {
        _api.log().debug("RemoteCtl", "Got RMNODE from "+sender.getIdent()+": "+arg);
        arg=arg.trim();
        
        /* Is the removed node a direct parent or child? */
        if (sender.getIdent().equals(arg)) {
            _users.removeAll(arg);
            if (arg.equals(_parent))
                disconnectParent(); 
            else
                disconnectChild(arg);
        }
        else
            _users.removeMatching(sender.getIdent(), ".+@"+arg);
        return true;
    }
   
   
   
    /** 
     * Message to user. Just pass it on to a subscriber. 
     */
    protected boolean doMessage(Station sender, String arg)
    {
        _api.log().debug("RemoteCtl", "Got message from "+sender.getIdent()+": "+arg);
        if (_pmsg != null) 
            return _pmsg.handleMessage(sender, null, arg);
        return false;
    }

    
    
    /** 
     * Handler for connect command. 
     * Commands should perhaps be "plugin" modules or plugin-modules should
     * be allowed to add commands 
     */
    protected boolean doConnect(Station sender, String arg)
    {
       /* 
        * If not connected already, add sender to children list.
        */
        if ((_parent == null || !sender.getIdent().equals(_parent)) 
               && !hasChild(sender.getIdent()))       
        {
            _log.info(null, "Connection from child: "+sender.getIdent()+" established");
         
            /* 
             * Now get arguments from CON command. It can be just CON without arguments
             * or with a radius, a latitude and a longitude
             */
            int rad = -1;
            float lat=0, lng=0;
            if (arg != null && arg.length() > 0) {
                if (arg.matches("\\d+\\s+\\d+(\\.(\\d)+)?\\s+\\d+(\\.(\\d)+)?"))
                {
                    String[] args = arg.split("\\s+", 3);
                    if (args.length >= 3) {
                        rad=Integer.parseInt(args[0]); 
                        lat=Float.parseFloat(args[1]);
                        lng=Float.parseFloat(args[2]);
                        System.out.println("*** radius="+rad+", lat="+lat+", lng="+lng);
                    }
                }
                else
                    _api.log().warn("RemoteCtl", "Number format error in CON request from "+sender.getIdent());
            }
            addChild(sender.getIdent(), rad, new LatLng(lat,lng));
            
            /* 
             * Play back log, but wait until this function has 
             * returned and reply is sent 
             */
            gc.schedule( ()->
                playbackLog(sender.getIdent()), 5, TimeUnit.SECONDS);
        }
        else
            updateChildTS(sender.getIdent()); 
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
            _users.add(sender.getIdent(), u);
        else 
            _users.remove(sender.getIdent(), u); 
        return true;
    }
       
       
   /**
    * Set SAR mode. 
    * @param sender The station that sent the commmand.
    * @param args The arguments of the SAR command or OFF.
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
           String arg1, arg2;
           String[] arg = args.split("\\s+", 3);
           if (arg.length < 1) {
               _api.log().warn("RemoteCtl", "Remote SAR command syntax error");
               return false;
           }
           else if (arg.length < 2) {
                arg1 = "NONE";
                arg2 = "";
           }
           else if (arg.length < 3) {
                arg1 = arg[1];
                arg2 = "";
           }
           else {
                arg1 = arg[1]; arg2=arg[2]; 
           }
           
           boolean nohide = false; 
           if (arg2.matches("\\[NOHIDE\\].*")) {
                nohide = true; 
                arg2 = arg2.substring(8); 
           }
           
           _api.log().debug("RemoteCtl", "Set SAR-mode from "+sender.getIdent());
           String filter = ("NONE".equals(arg1) ? "" : arg1);
           _api.setSar(arg2, arg[0]+"@"+sender.getIdent(), filter, !nohide);
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
          return false;   
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
        if (item==null || item.expired())
            return false; 
            
        arg[1] = arg[1].trim();
        if ("NULL".equals(arg[1]))
            arg[1] = null;
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
      if (item==null || item.expired())
        return false; 
        
      arg[1] = arg[1].trim();
      if ("NULL".equals(arg[1]))
         arg[1] = null;
      else 
         arg[1] = arg[1].trim();
      item.setIcon(arg[1]);
      return true;
   } 
       

       
   private void disconnectChild(String ident) 
   {
        _users.removeAll(ident);
        _cmds.values().removeIf( (x)-> ident.equals(x.origin));
        removeChild(ident);
        sendRequestAll("RMNODE", ident, ident);
   }
   
       
       
   private void disconnectParent()
   {    
        if (!_parentCon) 
            return;
        _users.removeAll(_parent);
        _cmds.values().removeIf( (x)-> _parent.equals(x.origin));
        _parentCon = false;
        sendRequestAll("RMNODE", _parent, _parent);
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
         Thread.sleep(60000);

         while (true) {
            if (_parent != null && _tryPause <= 0) {
                _api.log().debug("RemoteCtl", "Send CON: "+_parent);
               
                /* Now get position and radius if set */
                String arg = "";
                Reference pos = _api.getOwnPos().getPosition();
                if (_radius > 0 && pos != null) {
                    LatLng pp = pos.toLatLng(); 
                    arg += " "+_radius+" "+ 
                        ((float) Math.round(pp.getLatitude()*1000))/1000+" "+ 
                        ((float) Math.round(pp.getLongitude()*1000))/1000;
                }
                sendRequest(_parent, "CON", arg);
            }
            if (_tryPause > 0)
                _tryPause--;
            
            for (String x : getChildren()) 
                if (getChild(x).getTime() + 2400000 <= (new Date()).getTime()) {
                   _log.info(null, ""+x+" disconnected (timeout)");
                   disconnectChild(x);
                }
            Thread.sleep(1200000);
         }
      } catch (Exception e) {}
   } 
}



