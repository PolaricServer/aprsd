 
 package no.polaric.aprsd;
 import java.io.*;

 
 /* Communication device */
 
 public abstract class CommDevice implements Runnable {
     
    protected ServerAPI     _api; 
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
    
    
       
    public CommDevice(ServerAPI api, String id) {
        _api= api; 
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
 
