    
package no.polaric.aprsd.util;
import java.util.*;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import javax.crypto.Cipher;
import java.security.NoSuchAlgorithmException;
import java.security.spec.*;
import java.time.LocalDate;
import java.nio.ByteBuffer;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.security.Security;



 
/**
 * Implement symmetric encryption. Based on AES cipher. Works for short strings and 
 * can generate key from passwords/passphrase-like secrets.
 *
 * Use AES-GCM-SIV
 *   Use of 8-12 character initialisation vector (nonce) is recommended.
 *   IV is padded with null to be 12 bytes long.
 *
 *   256 bit AES key is generated from a pasword-like secret using PBKDF with HmacSHA25
 *   using a salt which should be different for different services
 *
 *   Ciphertext can be encoded using Base64 or Base91. See superclass
 */

public class AesGcmSivEncryption extends Encryption {
    
    private SecretKey _key;
    private String _algorithm = "AES/GCM-SIV/NoPadding";
    private Cipher _cipher;
    
    private static final int PKDF2_ITERATIONS = 16384;
    
    

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    
    /**
     * Sets up an encryption channel with a key. Key is generated from the given secret and a salt.
     * The salt should be different for different services. A default value will be used if null.
     * @param k Secret (typically a password or passphrase)
     * @param salt Salt-value to make it harder to crack the key (password)
     */
    public AesGcmSivEncryption(String k, String salt)
    {
        if (salt==null)
            salt = "PoLaR1c";
        try {
            _cipher = Cipher.getInstance(_algorithm, "BC");

            /* Generate AES key from shared secret and a salt. */
            _key = getKeyFromPassword(k, salt);
            
        } catch(Exception e) {
            throw new Error("Error in encryption setup: "+e.getMessage(), e);
        } 
    }

    
    /** 
     * Prepare an initialization vector.
     */
    private byte[] getIv(String arg)
    {
        int len = arg.length();
        if (len > 12)
            len = 12;
        byte[] nonce = new byte[12];
        ByteBuffer.wrap(nonce).put(arg.getBytes(), 0, len);
        return nonce;
    }
    

    
    /**
     * Encrypt a string.
     */
    public synchronized byte[] encrypt(String inp, String nonce, String aad)
    {
        try {
            /* Prepare the initialization vector */
            byte[] iv = getIv(nonce);
        
            /* Do the encryption */ 
            GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
            _cipher.init(Cipher.ENCRYPT_MODE, _key, parameterSpec);

            if (aad != null)
                _cipher.updateAAD(aad.getBytes());

            return _cipher.doFinal(inp.getBytes());
        }
        catch (Exception e) {
            throw new Error("Error in encryption: "+e.getMessage(), e);
        }
    }
    
    
    
    /**
     * Decrypt a string. 
     */
    public synchronized String decrypt(byte[] inp, String nonce, String aad)
    {
        try {
            byte[] iv = getIv(nonce);
        
            /* Do the decryption and return result */  
            GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
            _cipher.init(Cipher.DECRYPT_MODE, _key, parameterSpec);

            if (aad != null)
                _cipher.updateAAD(aad.getBytes());

            byte[] text = _cipher.doFinal(inp);
            return new String(text);
        }
        catch (Exception e) {
            if (e instanceof javax.crypto.AEADBadTagException)
                return null;
            throw new Error("Error in encryption: "+e.getMessage(), e);
        }    
    }
    
    
    

    
    /**
     * Generate an AES key from a password. 
     */
    public static SecretKey getKeyFromPassword(String password, String salt)
        throws NoSuchAlgorithmException, InvalidKeySpecException {
    
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt.getBytes(), PKDF2_ITERATIONS, 256);
        SecretKey secret = new SecretKeySpec(factory.generateSecret(spec)
            .getEncoded(), "AES");
        return secret;
    }
    

}


