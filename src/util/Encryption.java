    
package no.polaric.aprsd.util;
import no.polaric.core.util.*;
import java.util.*;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import javax.crypto.Cipher;
import java.security.NoSuchAlgorithmException;
import java.security.spec.*;
import java.time.LocalDate;
import java.nio.ByteBuffer;


 
/**
 * Implement symmetric encryption. Based on AES cipher. Works for short strings and 
 * can generate key from passwords/passphrase like secrets. 
 */
public abstract class Encryption {
    

    
    /**
     * Encrypt a string.
     */
    public abstract byte[] encrypt(String inp, String nonce, String aad);
    
    public String encryptB64(String inp, String nonce, String aad) {
        byte[] ctext = encrypt(inp, nonce, aad);
        return SecUtils.b64encode(ctext);
    }

    public String encryptB91(String inp, String nonce, String aad) {
        byte[] ctext = encrypt(inp, nonce, aad);
        return SecUtils.b91encode(ctext);
    }


    /**
     * Decrypt a string. Returns null if decryption or authentication fails.
     */
    public abstract String decrypt(byte[] inp, String nonce, String aad);
    
    public String decryptB64(String inp, String nonce, String aad) {
        byte[] ibytes = SecUtils.b64decode(inp);
        return decrypt(ibytes, nonce, aad);
    }

    public String decryptB91(String inp, String nonce, String aad) {
        byte[] ibytes = SecUtils.b91decode(inp);
        return decrypt(ibytes, nonce, aad);
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


