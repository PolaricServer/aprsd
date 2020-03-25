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
 
package no.polaric.aprsd;
import java.util.regex.*;
import java.io.*;
import java.net.*;
import java.util.*;


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


   /*
    * The nodes will form a overlay network organised in a strict
    * hierarchy. Requests will be sent to parent, but also to
    * children. There is a scalability problem here. Therefore, we
    * may consider to allow filters, i.e. a child may request a
    * filter or area of interest. Based on callsign or geographical
    * position. 
    */
   private Map<String, Date> _children = new HashMap<String, Date>();
   private String  _parent;
   private boolean _parentCon = false; 
   private Thread  _thread;
   
   private MessageProcessor _msg;
   private ServerAPI _api;
   private Logfile   _log;
    
   private LinkedHashMap<String, String> _cmds = new LinkedHashMap(); 
   

   public String getParent()
       { return _parent; }
       
   public Set<String> getChildren()
       { return _children.keySet(); }
       
   
   private int threadid=0;    
   public RemoteCtl(ServerAPI api, MessageProcessor mp)
   {
       String myCall = api.getProperty("remotectl.mycall", "").toUpperCase();
       if (myCall.length() == 0)
           myCall = api.getProperty("default.mycall", "NOCALL").toUpperCase();
       _parent = api.getProperty("remotectl.connect", null);
       _log = new Logfile(api, "remotectl", "remotectl.log");
       if (_parent != null) 
          _parent = _parent.trim().toUpperCase();
       if ("".equals(_parent))
          _parent = null;
       mp.subscribe(myCall, new Subscriber(), true);
       _msg = mp;
       _api = api;
       _thread = new Thread(this, "RemoteCtl-"+(threadid++));
       _thread.start();
   }


   private int _try_parent = 0;

   /* Implements: Interface MessageProcessor.Notification */
   public void reportFailure(String id)
   {
      _api.log().warn("RemoteCtl", "Failed to deliver message to: "+id);
      if (id.equals(_parent)) {
          _parentCon = false;
          _log.info(null, "Connection to parent: "+id+ " failed");
      }
      _children.remove(id);
   }
   
   
   /* Implements: Interface MessageProcessor.Notification */
   public void reportSuccess(String id)
   {
      if (!_parentCon && id.equals(_parent)) {
          _log.info(null, "Connection to parent: "+id+ " established");
          _parentCon = true;
      }
   }
   
   
   /** 
    * Send request to given destination.
    */
   public void sendRequest(String dest, String text)
   { 
      _msg.sendMessage(dest, text, true, true, this);
      _log.log("[> "+dest+"] "+text);
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
         _log.log("[> ALL("+n+")] " + text + (except==null||n==0 ? "" : " -- (not to "+except+")" ));
      storeRequest(text);                
   }



   private void storeRequest(String text)
   {
     /* Log last update to each item */
     String[] arg = text.split("\\s+", 3);
     String prefix = arg[0], suffix = null;
  
     if (arg[0].matches("ALIAS|ICON|TAG|RMTAG")) {
         prefix = prefix + " " + arg[1];
         suffix = arg[2];
     }
     else if (arg[0].matches("SAR"))
         suffix = arg[1] + (arg.length > 2 ? " " + arg[2] : "");
     else
         return;
     _cmds.remove(prefix);
     if (!suffix.matches("NULL|OFF"))
         _cmds.put(prefix, suffix);
   }
   


   private void playbackLog(String dest)
   {
       for (Map.Entry<String, String> entry: _cmds.entrySet())
          sendRequest(dest, entry.getKey()+" "+entry.getValue());
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
           
      /* If command returned true, propagate the request further 
        * to children and parent, except the originating node.
        */
      _log.log(" [< "+sender.getIdent()+"] "+text+ (p ? " -- OK" : " -- FAILED"));
      if (!p)
         return false;
     
      if (propagate){
         storeRequest(text);
         sendRequestAll(text, sender.getIdent());
      }
      return true;
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
            playbackLog(sender.getIdent());
        }
        _children.put(sender.getIdent(), new Date());
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
         Thread.sleep(20000);
         while (true) {
            if (_parent != null) {
               _api.log().debug("RemoteCtl", "Send CON: "+_parent);
               sendRequest(_parent, "CON");
            }
            
            for (String x : getChildren()) 
                if (_children.get(x).getTime() + 2400000 <= (new Date()).getTime()) {
                   _log.info(null, ""+x+" disconnected (timeout)");
                   _children.remove(x);
                }
            Thread.sleep(1200000);
         }
      } catch (Exception e) {}
   } 
}
