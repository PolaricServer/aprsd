 /* 
 * Copyright (C) 2022 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
 import net.cinnom.nanocuckoo.NanoCuckooFilter;

 
 public class DuplicateChecker {
    private NanoCuckooFilter _cfilter1;
    private NanoCuckooFilter _cfilter2; 
    private int _capacity;
    
    public DuplicateChecker(int capacity) {
        _capacity = capacity;
        _cfilter1 = new NanoCuckooFilter.Builder( capacity/2+1 ).build();
        _cfilter2 = null;
    }
    
    public void add(String val) {
        if (! _cfilter1.insert(val)) {
            _cfilter2 = _cfilter1;
            _cfilter1 = new NanoCuckooFilter.Builder( _capacity/2+1 ).build();
            _cfilter1.insert(val);
        }
    }
    
    public boolean contains(String val) {
        return (_cfilter1.contains(val) && 
          (_cfilter2 != null && _cfilter2.contains(val)));
    }
    
 }
