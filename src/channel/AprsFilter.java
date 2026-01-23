/* 
 * Copyright (C) 2025 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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


package no.polaric.aprsd.channel;
import no.polaric.aprsd.*;
import no.polaric.aprsd.aprs.*;
import no.polaric.aprsd.point.*;
import java.util.*;


/**
 * APRS-IS filters.
 * See https://www.aprs-is.net/javAPRSFilter.aspx. 
 *
 * These filters are supported. C and * are specific for Polaric Aprsd. 
 *  a - area
 *  r - range 
 *  m - my range
 *  f - friend range
 *  t - type
 *  p - prefix 
 *  P - predefined filter (see StoredFilter.java)
 *  d - digipeater (with wildcards)
 *  b - budlist (with wildcards) 
 *  u - unproto (with wildcards) 
 *  e - entry (with wildcards)
 *  o - object/item (with wildcards)
 *  os - strict object (with wildcards)
 *  s - symbol
 *  g - group message (with wildcards)
 *  q - Q construct (with wildcards)
 *  C - input channel (internal only)
 *  * - matches all   (internal only)
 *
 *
 * Filters separated by just a space is a disjunction. For example 'a b' means 'a OR b'.
 * If the filter starts with '-' it is an exception. If any such filters is true, it means the whole filter is false. 
 * If the filter starts with '&amp;' it is a conjunction with the filter it immediately follows.
 *    For example 'a &amp;b &amp;c' means '(a AND b AND c)'. 'a &amp;b c' means '(a AND b) OR c'.
 * Conjunctions can also be used with exceptions. 
 *    For example -t/x &amp;p/a means NOT(t/x AND p/a)
 */

 
public abstract class AprsFilter {

    protected static AprsServerConfig _conf; 
    protected static StoredFilter _predefined;
    
    
    /**
     * Convert wildcard pattern (* and ?) to regex pattern.
     * Used during filter initialization only, not during packet processing.
     */
    protected static String wildcardToRegex(String wildcard) {
        return wildcard.replaceAll("\\*", "(.*)").replaceAll("\\?", ".");
    }
    
    
    public static class All extends AprsFilter {
        @Override public boolean test(AprsPacket p) {
            return true;
        }
        public String toString() {return "All";}
    }
    
    
    
    public static class Nothing extends AprsFilter {
        @Override public boolean test(AprsPacket p) {
            return false;
        }
        public String toString() {return "Nothing";}
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
            return (!pos.isNull() && pos.distance(pktpos) <= dist*1000);
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
        protected String item;
        
        public ItemRange(String it, String[] parms, int index) {
            item = it;
            dist = Integer.parseInt(parms[index]);
        }
        
        public ItemRange(String it, String[] parms) {
            this(it, parms, 1);
        }
        
        public String toString() {return "ItemRange";}
        
        @Override public boolean test(AprsPacket p) {
            pos = _conf.getDB().getItem(item, null);
            return super.test(p);
        }
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
            boolean telemetry = (t=='m' && p.from.equals(p.msgto));

            if (types.indexOf(t) != -1 || (telemetry && types.indexOf('M') != -1)) {
            
                /* Telemetry. Drop if item position is not known */
                if (telemetry) {
                    Point x = _conf.getDB().getItem(p.from, null);
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
        private java.util.regex.Pattern[] patterns;
        
        public Budlist(String[] parms) {
            patterns = new java.util.regex.Pattern[parms.length - 1]; 
            /* Convert to regex and pre-compile patterns */
            for (int i = 1; i< parms.length; i++)
                patterns[i-1] = java.util.regex.Pattern.compile(
                    wildcardToRegex(parms[i].toUpperCase()));
        }
        
        @Override public boolean test(AprsPacket p) {
            for (java.util.regex.Pattern x : patterns)
                if (x.matcher(p.from).matches())
                    return true;
            return false;
        }
        
        public String toString() {return "Budlist";}
    }
    
    
    
        
    /**
     * u - unproto with wildcards
     */
    public static class Unproto extends AprsFilter {
        private java.util.regex.Pattern[] patterns;
        
        public Unproto(String[] parms) {
            patterns = new java.util.regex.Pattern[parms.length - 1]; 
            /* Convert to regex and pre-compile patterns */
            for (int i = 1; i< parms.length; i++)
                patterns[i-1] = java.util.regex.Pattern.compile(
                    wildcardToRegex(parms[i].toUpperCase()));
        }
        
        @Override public boolean test(AprsPacket p) {
            for (java.util.regex.Pattern x : patterns)
                if (x.matcher(p.to).matches())
                    return true;
            return false;
        }
        
        public String toString() {return "Unproto";}
    }
    
    
    
    
    /**
     * d - digipeater with wildcards
     */
    public static class Digi extends AprsFilter {
        private java.util.regex.Pattern[] patterns;
        private static final java.util.regex.Pattern DIGI_USED = java.util.regex.Pattern.compile(".+\\*");
        
        public Digi(String[] parms) {
            patterns = new java.util.regex.Pattern[parms.length - 1]; 
            /* Convert to regex and pre-compile patterns */
            for (int i = 1; i< parms.length; i++)
                patterns[i-1] = java.util.regex.Pattern.compile(
                    wildcardToRegex(parms[i].toUpperCase()) + "(\\*)?");
        }
         
        @Override public boolean test(AprsPacket p) {
            if (p.via == null)
                return false; 
            String[] digis = p.via.split(",");
            int i;
            for (i=digis.length; i>0; i--)
                if (DIGI_USED.matcher(digis[i-1]).matches())
                    break;

            for (int j=0; j<i; j++)
                for (java.util.regex.Pattern x : patterns)
                    if (x.matcher(digis[j]).matches())
                        return true;
            return false;
        }
        
        public String toString() {return "Digi";}
    }
    
    
    
        
    /**
     * e - entry calls with wildcards
     */
    public static class Entry extends AprsFilter {
        private java.util.regex.Pattern[] patterns;
        
        public Entry(String[] parms) {
            patterns = new java.util.regex.Pattern[parms.length - 1]; 
            /* Convert to regex and pre-compile patterns */
            for (int i = 1; i< parms.length; i++)
                patterns[i-1] = java.util.regex.Pattern.compile(
                    wildcardToRegex(parms[i].toUpperCase()));
        }
        
        @Override public boolean test(AprsPacket p) {
            String[] qc = p.getQcode();
            if (qc==null || qc[1] == null)
                return false; 
            for (java.util.regex.Pattern x : patterns)
                if (x.matcher(qc[1]).matches())
                    return true;
            return false;
        }
        
        public String toString() {return "Entry";}
    }
    
    
    
    /**
     * o - object with wildcards
     */
    public static class Object extends AprsFilter {
        private java.util.regex.Pattern[] patterns;
        
        public Object(String[] parms) {
            patterns = new java.util.regex.Pattern[parms.length - 1]; 
            /* Convert to regex and pre-compile patterns */
            for (int i = 1; i< parms.length; i++)
                patterns[i-1] = java.util.regex.Pattern.compile(
                    wildcardToRegex(parms[i].toUpperCase()));
        }
        
        @Override public boolean test(AprsPacket p) {
            if (p.type != ';' && p.type != ')')
                return false;
            if (p.report == null || p.report.length() < 3)
                return false;
            
            String objname;
            if (p.type == ';') {
                /* Object report: fixed 9-character name at position 1-9 */
                if (p.report.length() < 10)
                    return false;
                objname = p.report.substring(1, 10).trim();
            } else {
                /* Item report: variable length name, terminated by ! or _ */
                String msg = p.report.substring(1);
                int i = msg.indexOf('!');
                if (i == -1 || i > 10)
                    i = msg.indexOf('_');
                if (i == -1)
                    return false;
                objname = msg.substring(0, i).trim();
            }
            
            for (java.util.regex.Pattern pattern : patterns)
                if (pattern.matcher(objname).matches())
                    return true;
            return false;
        }
        
        public String toString() {return "Object";}
    }
    
    
    
    /**
     * os - strict object (not items) with wildcards
     */
    public static class StrictObject extends AprsFilter {
        private java.util.regex.Pattern[] patterns;
        
        public StrictObject(String[] parms) {
            patterns = new java.util.regex.Pattern[parms.length - 1]; 
            /* Convert to regex and pre-compile patterns */
            for (int i = 1; i< parms.length; i++)
                patterns[i-1] = java.util.regex.Pattern.compile(
                    wildcardToRegex(parms[i].toUpperCase()));
        }
        
        @Override public boolean test(AprsPacket p) {
            if (p.type != ';')
                return false;
            if (p.report == null || p.report.length() < 10)
                return false;
            String objname = p.report.substring(1, 10).trim();
            for (java.util.regex.Pattern pattern : patterns)
                if (pattern.matcher(objname).matches())
                    return true;
            return false;
        }
        
        public String toString() {return "StrictObject";}
    }
    
    
    
    /**
     * s - symbol filter - pri/alt/overlay
     */
    public static class Symbol extends AprsFilter {
        private String primary = null;
        private String alternate = null; 
        private String overlay = null;
        
        public Symbol(String[] parms) {
            if (parms.length >= 2 && parms[1] != null && !parms[1].isEmpty())
                primary = parms[1];
            if (parms.length >= 3 && parms[2] != null && !parms[2].isEmpty())
                alternate = parms[2];
            if (parms.length >= 4 && parms[3] != null && !parms[3].isEmpty())
                overlay = parms[3];
        }
        
        @Override public boolean test(AprsPacket p) {
            ReportHandler.PosData pd = AprsUtil.getPosData(p);
            if (pd == null)
                return false;
                
            if (primary != null && primary.indexOf(pd.symbol) == -1)
                return false;
            if (alternate != null && alternate.indexOf(pd.symtab) == -1)
                return false;
            if (overlay != null) {
                /* Overlay is when symtab is numerical (0-9) or A-Z */
                if (overlay.indexOf(pd.symtab) == -1)
                    return false;
            }
            return true;
        }
        
        public String toString() {return "Symbol";}
    }
    
    
    
    /**
     * g - group message filter with wildcards
     */
    public static class GroupMsg extends AprsFilter {
        private java.util.regex.Pattern[] patterns;
        
        public GroupMsg(String[] parms) {
            patterns = new java.util.regex.Pattern[parms.length - 1]; 
            /* Convert to regex and pre-compile patterns */
            for (int i = 1; i< parms.length; i++)
                patterns[i-1] = java.util.regex.Pattern.compile(
                    wildcardToRegex(parms[i].toUpperCase()));
        }
        
        @Override public boolean test(AprsPacket p) {
            if (p==null)
                return false;
            if (p.type != ':')
                return false;
            if (p.msgto == null)
                return false;
            for (java.util.regex.Pattern pattern : patterns)
                if (pattern.matcher(p.msgto).matches())
                    return true;
            return false;
        }
        
        public String toString() {return "GroupMsg";}
    }
    
    
    
    /**
     * q - Q construct filter with wildcards
     */
    public static class QConstruct extends AprsFilter {
        private java.util.regex.Pattern[] patterns;
        
        public QConstruct(String[] parms) {
            patterns = new java.util.regex.Pattern[parms.length - 1]; 
            /* Convert to regex and pre-compile patterns */
            for (int i = 1; i< parms.length; i++)
                patterns[i-1] = java.util.regex.Pattern.compile(
                    wildcardToRegex(parms[i].toUpperCase()));
        }
        
        @Override public boolean test(AprsPacket p) {
            String[] qc = p.getQcode();
            if (qc == null || qc[0] == null)
                return false;
            for (java.util.regex.Pattern pattern : patterns)
                if (pattern.matcher(qc[0]).matches())
                    return true;
            return false;
        }
        
        public String toString() {return "QConstruct";}
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
        
        public Combined(String cl, String userid) {
            _client = userid; 
            _flist = new ArrayList<AprsFilter[]>();
            _xlist = new ArrayList<AprsFilter[]>();
            parse(cl);
        }

        
        private void parse(String fspec) {
            if (fspec.length() == 0)
                return;
            int findex = 0;
            String[] fspecs = fspec.split(" ");
            AprsFilter[] f = new AprsFilter[10]; 
             
            /* For each part of the filter spec */
            for (String fstr : fspecs) {
                if (fstr.length() == 0)
                    continue;
                String[] ff = fstr.split("/");
                String cmd = ff[0];
                boolean exception = false;
                
                char firstChar = cmd.charAt(0);
                if (firstChar == '-')  { // Negation (exception)
                    exception = true;
                    cmd = cmd.substring(1);
                    firstChar = cmd.charAt(0);
                }
                if (firstChar == '&') {  // Part of conjunction
                    cmd = cmd.substring(1);
                    findex++;
                }
                else {                  // Next part of disjunction - create new conjuncion
                    f = new AprsFilter[10];        
                    findex = 0;
                }
                
                f[findex] = switch (cmd) {
                    case "*" -> new All();
                    case "a" -> new Area(ff);
                    case "p" -> new Prefix(ff);
                    case "P" -> _predefined.get(ff[1]);
                    case "b" -> new Budlist(ff);
                    case "u" -> new Unproto(ff);
                    case "d" -> new Digi(ff);
                    case "t" -> new Type(ff);
                    case "r" -> new ERange(ff);
                    case "m" -> new ItemRange(_client, ff);
                    case "f" -> new ItemRange(ff[1], ff, 2);
                    case "e" -> new Entry(ff);
                    case "o" -> new Object(ff);
                    case "os" -> new StrictObject(ff);
                    case "s" -> new Symbol(ff);
                    case "g" -> new GroupMsg(ff);
                    case "q" -> new QConstruct(ff);
                    case "C" -> new Chan(ff);
                    default -> null;
                }; 
                if (f[findex] == null) {
                    _conf.log().warn("AprsFilter", "Invalid filter: "+fstr);
                    f[findex] = new Nothing();
                }
                
                if (findex > 0) // filter is part of a conjunction
                    continue;
                    
                if (exception)
                    _xlist.add(f);
                else
                    _flist.add(f);
            }
        }
        
        
        /** 
         * Go through rules and test.
         * Negative results of exception rules will override any other rule regardless of order
         * Expressions are on a disjunctive normal form, so each part can be seen as a conjunction
         */
        public boolean test(AprsPacket p) {
            /* Check exceptions first - if any match, result is false */
            for (AprsFilter[] f: _xlist)
                if (ctest(f, p)) return false;
                
            /* Check positive filters - if any match, result is true */
            for (AprsFilter[] f: _flist)
                if (ctest(f, p)) return true;
                
            return false; 
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
    
    
    public static void init(AprsServerConfig conf) {
        _conf = conf;
        String fname = conf.getProperty("aprsfilters.file", "/etc/polaric-aprsd/aprsfilters");
        _predefined = new StoredFilter(conf);
        _predefined.init(fname);
    }
    
    
    public static AprsFilter createFilter(String fspec, String userid) {
        return new Combined(fspec, userid);
    }
    

    
}


