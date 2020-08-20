
package no.polaric.aprsd.http;
import java.util.*;


public interface WebClient {
    public AuthInfo getAuthInfo();
    public boolean login(); 
    public String getUid();
    public String getUsername();
    public Date created();
}

