/* 
 * Copyright (C) 2015-2023 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
 
 
package no.polaric.aprsd.aprs;
import no.polaric.aprsd.*;
import no.polaric.aprsd.point.*;
import java.util.*;
import java.io.Serializable;



/**
 * Telemetry data for a station. 
 */
 
public class Telemetry implements Serializable
{
    public static final int MAX_DATA_LENGTH = 256;
    public static final int ANALOG_CHANNELS = 5; 
    public static final int BINARY_CHANNELS = 8;
    public static final int CHANNELS = (ANALOG_CHANNELS+BINARY_CHANNELS);
    
    
    /**
     * Data record. 
     */
    public static class Data implements Serializable {
        public int seq;
        public Date time; 
        public float[] num;
        public boolean[] bin;
        
        public Data(int s, Date t, float[] n, boolean[] b) {
            seq = s;
            time = t; 
            num = n; 
            bin = b; 
        }
    }
    
    
    /**
     * Base class for metadata. 
     */
    public static class ChannelMeta implements Serializable {
        public String parm; 
        public String unit; 
    }
    
    
    /** 
     * Metadata for binary channels. 
     */
    public static class BinChannelMeta extends ChannelMeta implements Serializable {
        public boolean bit; 
        public boolean use = false; 
    }
    
    
    /**
     * Metadata for analog channels. 
     */
    public static class NumChannelMeta extends ChannelMeta implements Serializable {
        public float[] eqns = {0, 1, 0};
    }
    
    
    public static class Meta {
        public NumChannelMeta[] num; 
        public BinChannelMeta[] bin;
    }
    
    
    private NumChannelMeta[] _chanMeta = new NumChannelMeta[5];
    private BinChannelMeta[] _binChanMeta = new BinChannelMeta[8]; 
    private Queue<Data> _data = new LinkedList<Data>();
    private Data _current; 
    private String _ident;
    private String _descr; 
    private int    _lastSeq = 0;
    
    
    
    public Telemetry(AprsServerConfig api, String id) {
        _ident = id;
        api.getWebserver().pubSub().createRoom("telemetry:"+id, (Class) null);

        for (int i=0; i < ANALOG_CHANNELS; i++) 
           _chanMeta[i] = new NumChannelMeta(); 
        for (int i=0; i < BINARY_CHANNELS; i++)
           _binChanMeta[i] = new BinChannelMeta(); 
    }
    
    
    
    public Meta getMeta() { 
        Meta m = new Meta(); 
        m.num = _chanMeta;
        m.bin = _binChanMeta;
        return m;
    }
    
    
    /* Get numeric metadata for a channel */
    public NumChannelMeta getMeta(int ch) 
      { return _chanMeta[ch]; }
    
    /* Get binary metadata for a channel */
    public BinChannelMeta getBinMeta(int ch)
      { return _binChanMeta[ch]; }
      
      
    
    /**
     * Set field names (metadata).
     */
    public void setParm(String[] p) {
        for (int i=0; i < CHANNELS && i < p.length; i++) 
            if (i < ANALOG_CHANNELS) 
                _chanMeta[i].parm = p[i];
            else {
                _binChanMeta[i-ANALOG_CHANNELS].parm = p[i];
                _binChanMeta[i-ANALOG_CHANNELS].use = true; 
            }
    }
    
    
    /**
     * Set field units (metadata).
     */
    public void setUnit(String[] p) {
        for (int i=0; i < CHANNELS && i < p.length; i++)
            if (i < ANALOG_CHANNELS)
                _chanMeta[i].unit = p[i];
            else {
                _binChanMeta[i-ANALOG_CHANNELS].unit = p[i];
                _binChanMeta[i-ANALOG_CHANNELS].use = true;
            }
    }
    
    
    /** 
     * Set eqns coeffsients (metadata).
     */
    public void setEqns(float[] p) {
        for (int i=0; i<ANALOG_CHANNELS && i*3 < p.length-3; i++) {
            _chanMeta[i].eqns[0] = p[i*3];
            _chanMeta[i].eqns[1] = p[i*3+1];
            _chanMeta[i].eqns[2] = p[i*3+2];
        }       
    }
    
    
    /**
     * Set sense bits (metadata.
     */
    public void setBits(boolean[] b) {
        for (int i=0; i < BINARY_CHANNELS; i++)
           _binChanMeta[i].bit = b[i];
    }
    
    
    /**
     * Set description.
     */
    public void setDescr(String d) 
       { _descr = d; }
     
     
    /**
     * Get description.
     */
    public String getDescr()
       { return _descr; }
       
    
    /**
     * Add received data record. 
     */
    public void addData(int seq, Date time, float[] n, boolean[] b) {
        Data d = new Data(seq, time, n, b);
        
        if (seq > _lastSeq || seq + 100 < _lastSeq) {
           _data.add(d);
           _current = d; 
           
           if (_data.size() > MAX_DATA_LENGTH)
              _data.remove();
        }
        _lastSeq = seq; 
        
        /* Notify client about change */
        var api = TrackerPoint.getApi();
        if (_ident == null)
            return;
            
        api.getWebserver().pubSub().createRoom("telemetry:"+_ident, (Class) null);
        api.getWebserver().pubSub().put("telemetry:"+_ident, null);
    }
    
    
    public Collection<Data> getHistory()
       { return _data; }
    
    
    public Collection<Data> getHistory(int hrs) {
        List<Data> res = new LinkedList<Data>();
        for (Data x : _data)
            if ( x.time.getTime() + 1000 * 60 * 60 * hrs > (new Date()).getTime() )
                res.add(x);
        return res;
    }
       
    public Data getCurrent()
       { return _current; }

       
    public boolean valid() {
        if ( _current == null) return false;
        if ( _current.time == null || 
             _current.time.getTime() + 1000 * 60 * 60 * 24 < (new Date()).getTime())
           return false; 
        return true;
    }
}

