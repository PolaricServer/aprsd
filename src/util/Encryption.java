    
package no.polaric.aprsd.util; 
import no.arctic.core.util.*;
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
public class Encryption {
    
    private SecretKey _key;
    private String _algorithm = "AES/CBC/PKCS5Padding";
    private Cipher _cipher;

    
    /**
     * Sets up an encryption channel with a key. Key is generated from the given secret and a 
     * default salt 
     * @param k Secret (typically a password or passphrase)
     */
    public Encryption(String k) 
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
    private IvParameterSpec getIv(byte[] prefix) {
        byte[] iv = "000123456789abcd".getBytes();
        iv[0] = prefix[0];
        iv[1] = prefix[1];
        /* Add day of year */
        byte day = (byte) (LocalDate.now().getDayOfYear() % 256);
        iv[2] = day; 
        return new IvParameterSpec(iv);
    }
    
    
    /**
     * Encrypt a string.
     */
    public synchronized String encrypt(String inp) 
    {
        try {
            /* Prepare the initialization vector */
            byte[] prefix = SecUtils.getRandom(2); 
            IvParameterSpec iv = getIv(prefix); 
        
            /* Do the encryption */ 
            _cipher.init(Cipher.ENCRYPT_MODE, _key, iv);
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
     */
    public synchronized String decrypt(String inp) 
    {
        try {
            /* Prepare the intitialization vector using 2 fist bytes of input */
            byte[] ibytes = SecUtils.b64decode(inp);
            IvParameterSpec iv = getIv(ibytes); 
        
            /* Get the ciphertext without the prefix */
            byte[] ctext = Arrays.copyOfRange(ibytes, 2, ibytes.length);
                
            /* Do the decryption and return result */  
            _cipher.init(Cipher.DECRYPT_MODE, _key, iv);
            byte[] text = _cipher.doFinal(ctext);
            return new String(text);
        }
        catch (Exception e) {
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


