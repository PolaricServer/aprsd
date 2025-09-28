/* 
 * Copyright (C) 2016-2025 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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

package no.polaric.aprsd.util;
import java.util.*;
import java.util.function.*;



/**
 * Sequence of any type. I made this since I have trouble using
 * the Iterable interface and I only need the lambda-iterator. 
 * This is simpler. 
 */
 
 
 public interface Seq<T> {
    public void forEach(Consumer<T> f); 
    public boolean isEmpty();
 
 
    /**
     * Wrapper implementation for Iterable interface. 
     */
    public class Wrapper<T> implements Seq<T> {
        Iterable<T> _it; 
        Predicate<T> _pred;
        
        public Wrapper(Iterable<T> it) 
           { _it = it; }
        
        public Wrapper(Iterable<T> it, Predicate<T> pred) 
           { _it = it; _pred = pred; }
           
        public void forEach(Consumer<T> f) { 
           if (_pred == null) 
             _it.forEach(f);
           for (T x: _it)  
             if (_pred.test(x))
                f.accept(x);
        }
        
        public boolean isEmpty() 
           { return _it.iterator().hasNext(); }
    }

 }
