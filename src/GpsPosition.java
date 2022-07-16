 
/* 
 * Copyright (C) 2016-2022 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import java.util.*;
import java.io.*;
import uk.me.jstott.jcoord.*;
import gnu.io.*;
import java.text.*;



/**
 * Tracking of our own position
 */
public class GpsPosition extends OwnPosition
{
    public static SimpleDateFormat nmeadateformat = new SimpleDateFormat("ddMMyy");
    public static SimpleDateFormat nmeatimeformat = new SimpleDateFormat("ddMMyy HHmmss.SSS");
    public static SimpleDateFormat linuxtimeformat = new SimpleDateFormat("MMddHHmmyy.ss");

    
    public class GpsParser extends Thread {
        private  SerialPort     _serialPort;
        private  BufferedReader _in;
        private  boolean        _running = true;

        
        public void deactivate() {
            _running = false;
            interrupt();
            try { join(); } catch(Exception e) {} ; 
        }
        
        
        public void run() {
            int retry = 0;
            int readerror = 0;
            while (_running)
            {
                var running = true;
                if (retry <= _max_retry || _max_retry == 0) 
                    try { 
                        long sleep = 30000 * (long) retry;
                        if (sleep > _retry_time) 
                            sleep = _retry_time; /* Default: Max 30 minutes */
                        Thread.sleep(sleep); 
                    } 
                    catch (Exception e) 
                        {break;} 
                else {
                    _api.log().error("GpsPosition", "Couldn't connect to GPS on'"+_portName+"' - giving up");
                    break;
                }

                try {
                    Thread.sleep(2000);
                    _api.log().info("GpsPosition", "Initialize GPS on "+_portName);
                    _serialPort = connect(); 
                    if (_serialPort != null) {
                        _in = new BufferedReader(new InputStreamReader(_serialPort.getInputStream()));
                        while (_running) {
                            try {
                                String inp = _in.readLine();
                                receivePacket(inp);
                                readerror = 0;
                            }
                            catch(IOException e)
                            {
                                if (readerror > 15) {
                                    _api.log().warn("GpsPosition", "read error from " + _portName);
                                    e.printStackTrace(System.out);
                                    throw new RuntimeException("Too many read errors from " + _portName);
                                }
                                readerror++;
                                Thread.sleep(15000); 
                            }
                        }
                        /* Close port */
                        _in.close();
                        _serialPort.close();
                    }
                }
                catch(NoSuchPortException e)
                {
                    _api.log().warn("GpsPosition", "serial port " + _portName + " not found");
                }
                catch(Exception e)
                {   
                    e.printStackTrace(System.out); 
                    _serialPort.close();
                }
                retry++;
            }
        }
    }



    private   String    _portName; 
    private   int       _baud;
    transient private   int       _max_retry;
    transient private   long      _retry_time; 
    transient private   int       _minDist, _turnLimit; 
    transient private   boolean   _adjustClock; 
    transient private   GpsParser _gpsParser;
    transient private   boolean   _gpsOn;
    transient private   boolean   _gpx_fix = false;
    transient private   int       _course = 0, _prev_course = 0, _prev_speed = 0;
    transient private   Reference _prev_pos = null;
    transient private   Date      _prev_timestamp; 
    transient private   XReports  _xreports;


    public GpsPosition(ServerAPI api) 
    {
        super(api);    
    }

    
    
    @Override public void init() {
          
        super.init();
        
         _gpsOn = _api.getBoolProperty("ownposition.gps.on", false);
        _portName = _api.getProperty("ownposition.gps.port", "/dev/ttyS1");
        _baud = _api.getIntProperty("ownposition.gps.baud", 9600);
        _minDist = _api.getIntProperty("ownposition.tx.mindist", 150);
        _turnLimit = _api.getIntProperty("ownposition.tx.turnlimit", 30);
        _max_retry = _api.getIntProperty("ownposition.gps.retry", 0);
        _retry_time = Long.parseLong(_api.getProperty("ownposition.gps.retry.time", "30")) * 60 * 1000;         
        _adjustClock = _api.getBoolProperty("ownposition.gps.adjustclock", false); 
        _xreports = new XReports();

        if (_gpsParser!=null)
            _gpsParser.deactivate();
        _gpsParser = null;
        if (_gpsOn) {
            _gpsParser = new GpsParser();
            _gpsParser.start();
        }
    }
    
    

    private void receivePacket (String p)
    {
        if (p.charAt(0) != '$')
            return;

        /* Checksum (optional) */
        int i, checksum = 0;
        for (i=1; i < p.length() && p.charAt(i) !='*'; i++) 
            checksum ^= p.charAt(i);
        
        /* Sometimes two NMEA lines are merged -> garbage */
        if (i >= p.length())
            return;
        if ((p.length() - i) > 3)
            return;
        if (p.charAt(i) == '*') {
            int c_checksum = Integer.parseInt(p.substring(i+1, p.length()), 16);
            if (c_checksum != checksum) 
                return;
        } 
        String[] tok = p.split(",");
        if ( "$GPRMC".equals(tok[0])) 
            do_RMC(tok);
        else if ("$GPGGA".equals(tok[0])) {}
    }



    private int fcnt=300;
    private void do_RMC(String[] arg)
    {
        if (arg.length != 12 && arg.length != 13)   /* Ignore if wrong format, NMEA 2.3 and beyond use 13 fields */
            return;

        if (!"A".equals(arg[2])) {
            _gpx_fix = false;
            if (++fcnt>=360) {
                _api.log().info("GpsPosition", "Still waiting for GPS to get fix...");
                fcnt=0;
            }
            return;
        }
        _gpx_fix = true;
        try {
            Date now = new Date();
            String ndate = nmeadateformat.format(now);
            nmeatimeformat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date ts = nmeatimeformat.parse(ndate+" "+arg[1]);
                /* we may use date string arg[9] from GPS but it is sometimes wrong */

            /* Position parsing is taken from AprsParser.java */
            Double latDeg = Integer.parseInt(arg[3].substring(0,2)) + Double.parseDouble(arg[3].substring(2,7))/60;
            if (arg[4].equals("S"))
                latDeg *= -1;
            Double lngDeg = Integer.parseInt(arg[5].substring(0,3)) + Double.parseDouble(arg[5].substring(3,8))/60;
            if (arg[6].equals("W"))
                lngDeg *= -1;
            Reference pos = new LatLng(latDeg, lngDeg); 
          
            /* Speed and course */
            int speed = Math.round( Float.parseFloat(arg[7]) * KNOTS2KMH);
            int crs = (arg[8].equals("") ? 0 : Math.round( Float.parseFloat(arg[8]) ));   // FIXME??       
   
            if (_adjustClock) 
                updateTime(ts);

            updatePosition(ts, pos, crs, speed);
        }
        catch (Exception e) {e.printStackTrace(System.out);}    
    }



    private int cnt = 0;
    private void updatePosition(Date t, Reference pos, int crs, int speed)
    {
        /* Update position locally on map */
        if (++cnt >= 5 || speed > 1 && ( course_change(crs, getCourse(), 30) && cnt >= 2)) {
           cnt = 0;
           AprsHandler.PosData pd = new AprsHandler.PosData(pos, crs, speed, (char) 0, (char) 0); 
           update(t, pd, _comment, "(GPS)");
        }
    }


    private int tcnt = 660;
    private void updateTime(Date t)
    {
       if (++tcnt >= 720) { /* 12 minutes */
          tcnt = 0;
          try {
            _api.log().info("GpsPosition", "Adjusting time from GPS: "+t);
             String cmd = "sudo date " + linuxtimeformat.format(t); 
             Runtime.getRuntime().exec(cmd);
          } catch (IOException e1) 
             {  e1.printStackTrace(); }

       }
    }



    /* Adapted from Polaric Tracker/Arctic Tracker code */

    @Override protected boolean should_update() 
    {
        if (!_gpsOn) 
            return super.should_update();
        
        float dist = (_prev_pos == null ? 0 : distance(_prev_pos));
        long tdist = (_prev_timestamp == null  ?
            getUpdated().getTime() : (getUpdated().getTime() - _prev_timestamp.getTime())) / 1000;
        float est_speed  = (tdist==0) ? 0 : ((float) dist / (float) tdist);
       
       /* 
        * Note that est_speed is in m/s while
        * the other speed fields are in km/h 
        */  

        if ( _timeSinceReport >= _maxPause

            /* Change in course */   
            || ( est_speed > 0.8 && getCourse() >= 0 && _prev_course >= 0 && 
                course_change(getCourse(), _prev_course, _turnLimit))
                
            /* Send report when starting or stopping */             
            || ( _timeSinceReport >= _minPause &&
               (( getSpeed() < 3  &&  _prev_speed > 15 ) ||
                ( _prev_speed < 3  &&  getSpeed() > 15 )))                      

            /* Distance threshold on low speeds */
            || ( _timeSinceReport >= _minPause && est_speed <= 1 && dist >= _minDist )
           
            /* Time period based on average speed */
            || ( est_speed>0 && _timeSinceReport >= smartbeacon(_minDist, est_speed, _minPause ))              
        )                   
        { 
            _prev_course = getCourse();
            _prev_speed = getSpeed();
            _prev_pos = getPosition();
            _prev_timestamp = getUpdated();

            /* Enqueue extra reports */
            _xreports.enqueue(new XReports.XRep(getUpdated(), getPosition()), 2);
            _xreports.enqueue(new XReports.XRep(getUpdated(), getPosition()), 5);
            return true; 
        }
        return false; 
    }

    
    
    /* Extra reports (in comment field) */
    @Override protected String xReports(Date ts, LatLng pos) {
        XReports.XRep curr = new XReports.XRep(ts, pos.getLatitude(), pos.getLongitude());
        String rep = _xreports.encode(curr);
        return rep;
    }
    
    
    
    /* Updated smartbeaconing calculation - from Arctic Tracker code */
    private int smartbeacon(int mindist, float speed, int minpause) {
        float k = 0.5f;
        if (minpause > 30)
            k = 0.3f;
    
        double x = mindist - Math.log10(speed*k) * (mindist-32); 
        if (speed < 4)
            x -= (5-speed)*4;
        if (x < minpause)
            x = minpause;
        return (int) Math.round(x);
    }



    
    /**
     * Setup the serial port.
     * FIXME: There is an identical function in TncChannel.java
     */
    private SerialPort connect () throws Exception
    {
        CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(_portName);
        if ( portIdentifier.isCurrentlyOwned() )
            _api.log().error("GpsPosition", "Port "+ _portName + " is currently in use");
        else
        {
            CommPort commPort = portIdentifier.open(this.getClass().getName(), 2000);       
            if ( commPort instanceof SerialPort )
            {
                SerialPort serialPort = (SerialPort) commPort;
                serialPort.setSerialPortParams(_baud, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
                serialPort.enableReceiveTimeout(3000);
                if (!serialPort.isReceiveTimeoutEnabled())
                   _api.log().warn("GpsPosition", "Timeout not enabled on serial port");
                return (SerialPort) commPort;
            }
            else
                _api.log().error("GpsPosition", "Port " + _portName + " is not a serial port.");
        }    
        return null; 
    }
   


}

