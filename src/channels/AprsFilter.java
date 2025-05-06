 
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
 *  a - area
 *  r - range 
 *  m - my range
 *  f - friend range
 *  t - type
 *  p - prefix 
 *  d - digipeater (with wildcards)
 *  b - budlist (with wildcards) 
 *  u - unproto (with wildcards) 
 *  e - entry (with wildcards)
 *  C - input channel (internal only)
 *
 * Todo: o-object, os-strict object, s-symbol, g-group, q-qconstruct, 
 *
 * Filters separated by just a space is a disjunction. For example 'a b' means 'a OR b'.
 * If the filter starts with '-' it is an exception. If any such filters is true, it means the whole filter is false. 
 * If the filter starts with '&' it is a conjunction with the filter it immediately follows. 
 *    For example 'a &b &c' means '(a AND b AND c)'. 'a &b c' means '(a AND b) OR c'.  
 * Conjunctions can also be used with exceptions. 
 *    For example -t/x &p/a means NOT(t/x AND p/a) 
 */

 
public abstract class AprsFilter {

    protected static ServerAPI _api; 
    
    
    public static class All extends AprsFilter {
        @Override public boolean test(AprsPacket p) {
            return true;
        }
        public String toString() {return "All";}
    }
    
        
    
    /**
     * Area filter.  a/latN/lonW/latS/lonE 
     */
    public static class Area extends AprsFilter {
        protected LatLng uleft, lright;
        
        public Area(String[] parms) {
            double latN = Double.parseDouble(parms[1]);
            double lonW = Double.parseDouble(parms[2]);
            double latS = Double.parseDouble(parms[3]);
            double lonE = Double.parseDouble(parms[4]);
            
            uleft = new LatLng(latN, lonW); 
            lright = new LatLng(latS, lonE);
        }
        
        @Override public boolean test(AprsPacket p) {
            Point pktpos = AprsUtil.getPos(p); 
            if (uleft == null || lright == null || pktpos == null)
                return false;
            return pktpos.isInside(uleft, lright);
        }
    }
    
    
    
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
            
                /* Telemetry. Drop if item position is not known */
                if (t=='m' && p.from.equals(p.msgto)) {
                    Point x = _api.getDB().getItem(p.from, null);
                    if (x==null || x.getPosition() == null) 
                        return false;
                 }
                        
                /* Range check */
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
            for (int i=0; i<_prefixes.length; i++)
                _prefixes[i] = _prefixes[i].toUpperCase();
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
                patterns[i] = patterns[i].toUpperCase().replaceAll("\\*", "(.*)").replaceAll("\\?", ".");
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
     * u - unproto with wildcards
     */
    public static class Unproto extends AprsFilter {
        private String[] patterns;
        
        public Unproto(String[] parms) {
            patterns = parms; 
            /* Convert to regex */
            for (int i = 1; i< patterns.length; i++)
                patterns[i] = patterns[i].toUpperCase().replaceAll("\\*", "(.*)").replaceAll("\\?", ".");
        }
        
        @Override public boolean test(AprsPacket p) {
            for (String x : patterns)
                if (p.to.matches(x))
                    return true;
            return false;
        }
        
        public String toString() {return "Unproto";}
    }
    
    
    
    
    /**
     * d - digipeater with wildcards
     */
    public static class Digi extends AprsFilter {
        private String[] patterns;
        
        public Digi(String[] parms) {
            patterns = parms; 
            /* Convert to regex */
            for (int i = 1; i< patterns.length; i++)
                patterns[i] = patterns[i].toUpperCase().replaceAll("\\*", "(.*)").replaceAll("\\?", ".")+"(\\*)?";
        }
         
        @Override public boolean test(AprsPacket p) {
            if (p.via == null || p.via == null)
                return false; 
            String[] digis = p.via.split(",");
            int i;
            for (i=digis.length; i>0; i--)
                if (digis[i-1].matches(".+\\*"))
                    break;

            for (int j=0; j<i; j++)
                for (String x : patterns)
                    if (digis[i].matches(x))
                        return true;
            return false;
        }
        
        public String toString() {return "Digi";}
    }
    
    
    
        
    /**
     * e - entry calls with wildcards
     */
    public static class Entry extends AprsFilter {
        private String[] patterns;
        
        public Entry(String[] parms) {
            patterns = parms; 
            /* Convert to regex */
            for (int i = 1; i< patterns.length; i++)
                patterns[i] = patterns[i].toUpperCase().replaceAll("\\*", "(.*)").replaceAll("\\?", ".");
        }
        
        @Override public boolean test(AprsPacket p) {
            String[] qc = p.getQcode();
            if (qc==null || qc[1] == null)
                return false; 
            for (String x : patterns)
                if (qc[1].matches(x))
                    return true;
            return false;
        }
        
        public String toString() {return "Entry";}
    }
    
    
    
    /**
     * C - Input channel
     */
     public static class Chan extends AprsFilter {
        private String[] chan;
    
        public Chan(String c[]) { 
            chan = new String[c.length-1];
            for (int i = 1; i< c.length; i++)
                chan[i-1] = c[i];
        }
    
        @Override public boolean test(AprsPacket p) {
            if (p.source==null)
                return true;
            for (int i=0; i<chan.length; i++) {
                if (chan[i].equals(p.source.getIdent())) 
                    return true;
            }
                
            return false;
        }
        
        public String toString() {return "Channel";}
    }
    
    
    
    /**
     * Combined filter. List of filters. 
     */
    public static class Combined extends AprsFilter {
        List<AprsFilter[]> _flist, _xlist;
        String _client; // Callsign of the logged in client
        
        public Combined(String cl) {
            _client = cl; 
            _flist = new LinkedList<AprsFilter[]>();
            _xlist = new LinkedList<AprsFilter[]>();
            parse(cl);
        }

        
        private void parse(String fspec) {
            if (fspec.length() == 0)
                return;
            int findex = 0;
            String[] fspecs = fspec.split(" ");
            AprsFilter[] f = new AprsFilter[10]; 
             
            for (String fstr : fspecs) {
                if (fstr.length() == 0)
                    continue;
                String[] ff = fstr.split("/");
                String cmd = ff[0];
                boolean exception = false;
                
                if (cmd.charAt(0) == '-')  {
                    exception = true;
                    cmd = cmd.substring(1);
                }
                if (cmd.charAt(0) == '&') {
                    cmd = cmd.substring(1);
                    findex++;
                }
                else {
                    f = new AprsFilter[10];        
                    findex = 0;
                }
                
                f[findex] = switch (cmd) {
                    case "*" -> new All();
                    case "a" -> new Area(ff);
                    case "p" -> new Prefix(ff);
                    case "b" -> new Budlist(ff);
                    case "u" -> new Unproto(ff);
                    case "d" -> new Digi(ff);
                    case "t" -> new Type(ff);
                    case "r" -> new ERange(ff);
                    case "m" -> new ItemRange(_client, ff);
                    case "f" -> new ItemRange(ff[1], ff, 2);
                    case "e" -> new Entry(ff);
                    case "C" -> new Chan(ff);
                    default -> null;
                }; 
                if (findex > 0)
                    continue;
                if (exception)
                    _xlist.add(f);
                else
                    _flist.add(f);
            }
        }
        
        
        /* 
         * Go through rules and test.
         * Negative results of exception rules will override any other rule regardless of order 
         */
        public boolean test(AprsPacket p) {
            boolean res = false; 
            for (AprsFilter[] f: _flist)
                if (ctest(f, p)) { res = true; break; }
                
            /* Exceptions */
            for (AprsFilter[] f: _xlist)
                if (ctest(f, p)) return false; 
            return res; 
        }
        
        
        /* 
         * Conjunction. Return true only if all parts are true 
         */
        private boolean ctest(AprsFilter[] conj, AprsPacket p) {
            for (AprsFilter f : conj) {
                if (f==null)
                    break;
                if (!f.test(p)) return false;
            }
            return true;
        }
        
        
        
        public String toString() {
            String res = "[ ";
            res += _toString(_flist);
            if (_xlist.size() > 0)
                res += "EXCEPT " + _toString(_xlist);
            return res + "]";
        }
        
        
        private String _toString(List<AprsFilter[]> list) {
            String res = "";
            for (AprsFilter[] x: list) {
                if (x[1] != null) {
                    res+= "(";
                    for (int i=0; i<x.length-1; i++) {
                        if (x[i] == null)
                            break;
                        res+=x[i] + (x[i+1]==null ? "" : " & ");
                    }
                    res+= ")";
                }
                else
                    res += x[0];
                res += " ";
            }
            return res;
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


