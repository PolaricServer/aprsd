/* 
 * Copyright (C) 2009-2022 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import java.io.*;
import java.util.*;
import java.security.*;
import com.mindprod.base64.Base64;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.NoSuchAlgorithmException;
import java.security.spec.*;



/**
 * Some utilities related to security.
 */
 
public class SecUtils
{

   private static SecureRandom _rand = null;

    static {
       try {
          _rand = SecureRandom.getInstance("SHA1PRNG");
       }
       catch (Exception e) {
          System.out.println("*** SecUtils: Couldnt create random generator");
       }
    }

 
    /**
     * Generate a random key. 
     */   
    public final static byte[] getRandom(int size)
    {
        if (size < 1)
            return null; // OOPS: Is this secure? 
        
        byte[] k = new byte[size];
        _rand.nextBytes(k);
        return k;
    }
    

    /**
     * Compute a hash from the text. Text can be given as
     * an array of bytes, a string or both. A string will be converted
     * to bytes using the UTF-8 encoding before computing the hash.
     */   
    public final static byte[] _digest ( byte[] bytes, String txt, String algo )
    {
        try{
            MessageDigest dig = MessageDigest.getInstance(algo);
            if (bytes != null) 
                dig.update(bytes);
            if (txt != null)
                dig.update(txt.getBytes("UTF-8"));
            return dig.digest();
        }
        catch (Exception e) {
            System.out.println("*** SecUtils: Cannot generate message digest: "+e);
            return null;
        }
    }

    
    /* Computes MD5 hash */
    public final static byte[] digest( byte[] bytes, String txt )
        { return _digest(bytes, txt, "MD5"); }
    
    /* Computes SHA-256 hash */
    public final static byte[] xDigest( byte[] bytes, String txt )
        { return _digest(bytes, txt, "SHA-256"); }
        
      

      
      
    public final static byte[] hmac(String data, String key)
    {
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes("UTF-8"), "HMAC_SHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKeySpec);
            return mac.doFinal(data.getBytes("UTF-8"));
        }
        catch (Exception e) {
            System.out.println("*** SecUtils: Cannot generate HMAC: "+e);
            return null;
        }
    }


        
    /**
     * Compute a hash from the text, represented as a hexadecimal string.
     */ 
    public final static String digestHex(String txt)
        {return b2hex(digest(null, txt));}
        
    public final static String xDigestHex(String txt)
        {return b2hex(xDigest(null, txt));}

    public final static String hmacHex(String txt, String key)
        {return b2hex(hmac(txt, key)); }
        
        
    /**
     * Base 64 encoded digest.
     * Returns n first characters of digest, encoded using
     * the Base 64 method.
     */
    public final static String digestB64(String txt, int n)
    {
       Base64 b64 = new Base64();
       String d = b64.encode(digest(null, txt));
       return d.substring(0,n); 
    }
    
    public final static String xDigestB64(String txt, int n)
    {
       Base64 b64 = new Base64();
       String d = b64.encode(xDigest(null, txt));
       return d.substring(0,n); 
    }
     
    public final static String hmacB64(String txt, String key, int n)
    {
       Base64 b64 = new Base64();
       String d = b64.encode(hmac(txt, key));
       return d.substring(0,n); 
    }
    
     
     
     
    /* FIXME: Can we use Java's own b64 implementation?  Must test! */
    public final static String b64encode(byte[] x) 
    {
        Base64 b64 = new Base64();
        return b64.encode(x);
    }
    public final static byte[] b64decode(String txt) 
    {
        Base64 b64 = new Base64();
        return b64.decode(txt);
    }
    
    
     
    
    /**
     * Hexadecimal representation of a byte array.
     */
    public final static String b2hex (byte[] bytes)
    {	
	StringBuffer sb = new StringBuffer(bytes.length * 2);
        for (int i = 0; i < bytes.length; i++) {
            sb.append(convertDigit((int) (bytes[i] >> 4)));
            sb.append(convertDigit((int) (bytes[i] & 0x0f)));
        }
        return (sb.toString());
    }     
        
        
    /**
     * [Private] Convert the specified value (0 .. 15) to the corresponding
     * hexadecimal digit.
     *
     * @param value Value to be converted
     */
    private final static char convertDigit(int value) {

        value &= 0x0f;
        if (value >= 10)
            return ((char) (value - 10 + 'a'));
        else
            return ((char) (value + '0'));

    }  
    
    
    
    public static String escape4regex(String x) {
        return x.replaceAll("([\\$\\^\\*\\+\\?\\.\\(\\)\\[\\]\\{\\}])", "\\\\$1");
    }
    
    
}

