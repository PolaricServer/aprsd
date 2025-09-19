 /* 
 * Copyright (C) 2025 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
 
 package no.polaric.aprsd.channel;
 import no.polaric.aprsd.*;
 import java.io.*;

 
 /* Communication device */
 
 public abstract class CommDevice implements Runnable {
     
    protected AprsServerConfig     _conf; 
    private   String        _ident;
    protected Channel.State _state = Channel.State.OFF;
    
    private   boolean       _running = false;
    private   Thread        _thread;
    protected Worker        _worker;
    protected FailHandler   _failHandler; 
 
    
    /* To be implemented using a lambda function */
    protected interface Worker {
        void worker() throws Exception; 
    }
    protected interface FailHandler {
        void handler(); 
    }
    
    
       
    public CommDevice(AprsServerConfig conf, String id) {
        _conf= conf; 
        _ident = id;
    }
    
       
    /** Start the service */
    public void activate(Worker w, FailHandler fh) {
        _worker = w;
        _failHandler = fh;
        _running = true; 
        _thread = new Thread(this, "commDevice."+_ident);
        _thread.start();
    }
    
    
    public Channel.State getState() 
        { return _state; }

  
    /** Stop the service */
    public void deActivate() 
        { _running=false; }
    
    
    public boolean running() 
        { return _running; }
    
    
    /* Get ouput stream */
    public abstract OutputStream getOutputStream() throws IOException;
        
    /* Get input stream */    
    public abstract InputStream getInputStream() throws IOException;
    
    
    public abstract void run(); 
    
 }
 
