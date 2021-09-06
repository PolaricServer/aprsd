/* 
 * Copyright (C) 2014 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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

package no.polaric.aprsd.filter;
import no.polaric.aprsd.*;


/**
 * Changes to how objects are displayed on map. 
 * Note that objects of this class represent changes to the default situation. 
 * Allow merging, i.e. the changes are added. For boolean fields this means disjunction (OR). For
 * the style string it means just to add a new class to the list. 
 *
 */
public class Action 
{
    /**
     * Null Action. No changes. 
     */
    public static Action NULL()
          { return new Action(false, false, false, false, false, false, "", null, -1, -1); }
        
        
    private boolean _hideIdent, _hideTrail, _hideAll, _hideAlias, _showPath, _public;
    private String _style = ""; 
    private long    _trailTime = -1, _trailLen = -1; 
    private String _icon = null;
    
    
    
    /**
     * Constructor for Action. 
     * 
     * @param hideid Hide ident label (default is to show it).
     * @param hidetrail Hide trail (default is to show it).
     * @param hideall Hide geo object completely (default is to show it).
     * @param hidealias Hide alias and/or special icon. 
     * @param showpath Show path of signals (default is to NOT show it).
     * @param style CSS class (used in addition to other classes). 
     * @param trailtime Timeout for trails in minutes (overrides, -1 means no change).
     * @param trailtime Trail length in minutes (overrides, -1 means no change).
     */
    public Action(boolean hideid, boolean hidetrail, boolean hideall, boolean hidealias, boolean showpath, 
                  boolean pub, String style, String icon, long trailtime, long traillen) { 
        _hideIdent = hideid; _hideTrail = hidetrail; _hideAll=hideall; _hideAlias=hidealias;
        _showPath = showpath; _style = style; _public = pub; _icon = icon;
        _trailTime = trailtime; _trailLen = traillen; 
    }
 
    
    /**
     * Merge with another action object in a disjunctive manner.     
     * @param x Action object. 
     */
    public void merge(Action x) { 
        if (x==null)
           return; 
        _hideIdent |= x._hideIdent; 
        _hideTrail |= x._hideTrail; 
        _hideAll   |= x._hideAll;
        _hideAlias |= x._hideAlias;
        _showPath  |= x._showPath; 
        _public    |= x._public;
        
        if (x._icon != null) 
           _icon = x._icon;
        
        if (!_style.contains(x._style))
           _style = _style + (_style.equals("") ? "": " ") + x._style; 
           
        /* Trail time just overrides */
        if (x._trailTime > -1)
           _trailTime = x._trailTime; 
        if (x._trailLen > -1)
           _trailLen = x._trailLen;
    }
    
    
    /** Return true if we want ident to be hidden */
    public boolean hideIdent()   
        { return _hideIdent; }  
    
    /** Return true if we want trail to be hidden */
    public boolean hideTrail()
        { return _hideTrail; }
    
    /** Return true if we want all about point to be hidden */
    public boolean hideAll()
        { return _hideAll; }
        
    /** Return true if we want to hide alias */
    public boolean hideAlias() 
        { return _hideAlias; }
    
    /** Return true if we want to show signal path */       
    public boolean showPath()
        { return _showPath; }
    
    public boolean isPublic()
        { return _public; }
        
        
    /** Get CSS style (classes) */
    public String getStyle()
        { return _style; }
    
    
    /** Get trail timeout (minutes) */
    public long getTrailTime()
        { return _trailTime; }
        
    
    /** Get trail timeout (minutes) */
    public long getTrailLen()
        { return _trailLen; }
        
        
    public String getIcon(String dfl)
        { return _icon == null ? dfl : _icon; }
    
    
    public String toString()
        { return "Action("+_hideIdent+", "+_hideTrail+", "+_hideAll+", "+_hideAlias+", "+_showPath+", "+_public+", '"+_style+"')"; }
    
}
