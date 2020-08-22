
package no.polaric.aprsd.http;
import java.util.*;
import com.fasterxml.jackson.annotation.*;


public interface WebClient {
    public AuthInfo getAuthInfo();
    
    public boolean login();
    
    public String getUid();
    
    public String getUsername();
    
    public Date created();
}

