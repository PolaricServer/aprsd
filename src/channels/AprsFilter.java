 
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
import java.util.*;


/*
 * APRS-IS filters
 * See https://www.aprs-is.net/javAPRSFilter.aspx. 
 *
 * A subset of these filters are supported (more can be added if needed):
 *  r - range
 *  m - my range
 *  f - friend range
 *  t - type
 *  p - prefix
 *  b - budlist (with wildcards)
 */

public abstract class AprsFilter {

    protected static ServerAPI _api; 
    
    /**
     * Range filter.
     */
    public abstract static class Range extends AprsFilter {
        protected Point pos; 
        protected int dist; // in km
        
        @Override public boolean test(AprsPacket p) {
            Point pktpos = AprsUtil.getPos(p); 
            if (pos==null || pktpos==null)
                return false;
            return (pos.distance(pktpos) <= dist*1000);
        }
    }
    
    
    /**
     * r - range - lat/lon/dist.
     */
    public static class ERange extends Range {
        public ERange(String[] parms) {
            pos = new Point(
               new LatLng(Double.parseDouble(parms[1]), Double.parseDouble(parms[2])));
            dist = Integer.parseInt(parms[3]);
        }
        public String toString() {return "ERange";}
    }
    
    
    /**
     * m - my range - dist.
     */
    public static class ItemRange extends Range {
        public ItemRange(String item, String[] parms, int index) {
            pos = _api.getDB().getItem(item, null);
            dist = Integer.parseInt(parms[index]);
        }
        public ItemRange(String item, String[] parms) {
            this(item, parms, 1);
        }
        public String toString() {return "ItemRange";}
    }
    
    
    
    /**
     * t - type - poimqstunw/call/km.
     */
    public static class Type extends AprsFilter {
        protected String types;
        protected ItemRange range;
         
        public Type(String[] parms) {
            types = parms[1];
            if (parms.length >= 3 && parms[2] != null)
                range = new ItemRange(parms[2], parms, 3);
        }
        
        @Override public boolean test(AprsPacket p) {
        
            char t = toFType(p);
            if (types.indexOf(t) != -1) {
                if (range != null)
                    return range.test(p);
                return true;
            }
            return false; 
        }
        public String toString() {return "Type";}
    }
    
    
    
    /**
     * p - prefix
     */
    public static class Prefix extends AprsFilter {
        private String[] _prefixes;
                
        public Prefix(String[] parms) {
            _prefixes = Arrays.copyOfRange(parms, 1, parms.length);
        }
        
        @Override public boolean test(AprsPacket p) {
            for (String pre : _prefixes)
                if (p.from.startsWith(pre))
                    return true;
            return false;
        }
        
        public String toString() {return "Prefix";}
    }

    
    
    
    /**
     * b - budlist with wildcards
     */
    public static class Budlist extends AprsFilter {
        private String[] patterns;
        
        public Budlist(String[] parms) {
            patterns = parms; 
            /* Convert to regex */
            for (int i = 1; i< patterns.length; i++)
                patterns[i] = patterns[i].replaceAll("\\*", "(.*)").replaceAll("\\?", ".");
        }
        
        @Override public boolean test(AprsPacket p) {
            for (String x : patterns)
                if (p.from.matches(x))
                    return true;
            return false;
        }
        
        public String toString() {return "Budlist";}
    }
    
    
    
    
    /**
     * Combined filter. List of filters. 
     */
    public static class Combined extends AprsFilter {
        List<AprsFilter> _flist; 
        String _client; // Callsign of the logged in client
        
        public Combined(String cl) {
            _client = cl; 
            _flist = new LinkedList<AprsFilter>();
            parse(cl);
        }
        
        private void add(AprsFilter f) {
            if (f != null) 
                _flist.add(f);
        }
        
        private void parse(String fspec) {
            String[] fspecs = fspec.split(" ");
            for (String fstr : fspecs) {
                String[] ff = fstr.split("/");
                AprsFilter f = switch (ff[0]) {
                    case "p" -> new Prefix(ff);
                    case "b" -> new Budlist(ff);
                    case "t" -> new Type(ff);
                    case "r" -> new ERange(ff);
                    case "m" -> new ItemRange(_client, ff);
                    case "f" -> new ItemRange(ff[1], ff, 2);
                    default -> null;
                }; 
                add(f);
            }
        }
        
        public boolean test(AprsPacket p) {
            for (AprsFilter f: _flist)
                if (f.test(p)) return true;
            return false;
        }
        
        public String toString() {
            String res = "[ ";
            for (AprsFilter x: _flist)
                res += x + " ";
            return res+"]";
        }
    }

    
    public boolean test(AprsPacket p) {
        return false;
    }
    
    
    public static char toFType(AprsPacket p) {
        return switch (p.type) {
            case '!', '=','@', '/', '\'', '`'  -> 'p';
            case ';' -> 'o';
            case ')' -> 'i';
            case ':' -> 'm';
            case '?' -> 'q'; 
            case '>' -> 's';
            case 'T' -> 't';
            case '_', '#', '*' -> 'w';
            case '{' -> 'u';
            default -> 'X';
        };
    }
    
    
    public static void init(ServerAPI api) {
        _api = api;
    }
    
    
    public static AprsFilter createFilter(String fspec) {
        return new Combined(fspec);
    }
    
}


