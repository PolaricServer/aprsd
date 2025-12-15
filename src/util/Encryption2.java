    
package no.polaric.aprsd.util; 
import no.polaric.core.util.*;
import java.util.*;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.NoSuchAlgorithmException;
import java.security.spec.*;
import java.time.LocalDate;

 
/**
 * Implement symmetric encryption. Based on AES cipher. Works for short strings and 
 * can generate key from passwords/passphrase like secrets. 
 */
public class Encryption2 {
    
    private SecretKey _key;
    private String _algorithm = "AES/GCM/NoPadding";
    private Cipher _cipher;

    
    /**
     * Sets up an encryption channel with a key. Key is generated from the given secret and a 
     * default salt 
     * @param k Secret (typically a password or passphrase)
     */
    public Encryption2(String k) 
    {
        try {
            _cipher = Cipher.getInstance(_algorithm);

            /* Generate AES key from shared secret and a salt. */
            _key = getKeyFromPassword(k, "PoLaR1c"); 
            
        } catch(Exception e) {
            throw new Error("Error in encryption setup: "+e.getMessage(), e);
        } 
    }
    
    
    /** 
     * Prepare an initialization vector. At each encryption, the two first 
     * bytes should be replaced with a random number which should also be used 
     * as a prefix to the ciphertext. 
     */
    private byte[] getIv(byte[] prefix, String prefix2) 
    {
        /* 12 bytes nonce when using AES/GCM */
        byte[] iv = "000123456789".getBytes();
        iv[0] = prefix[0];
        iv[1] = prefix[1];
        
        if (prefix2 == null) {
            /* Add day of year */
            byte day = (byte) (LocalDate.now().getDayOfYear() % 256);
            iv[2] = day;
        }
        else {
            byte[] pf2 = prefix2.getBytes();
            int i = 2;
            for (byte b : pf2) {
                iv[i++] = b;
                if (i > 12)
                    break;
            }
        }
        return iv;
    }
    
    
    
    /**
     * Encrypt a string.
     * If nonce is null, use day of the year instead % 256.
     */
    public synchronized String encrypt(String inp, String nonce) 
    {
        try {
            /* Prepare the initialization vector */
            byte[] prefix = SecUtils.getRandom(2); 
            byte[] iv = getIv(prefix, nonce); 
        
            /* Do the encryption */ 
            GCMParameterSpec parameterSpec = new GCMParameterSpec(96, iv); 
            _cipher.init(Cipher.ENCRYPT_MODE, _key, parameterSpec);

            byte[] ctext = _cipher.doFinal(inp.getBytes());
        
            /* Prefix the resulting ciphertext */
            byte[] result = Arrays.copyOf(prefix, 2+ctext.length);
            System.arraycopy(ctext, 0, result, 2, ctext.length);
        
            /* Return Base64 encoded string */
            return SecUtils.b64encode(result);
        }
        catch (Exception e) {
            throw new Error("Error in encryption: "+e.getMessage(), e);
        }
    }
    
    
    
    /**
     * Decrypt a string. 
     * If nonce is null, use day of the year instead % 256.
     */
    public synchronized String decrypt(String inp, String nonce) 
    {
        try {
            /* Prepare the intitialization vector using 2 fist bytes of input */
            byte[] ibytes = SecUtils.b64decode(inp);
            byte[] iv = getIv(ibytes, nonce); 
        
            /* Get the ciphertext without the prefix */
            byte[] ctext = Arrays.copyOfRange(ibytes, 2, ibytes.length);
                
            /* Do the decryption and return result */  
            GCMParameterSpec parameterSpec = new GCMParameterSpec(96, iv); 
            _cipher.init(Cipher.DECRYPT_MODE, _key, parameterSpec);
            byte[] text = _cipher.doFinal(ctext);
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
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt.getBytes(), 65536, 256);
        SecretKey secret = new SecretKeySpec(factory.generateSecret(spec)
            .getEncoded(), "AES");
        return secret;
    }
    

}


