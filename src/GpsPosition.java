 
/* 
 * Copyright (C) 2012 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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

    public static SimpleDateFormat nmeatimeformat = new SimpleDateFormat("ddMMyy HHmmss.SSS");
    public static SimpleDateFormat linuxtimeformat = new SimpleDateFormat("MMddHHmmyy.ss");

    public class GpsParser extends Thread {
       private  SerialPort     _serialPort;
       private  BufferedReader _in;

       public void run() {
           int retry = 0;
           while (true)
           {
               if (retry <= _max_retry || _max_retry == 0) 
                  try { 
                     long sleep = 30000 * (long) retry;
                     if (sleep > _retry_time) 
                        sleep = _retry_time; /* Default: Max 30 minutes */
                     Thread.sleep(sleep); 
                  } 
                  catch (Exception e) {break;} 
               else break;

               try {
                  System.out.println("*** Initialize GPS on "+_portName);
                  _serialPort = connect(); 
                  _in = new BufferedReader(new InputStreamReader(_serialPort.getInputStream()));
                  while (true) {
                     String inp = _in.readLine();
                     receivePacket(inp);
                  }
               }
               catch(NoSuchPortException e)
               {
                  System.out.println("*** WARNING: serial port " + _portName + " not found");
                  e.printStackTrace(System.out);
               }
               catch(Exception e)
               {   
                   e.printStackTrace(System.out); 
                   _serialPort.close();
               }
               retry++;
          }
          System.out.println("*** Couldn't connect to GPS on'"+_portName+"' - giving up");
       }
    }



    private   String    _portName; 
    private   int       _baud;
    transient private   int       _max_retry;
    transient private   long      _retry_time; 
    transient private   int       _minDist, _turnLimit; 
    transient private   boolean   _adjustClock; 
    transient private   GpsParser _gpsParser;
    transient private   boolean   _gpx_fix = false;
    transient private   int       _course = 0, _prev_course = 0, _prev_speed = 0;
    transient private   Reference _prev_pos = null;
    transient private   Date      _prev_timestamp; 



    public GpsPosition(Properties config) 
    {
        super(config);      
        _portName = config.getProperty("ownposition.gps.port", "/dev/ttyS1").trim();
        _baud= Integer.parseInt(config.getProperty("ownposition.gps.baud", "9600").trim());
        _minDist = Integer.parseInt(config.getProperty("ownposition.tx.mindist", "150").trim());
        _turnLimit = Integer.parseInt(config.getProperty("ownposition.tx.turnlimit", "30").trim());
        _max_retry = Integer.parseInt(config.getProperty("ownposition.gps.retry", "0").trim());
        _retry_time = Long.parseLong(config.getProperty("ownposition.gps.retry.time", "30").trim()) * 60 * 1000;         
        _adjustClock = config.getProperty("ownposition.gps.on", "false").trim().matches("true|yes");
        _gpsParser = new GpsParser(); 
        _gpsParser.start();
    }

       

    private void receivePacket (String p)
    {
         if (p.charAt(0) != '$')
            return;

         /* Checksum (optional) */
         int i, checksum = 0;
         for (i=1; i < p.length() && p.charAt(i) !='*'; i++) 
            checksum ^= p.charAt(i);
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



    private int cnt = 0;

    private void do_RMC(String[] arg)
    {
       if (arg.length != 12)   /* Ignore if wrong format */
          return;

       if (!"A".equals(arg[2])) {
          _gpx_fix = false;
          return;
       }
       _gpx_fix = true;
       try {
          nmeatimeformat.setTimeZone(TimeZone.getTimeZone("UTC"));
          Date ts = nmeatimeformat.parse(arg[9]+" "+arg[1]);

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



    private void updatePosition(Date t, Reference pos, int crs, int speed)
    {  
        if (++cnt >= 5 || speed > 1 && ( course_change(crs, getCourse(), 30) && cnt >= 3)) {
           cnt = 0;
           update(t, pos, -1, crs, speed, -1, _comment, (char) 0, (char) 0, "(GPS)");
        }
    }


    private int tcnt = 300;
    private void updateTime(Date t)
    {
       if (++tcnt >= 360) { /* 6 minutes */
          tcnt = 0;
          try {
             String cmd = "sudo date " + linuxtimeformat.format(t); 
             System.out.println("*** SET TIME: "+cmd); 
             Runtime.getRuntime().exec(cmd);
          } catch (IOException e1) 
             {  e1.printStackTrace(); }

       }
    }



    /* Adapted from polaric tracker code */

    @Override protected boolean should_update() 
    {
      float dist = (_prev_pos == null ? 0 : distance(_prev_pos));
      long tdist = (_prev_timestamp == null  ?
                          getUpdated().getTime() : (getUpdated().getTime() - _prev_timestamp.getTime())) / 1000;
      float est_speed  = (tdist==0) ? 0 : ((float) dist / (float) tdist);
     /* Note that est_speed is in m/s while
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
         || ( getSpeed()>0 && _timeSinceReport >= 
                           ((_minDist * 2 / est_speed) + _minPause)) )  
       { 
           _prev_course = getCourse();
           _prev_speed = getSpeed();
           _prev_pos = getPosition();
           _prev_timestamp = getUpdated();
           return true; 
       }
       return false; 
    }


    /**
     * Setup the serial port.
     * FIXME: There is an identical function in TncChannel.java
     */
    private SerialPort connect () throws Exception
    {
        CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(_portName);
        if ( portIdentifier.isCurrentlyOwned() )
            System.out.println("*** ERROR: Port "+ _portName + " is currently in use");
        else
        {
            CommPort commPort = portIdentifier.open(this.getClass().getName(), 2000);       
            if ( commPort instanceof SerialPort )
            {
                SerialPort serialPort = (SerialPort) commPort;
                serialPort.setSerialPortParams(_baud, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
                serialPort.enableReceiveTimeout(1000);
                if (!serialPort.isReceiveTimeoutEnabled())
                   System.out.println("*** WARNING: Timeout not enabled on serial port");
                return (SerialPort) commPort;
            }
            else
                System.out.println("*** ERROR: Port " + _portName + " is not a serial port.");
        }    
        return null; 
    }
   


}

