/* 
 * Copyright (C) 2014-2025 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
 
 
package no.polaric.aprsd.filter;
import java.util.*;
import no.polaric.aprsd.*;
import no.polaric.aprsd.aprs.*;
import no.polaric.aprsd.point.*;
import java.util.regex.*;


public abstract class Pred
{
   public abstract boolean eval(TrackerPoint obj, long scale); 
      
   public static Pred FALSE()
      { return new FALSE(); }
       
   public static Pred TRUE()
      { return new TRUE(); }
      
   public static Pred Tag(String t) 
      { return new Tag(t); }
      
   public static Pred Source(String regex)
      { return new Source(regex); }
   
   public static Pred Ident(String regex)
      { return new Ident(regex); }
   
   public static Pred Path(String regex)
      { return new Path(regex); }
      
   public static Pred Scale(long sc, String op)
      { return new Scale(sc, op); }
      
   public static Pred Speed(long sp, String op)
      { return new Speed(sp, op); } 
      
   public static Pred MaxSpeed(long sp, String op)
      { return new MaxSpeed(sp, op); } 
      
   public static Pred AvgSpeed(long sp, String op)
      { return new AvgSpeed(sp, op); } 
      
   public static Pred TrailLen(long sp, String op)
      { return new TrailLen(sp, op); } 
      
   public static Pred AprsSym(String regex)
      { return new AprsSym(regex); }
   
   public static Pred Moving()
      { return new Moving(); }
   
   public static Pred Infra(boolean fulldigi, boolean igate)
      { return new Infra(fulldigi, igate); }
   
   public static Pred Infra()
      { return new Infra(); }   

   public static Pred AND(Pred...p)
      { return new AND(p); }  
   
   public static Pred OR(Pred...p)
      { return new OR(p); }
   
   public static Pred NOT(Pred p)
      { return NOT.optimize(new NOT(p)); }  
      
    public static Pred TrafficTo(Pred p)
      { return new TrafficTo(p); }
}




class FALSE extends Pred 
{
        public boolean eval(TrackerPoint p, long scale) {
            return false;
        }
}



class TRUE extends Pred 
{
        public boolean eval(TrackerPoint p, long scale) {
            return true;
        }
}



/**
 * Check for tag
 */
class Tag extends Pred
{
    private Pattern plustag, minustag;

    public Tag(String t) { 
        if (t.charAt(0)=='+')
            t = t.substring(1, t.length());
        plustag = Pattern.compile("\\+?(" + t + ")");
        minustag = Pattern.compile("\\-"+t);
    }

    public boolean eval(TrackerPoint p, long scale) { 
        return ( p._hasTag(plustag) && !p._hasTag(minustag) );
    }
}



/**
 * Regex match of data source. 
 *
 */

class Source extends Pred 
{   
    private Pattern pattern;
    
    public Source(String regex) {     
        if (regex==null)
            return;
        pattern = Pattern.compile(regex);
    }
    
    public boolean eval(TrackerPoint p, long scale) { 
        if (pattern== null || p==null || p.getSourceId() == null)
            return false;
        return pattern.matcher(p.getSourceId()).matches(); 
    }
}




/**
 * Regex match of APRS symbol. 
 * 
 * The matching is done on a string consisting of the symbol-table/overlay character first
 * and then the symbol character. E.g. "/+" is the redcross symbol. Note, if the symbol
 * characters are also regex metacharacters they must be properly escaped using a backslash.
 *
 */
class AprsSym extends Pred 
{
    private Pattern pattern;
    
    public AprsSym(String regex) { 
        pattern = Pattern.compile(regex);
    }
    
    public boolean eval(TrackerPoint p, long scale) { 
        if (p instanceof AprsPoint) {
            return pattern.matcher(""+((AprsPoint)p).getSymtab()+((AprsPoint)p).getSymbol()).matches();
        }
        else return false;
    }
}


/**
 * Evaluates to true if point is an APRS station and if it is moving. 
 */
class Moving extends Pred 
{
        public boolean eval(TrackerPoint p, long scale) {
            return ((p instanceof Station) ? !((Station) p).getTrail().isEmpty() : false);
        }
}




/**
 * Evaluates to true if point is active APRS infrastructure. 
 * Returns false of it is not an APRS station.
 *   
 * @param fulldigi Return true if point is a full digipeater.
 * @param igate Return true if point is an igate.
 *
 */
class Infra extends Pred
{
   private boolean fulldigi, igate;
   
   public Infra(boolean fulldigi, boolean igate)
     { this.fulldigi = fulldigi; this.igate = igate; }
   
   public Infra() 
     { fulldigi = igate = false; }
     
   public boolean eval(TrackerPoint p, long scale) {
        if (!(p instanceof Station))
            return false; 
        Station s = (Station) p; 
           
       boolean iwd = (fulldigi ? s.isWideDigi() : s.isInfra()); 
       boolean igt = (igate ? s.isIgate() : s.isInfra()); 
       return iwd && igt;
   }
}




/**
 * Regex match of ident field (typically callsign). 
 */
class Ident extends Pred
{   
    private Pattern pattern;
    
    public Ident(String regex) { 
        pattern = Pattern.compile(regex);
    }
    
    public boolean eval(TrackerPoint obj, long scale) {
        if (obj == null || obj.getIdent() == null)
            return false;
        return pattern.matcher(obj.getIdent()).matches(); 
    }
}



/**
 * Regex match of path field. 
 */
class Path extends Pred
{   
    private Pattern pattern;
    
    public Path(String regex) { 
        pattern = Pattern.compile(regex);
    }
    
    public boolean eval(TrackerPoint obj, long scale) { 
        if (obj instanceof Station) {
            var path = ((Station) obj).getPathInfo();
            if (path != null) {
                return pattern.matcher(path).matches();
            }
            else
                return false; 
        }
        else if (obj instanceof AprsObject) {
            var owner = ((AprsObject) obj).getOwner();       
            if (owner != null && owner.getPathInfo() != null) {       
                return pattern.matcher(owner.getPathInfo()).matches();
            }
            else
                return false; 
        }
        else
            return false; 
    }
}



abstract class NumVal extends Pred
{
    private long val; 
    private String op;
    private byte _op;
    
    private byte opVal(String op) {
        switch (op) {
          case "<": return 1; 
          case ">": return 2;
          case "<=" : return 3; 
          case ">=" : return 4; 
          default: return 0; 
        }
    }
    
    
    protected NumVal(long val, String op)
       { this.val = val; this._op = opVal(op); }
       
       
    protected boolean _eval(long val) {
       switch (_op) {
          case 1: return val < this.val; 
          case 2: return val > this.val;
          case 3 : return val <= this.val; 
          case 4 : return val >= this.val; 
          default: return false; 
       }
    } 
    
}



class Scale extends NumVal
{
    public Scale(long scale, String op) 
       { super(scale, op); }
       
    public boolean eval(TrackerPoint obj, long scale) {
        return _eval(scale); 
    }
}



class Speed extends NumVal
{
    public Speed(long sp, String op)
       { super(sp, op); } 
       
    public boolean eval(TrackerPoint obj, long scale) {
       return _eval (obj.getSpeed());
    }
}


class MaxSpeed extends NumVal
{
    public MaxSpeed(long sp, String op)
       { super(sp, op); } 
       
    public boolean eval(TrackerPoint obj, long scale) {
       return _eval (obj.getMaxSpeed());
    }
}

class AvgSpeed extends NumVal
{
    public AvgSpeed(long sp, String op)
       { super(sp, op); }  
               
    public boolean eval(TrackerPoint obj, long scale) {
       return _eval (obj.getAvgSpeed());
    }
}

class TrailLen extends NumVal
{
    public TrailLen(long sp, String op)
       { super(sp, op); }  
               
    public boolean eval(TrackerPoint obj, long scale) {
       return _eval (obj.getTrailLen());
    }
}


 

class TrafficTo extends Pred 
{
    private Pred to;
    
    public TrafficTo(Pred p) {
        to = p; 
    }
    
    public boolean eval(TrackerPoint obj, long scale) {
        if (obj != null && obj instanceof Station st)  {
            Set<String> toset = st.getTrafficTo();
            if (toset==null)
                return false;
            for (String id : toset) {
                var item = TrackerPoint.getApi().getDB().getItem(id, null);
                if (to.eval(item, scale))
                    return true;
            }
        }
        return false; 
    }
}




/**
 * Conjunction of predicates. 
 *
 */
class AND extends Pred
{
    private List<Pred> conj = new ArrayList<Pred>(); 
    
    
    public AND() {};
    
    
    public AND(Pred... args) {
        for (Pred p: args)
            if (p != null) add(p);
        optimize(); 
    }
    
    
    public void add(Pred r)
       { conj.add(r); }

    
    public boolean eval(TrackerPoint obj, long scale) {
       for (Pred r : conj) 
          if (!r.eval(obj, scale)) 
             return false;
       return true; 
    }
    
    void optimize() {
        List<Pred> copy = new ArrayList<Pred>();
        for (Pred p : conj) {
           if (p instanceof AND pp) {
              for (Pred q : pp.conj)
                 copy.add(q); 
           }
           else
              copy.add(p); 
        }    
        conj = copy; 
    }
    
}




/**
 * Disjunction of predicates. 
 *
 */
class OR extends Pred
{
   private List<Pred> disj = new ArrayList<Pred>(); 
   
   
   public OR() {};
   
   
   public OR(Pred... args) {
        for (Pred p: args)
            if (p != null) add(p);
        optimize(); 
   }
   
   
   void add(Pred r)
      { disj.add(r); }

   
   public boolean eval(TrackerPoint obj, long scale) {
      for (Pred r : disj) 
         if (r.eval(obj, scale)) 
            return true;
      return false; 
   }
   
   
   /* 
    * IMPORTANT: Be sure that optimization does not change other nodes
    * than this, since they may be referenced from elsewhere. 
    */
   void optimize() {
        List<Pred> copy = new ArrayList<Pred>();
        for (Pred p : disj) {
           if (p instanceof OR pp) {
              for (Pred q : pp.disj)
                 copy.add(q); 
           }
           else
              copy.add(p); 
       }    
       disj = copy; 
    }
}


/**
 * Negation of predicates. 
 *
 */
class NOT extends Pred
{
        private Pred pred; 
        
        public NOT(Pred p)
            { pred = p; }
        
        public boolean eval (TrackerPoint obj, long scale)
            { return !pred.eval(obj, scale); }
            
        static Pred optimize(NOT p) {
           if (p.pred instanceof NOT pp) {
               return pp.pred; 
           }
           else
               return p;
        }
}

