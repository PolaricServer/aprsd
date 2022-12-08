package no.polaric.aprsd.http;
import java.io.*;
import java.net.http.*;
import java.net.*;
import no.polaric.aprsd.SecUtils;
import no.polaric.aprsd.ServerAPI;
import no.polaric.aprsd.*;
import spark.Request;
import spark.Response;



public class HmacAuth {
    private ServerAPI _api; 
    private String _key; 
    private DuplicateChecker _dup;
    
    
    
    public HmacAuth(ServerAPI api, String key) {
        _api = api;
        _key = key;
        _dup = new DuplicateChecker(2000);
    }
    
    
    /* Add headers to http request */
    public final HttpRequest.Builder addAuth(HttpRequest.Builder bld, String body) {
        String k = _api.getProperty(_key, "NOKEY");
        String nonce = SecUtils.b64encode( SecUtils.getRandom(8) );
        bld.header("Arctic-Nonce", nonce );
        bld.header("Arctic-Hmac", SecUtils.hmacB64(nonce+body, k, 44));
        return bld;
    }
    
    
    /* Check headers of http request */
    public final boolean checkAuth(Request req) {
        String rmac = req.headers("Arctic-Hmac");
        String nonce = req.headers("Arctic-Nonce");
        return checkAuth(nonce, rmac, req.body());
    }
    
        
    /* Check authentication fields: nonce, hmac and data) */
    public final boolean checkAuth(String nonce, String rmac, String data) {
        String k = _api.getProperty(_key, "NOKEY");
        if (_dup.contains(nonce)) 
            return false;
    
        _dup.add(nonce);
        return SecUtils.hmacB64(nonce+data, k, 44).equals(rmac); 
    }
    
    
    
    public final String genAuthPrefix(String data) {
        /*
         * Generate a nonce and a HMAC based on the data 
         * (and the nonce). 
         */
        String k = _api.getProperty(_key, "NOKEY");
        String nonce = SecUtils.b64encode( SecUtils.getRandom(8) );
        String hmac = SecUtils.hmacB64(nonce+data, k, 44);     
        return nonce+" "+hmac;
    }
    
    
}


