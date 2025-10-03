/* 
 * Copyright (C) 2012-2025 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
 
package no.polaric.aprsd.point;
import no.polaric.aprsd.*;
import no.polaric.aprsd.aprs.*;
import java.util.*;
import java.io.Serializable;
  
  
/**
 * APRS object. 
 */
public class AprsObject extends AprsPoint implements Serializable
{
    private static long _expiretime    = 1000 * 60 * 60 * 12;    // Default: 3 hour
    
    
        
    public static class JsInfo extends AprsPoint.JsInfo {
        public String owner;
        
        public JsInfo(AprsObject p) {
            super(p);
            type = "AprsObject";
            owner = p.getOwner().getIdent();
        }
    }
    
        
    public JsInfo getJsInfo() {
        return new JsInfo(this);
    }
    
    
    
    
    /*
     * Attributes of object/item record (APRS data)
     */
    private String    _ident; 
    private Station   _owner; // FIXME: use ident instead
    private boolean   _killed = false;
    private boolean   _timeless = false;
       /* If an object is timeless it also permanent, i.e. it allows other permanent objects 
        * to exist with the same name (in another area and with another owner id)
        * Permanence is a proposed APRS 1.2 feature
        */
        
    public AprsObject(Station owner, String id)
       { 
         super(null);
         _owner = owner;
         _ident = id;        
         _updated = new Date(); 
       }
        

    public synchronized void reset()
    {
       _killed = true;
       super.reset();
    }
      
    
    public String getIdent()
       { return _ident+'@'+
           (_owner!= null ? _owner.getIdent() : "UNKNOWN"); }
       
       
    public void setOwner(Station o)
       { _owner = o; }
       
       
    public Station getOwner()
       { return _owner; }
       
    
    @Override
    public Source getSource() { 
        return (_owner==null ? null : _owner.getSource()); 
    }
       
       
    /**
     * Set the object to be timeless/permanent. If true it allows other
     * permanent objects o exist with the same name...
     *
     * @param p Set to true if we want the object to be timeless/permanent.
     */
    public void setTimeless(boolean p)
       { _timeless = p; }
       
       
    /**
     * Return true if object is timeless/permanent.
     */
    public boolean isTimeless()
       { return _timeless; }
       
       
    /**
     *  Set object to updated (with new timestamp).
     */
    public synchronized void update()
    {  
        if (!_killed)
            setChanging();
        _killed = false;  
        _owner.setUpdated(new Date());
    }
    
    
    /**
     * Update position of the object. 
     *
     * @param ts Timestamp (time of update). If null, the object will be timeless/permanent.
     * @param pd Position data (position, symbol, ambiguity, etc..)
     * @param descr Comment field. 
     * @param path Digipeater path of containing packet. 
     */
    public synchronized void update(Date ts, ReportHandler.PosData pd, String descr, String path)
    { 
        /* Should try to share code with Station class ?*/
        if (_symbol != pd.symbol || _altsym != pd.symtab || 
              (_position == null && pd.pos != null))
            setChanging();

        if (_position != null && _updated != null) {
            long distance = Math.round(_position.distance(pd.pos) * 1000);  
            if (distance > 10)
                setChanging();
        }
        if (ts==null) {
            setTimeless(true);
            ts = new Date();
        }
        LatLng prevpos = (getPosition()==null ? null : getPosition());
        saveToTrail(ts, pd.pos, 0, 0, "(obj)");
        updatePosition(ts, pd.pos, pd.ambiguity);        
        setDescr(descr); 
        _symbol = pd.symbol; 
        _altsym = pd.symtab;
        _killed = false;  
        _owner.setUpdated(new Date());      
        _api.getDB().updateItem(this, prevpos);
    }
    
    
    /** 
     * Return true if object has expired. 
     */ 
    protected boolean _expired() 
    {    Date now = new Date(); 
         return (_owner != _api.getOwnPos() && // Do not expire own objects
                (_owner.expired() || now.getTime() > (_updated.getTime() + _expiretime))) ; 
    }
    
       
    /**
     * Return false if object should not be visible on maps. If it has expired
     * or if it has been killed.
     */
    @Override
    public synchronized boolean visible()
       { return !_killed && !expired(); }
       
       
    /**
     * Kill the object. Mark it for removal.
     */
    public void kill()
    {
        _killed = true;
        setChanging();
    }
}
