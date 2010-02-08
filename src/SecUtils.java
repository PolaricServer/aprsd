/* 
 * Copyright (C) 2009 by Øyvind Hanssen (ohanssen@acm.org)
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

package aprs; 
import java.io.*;
import java.util.*;
import java.security.*;
import com.mindprod.base64.Base64;

 
public class SecUtils
{
       
    public final static byte[] digest ( byte[] bytes, String txt )
    {
       try{
           MessageDigest dig = MessageDigest.getInstance("MD5");
	   if (bytes != null) 
	      dig.update(bytes);
	   if (txt != null)
	      dig.update(txt.getBytes("UTF-8"));
	    
           return dig.digest();
      }
      catch (Exception e) {
          return null;
      }
    }

    
    
    public final static String digestHex(String txt)
        {return b2hex(digest(null, txt));}


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
    
}

