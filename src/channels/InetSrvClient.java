 
/* 
 * Copyright (C) 2025 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.net.*;
import java.io.*;


/** 
 * APRS-IS client. 
 * Typically igate or end-user. 
 */
 
public class InetSrvClient extends InetSrvChannel.Client implements Runnable 
{
    private   ServerAPI _api;
    private   InetSrvChannel _chan;
    private   Socket _conn;
    private   String _ipaddr;
    private   InputStream  _istream;
    private   OutputStream _ostream;
    private   PrintWriter    _writer;
    private   BufferedReader _reader;
 
    private   String _userid = "NOCALL";
    private   boolean _verified = false;
    private   AprsFilter _filter;
    private   boolean _login;
    
    
    
    public InetSrvClient(ServerAPI api, Socket conn, InetSrvChannel chan) 
    {
        _api = api;
        _conn = conn;
        _chan = chan;
        _ipaddr = _conn.getInetAddress().getHostAddress();
        
        if (conn != null) {
            Thread workerthread = new Thread(this);
            workerthread.start();
        }
    }
    

    /**
     * Passcode verification. 
     * This is the algorithm used to generate and verify APRS-IS passcodes. 
     * A passcode is a simple 15 bit hash of the callsign (without the SSID) 
     * given as a decimal number. 
     */
    protected void verify(String call, String pass) {
	    String[] x = call.split("-");
	    String c = x[0];
	    int hash = 0x73e2; 
        int i = 0; 
        int len = c.length(); 

        // hash callsign two bytes at a time 
        for (i=0; i<len; i+= 2) { 
            hash ^= c.charAt(i) << 8; 
            hash ^= c.charAt(i+1); 
        } 
        _verified =  (Integer.parseInt(pass) == (hash & 0x7fff)); 
    }
    
    
    /**
     * User login with passcode and filter command. 
     */
    protected void getLogin(InputStream istr) throws IOException {
       /*
        * Format:  user mycall[-ss] pass passcode[ vers softwarename softwarevers[ UDP udpport][ servercommand]] 
        * See http://www.aprs-is.net/connecting.aspx
        */
        String line = "";
        String[] fline;
        String[] x;
        while (true) {
            line = _reader.readLine();
            if (line==null)
                return;
            if (line.startsWith("#") || line.length() < 1) 
                continue;
            
            /* Get filter command if it exists */
            fline = line.split("(filter|FILTER)(\\s+)");
            if (fline[1] != null)
                _filter = AprsFilter.createFilter(fline[1]);
            
            x = fline[0].split("\\s+");
            if (x.length < 2) {
                _writer.println("# Invalid login string, too few arguments");
                _writer.flush();
                continue;
            }
            break;
        }
        if (x[0].matches("user|USER"))
            _userid = x[1].toUpperCase();
        if (x.length > 3 && x[2].matches("pass|PASS"))
            verify(_userid, x[3]);
        _writer.println("# Login ok user="+_userid+(_verified? ", " : ", not ")+"verified");
        _writer.flush();
        _api.log().info("InetSrvChannel", "User "+_userid+" verification "+(_verified ? "Ok": "Failed"));
        _api.log().info("InetSrvChannel", "Filter: "+_filter);
        _login = true; 
    }
    
    
    
    
    protected void close() {
        try { 
          if (_istream != null) _istream.close(); 
          if (_ostream != null)  _ostream.close();
          if (_conn != null) _conn.close(); 
          Thread.sleep(500);
       } catch (Exception e) {}
    }
    
    
    /**
     * q-code processing. 
     *
     * NOTE: We assum that all packets come from verified connections and that connecting 
     * nodes are clients (igates or UI-devices). 
     *
     * See https://www.aprs-is.net/q.aspx (this is how I interpret it in this 
     * context. It is not easy to understand. Should we support I-construct?? 
     * Suggestions are appreciated. 
     */
    protected void qProcess(AprsPacket p) {
        String[] qq = p.getQcode();
        String mycall = _api.getProperty("default.mycall", "NOCALL");
        
        /* FROM != id of connected gate */
        if (!p.from.equals(_userid)) {
            if (qq != null) {
                if (qq[0].matches("qA[rR]"))
                    p.setQcode("qAo", qq[1]);
                else if (qq[0].matches("qAS"))
                    p.setQcode("qAO", qq[1]);
                else if (qq[0].matches("qAC") && !qq[1].equals(_userid) 
                   && !qq[1].equals(mycall))
                    p.setQcode("qAO", qq[1]);
            }
            else
                p.setQcode("qAO", _userid);
        }
        
        /* FROM == id of connected gate */
        else {
            p.via = "TCPIP*";
            if (qq != null)
                p.setQcode("qAC", mycall); 
            else
                p.setQcode("qAS", _userid);
        }
    }
    
    
    /**
     * Process incoming packet
     */
    protected void processPacket(String str) {
        if (str == null || str.equals("") || str.charAt(0) == '#')
            return;
        if (!_verified)
            return;
        AprsPacket p = AprsPacket.fromString(str);
        
        /* q-code processing */
        qProcess(p);
        
        /* Pass packet to receivers */
        _chan.receivePacket(p, false);
        /* Send packet to other clients (except this) */
        _chan.sendPacket(p, this);
    }
    
    
    /**
     * Send packet to connected client.
     */
    public boolean sendPacket(AprsPacket p) {
        if (_login && _filter != null && _filter.test((p))) {
            _writer.println(p.toString());
            _writer.flush();
            return true;
        }
        return false; 
    }
    
    
    
    /* Main thread. Get incoming packets and commands from connected client. */
    public void run() {
        try {          
            _api.log().info("InetSrvChannel", "Incoming connection from: "+_ipaddr);
            _istream = _conn.getInputStream();
            _ostream = _conn.getOutputStream();
            _writer = new PrintWriter(_ostream);
            _reader = new BufferedReader(new InputStreamReader(_istream));

            _conn.setKeepAlive(true);
            getLogin(_istream);
            while (!_conn.isClosed() ) {
                String inp = _reader.readLine();
                if (inp == null)
                    break;
                processPacket(inp);
            }
            _api.log().info("InetSrvChannel", "Connection closed: "+_ipaddr);
        }
        catch (Exception ex) {
             _api.log().warn("InetSrvChannel", "Connection ("+_ipaddr+"): "+ex.getMessage());
             ex.printStackTrace(System.out);
        }
        finally {
            /* Close the connection */
            close();
            _chan.removeClient(this);
        }
    }

}

































































































