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

   protected class Subscriber implements MessageProcessor.MessageHandler
   {
      public boolean handleMessage(Station sender, String text)
      {
          System.out.println("*** RemoteCtl: "+sender.getIdent() + " > '"+ text + "'");
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
   private Set<String> _children = new HashSet<String>();
   private String _parent;
   private Thread _thread;
   
   private MessageProcessor _msg;
   private StationDB _db;

   public String getParent()
       { return _parent; }
       
   public Set<String> getChildren()
       { return _children; }
       
   
   private int threadid=0;    
   public RemoteCtl(Properties config, MessageProcessor mp, StationDB db)
   {
       String myCall = config.getProperty("remotectl.mycall", "N0CALL").trim().toUpperCase();
       _parent = config.getProperty("remotectl.connect", null);
          
       mp.subscribe(myCall, new Subscriber(), true);
       _msg = mp;
       _db = db;
       if (_parent != null) {
          _parent.trim();
          _thread = new Thread(this, "RemoteCtl-"+(threadid++));
          _thread.start();
       }
   }


   public void reportFailure(String id)
   {
      System.out.println("*** WARNING: Failed to deliver message");
      _children.remove(id);
   }
   
   /** 
    * Send request to given destination.
    */
   public void sendRequest(String dest, String text)
     { _msg.sendMessage(dest, text, true, true, null); }

     
   /**
    * Send request to all. To parent and children servers. 
    * may specify an exception. 
    */  
   public void sendRequestAll(String text, String except)
   {
      if (_parent != null && !_parent.equals(except))
         _msg.sendMessage(_parent, text, true, true, this);
      for (String r: _children)
         if (!r.equals(except))
            _msg.sendMessage(r, text, true, true, this);
   }


   /**
    * Process the request.
    * Return false if request is rejected.
    */
   protected boolean processRequest(Station sender, String text)
   {    
      String[] arg = text.split("\\s+", 2);
      if (arg.length == 0)
         return false;
         
      String args = (arg.length==1 ? null : arg[1]);
      boolean p = false;    
      if (arg[0].equals("CON"))
         p = doConnect(sender, args);
      if (arg[0].equals("ALIAS"))
         p = doSetAlias(sender, args);
      if (arg[0].equals("ICON"))
         p = doSetIcon(sender, args);

       /* If command returned true, propagate the request further 
        * to children and parent, except the originating node.
        */
      if (p)
         sendRequestAll(text, sender.getIdent());

       /* If the originating node is not parent or child,
        * add it to children list.
        */
      if (_parent == null || !_parent.equals(sender.getIdent()))
         _children.add(sender.getIdent());  
      return true;
   }



   /* Commands should perhaps be "plugin" modules */

   protected boolean doConnect(Station sender, String arg)
   {
      System.out.println("*** CONNECT from "+sender.getIdent());
      return false;
   }



   protected boolean doSetAlias(Station sender, String args)
   {
      if (args == null) {
          System.out.println("*** WARNING: missing arguments to remote ALIAS command");
          return false;
      }
      
      System.out.println("*** SET ALIAS from "+sender.getIdent());
      String[] arg = args.split("\\s+", 2);
      
      AprsPoint item = _db.getItem(arg[0].trim());
      arg[1] = arg[1].trim();
      if ("NULL".equals(arg[1]))
         arg[1] = null;
         
      if (item != null)
        item.setAlias(arg[1]);
      return true;
   }
      

   /* Note: These two methods are almost identical */
   protected boolean doSetIcon(Station sender, String args)
   {
      if (args == null) {
          System.out.println("*** WARNING: missing arguments to remote ICON command");
          return false;
      }
      
      System.out.println("*** SET ICON from "+sender.getIdent());
      String[] arg = args.split("\\s+", 2);
      
      AprsPoint item = _db.getItem(arg[0].trim());
      arg[1] = arg[1].trim();
      if ("NULL".equals(arg[1]))
         arg[1] = null;
         
      if (item != null)
        item.setIcon(arg[1]);
      return true;
   } 
       
       
   public boolean isEmpty() 
       { return _parent == null && _children.size() == 0; }    
   
   
   public String toString() {
      String res = (_parent==null ? "" : _parent);
      for (String x : _children)
          res += " "+x;  
      return res; 
   }
   
   
   /* 
    * Thread to refresh connection to "parent" every 10 minutes
    */
   public void run()
   {
      while (true) {
         System.out.println("*** RemoteCtl: Send CON");
         sendRequest(_parent, "CON");
         try {
            Thread.sleep(600000);
         } catch (Exception e) {}
     }
   } 
   
}
