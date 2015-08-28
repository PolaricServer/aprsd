 
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
import java.util.*;
import no.polaric.aprsd.*;



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
   private String tag;

   public Tag(String t)
      { this.tag=t; }

   public boolean eval(TrackerPoint p, long scale)
      { return p.hasTag(tag);}
}



/**
 * Regex match of data source. 
 *
 */

class Source extends Pred 
{   
    private String regex;  
    
    public Source(String regex) 
       { this.regex = regex; }
    
    public boolean eval(TrackerPoint p, long scale) 
       { return p.getSource().getIdent().matches(regex); }
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
    private String regex;  
    
    public AprsSym(String regex) 
       { this.regex = regex; }
    
        public boolean eval(TrackerPoint p, long scale) { 
           if (p instanceof AprsPoint) 
              return (""+((AprsPoint)p).getSymtab()+((AprsPoint)p).getSymbol()).matches(regex); 
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
    private String regex;  
    
    public Ident(String regex) 
       { this.regex = regex; }
    
    public boolean eval(TrackerPoint obj, long scale) 
       { return obj.getIdent().matches(regex); }
}



/**
 * Regex match of path field. 
 */
class Path extends Pred
{   
    private String regex;  
    
    public Path(String regex) 
       { this.regex = regex; }
    
    public boolean eval(TrackerPoint obj, long scale) 
       { if (obj instanceof Station) 
           return ((Station) obj).getPathInfo().matches(regex); 
         else if (obj instanceof AprsObject && 
              ((AprsObject) obj).getOwner() != null && ((AprsObject) obj).getOwner().getPathInfo() != null)
           return ((AprsObject) obj).getOwner().getPathInfo().matches(regex); 
         else
           return false; 
       }
}




class Scale extends Pred 
{
    private long scale; 
    private String op;
    
    public Scale(long scale, String op) 
       { this.scale = scale; this.op = op;}
       
    public boolean eval(TrackerPoint obj, long scale) {
       switch (op) {
          case "<": return scale < this.scale; 
          case ">": return scale > this.scale;
          case "<=" : return scale <= this.scale; 
          case ">=" : return scale >= this.scale; 
          default: return false; 
       }
    } 
}




/**
 * Conjunction of predicates. 
 *
 */
class AND extends Pred
{
    private List<Pred> conj = new LinkedList<Pred>(); 
    
    
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
        List<Pred> copy = new LinkedList<Pred>();
        for (Pred p : conj) {
           if (p instanceof AND) {
              for (Pred q : ((AND)p).conj)
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
   private List<Pred> disj = new LinkedList<Pred>(); 
   
   
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
        List<Pred> copy = new LinkedList<Pred>();
        for (Pred p : disj) {
           if (p instanceof OR) {
              for (Pred q : ((OR)p).disj)
                 copy.add(q); 
           }
           else
              copy.add(p); 
       }    
       disj = copy; 
    }
}



class NOT extends Pred
{
        private Pred pred; 
        
        public NOT(Pred p)
            { pred = p; }
        
        public boolean eval (TrackerPoint obj, long scale)
            { return !pred.eval(obj, scale); }
            
        static Pred optimize(NOT p) {
           if (p.pred instanceof NOT) {
               return ((NOT)p.pred).pred; 
           }
           else
               return p;
        }
}

