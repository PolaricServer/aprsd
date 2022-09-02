 
/* 
 * Copyright (C) 2022 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
 
package no.polaric.aprsd.http;
import java.io.*;
import java.net.http.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.time.Duration;
import no.polaric.aprsd.SecUtils;
import no.polaric.aprsd.ServerAPI;


public class RestClient {

    private ServerAPI _api;  
    private HttpClient _client; 
    private String _url; 
    private boolean _hmacAuth = false;
    

    
    public RestClient(ServerAPI api, String url, boolean hm) {
        _api = api;
        _url=url;
        _hmacAuth = hm;
        _client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    }
    
    public RestClient(ServerAPI api, String url)
        { this(api, url, false);}
    
    
    public RestClient(ServerAPI api, String url, Authenticator auth) {
        _api=api;
        _url=url;
        _client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .authenticator(auth)
            .build();
    }
    
    
    
    public String getUrl() {return _url;}
    
    
    
    private HttpRequest.Builder addAuth(HttpRequest.Builder bld, String body) {
        if (_hmacAuth) {
            String key = _api.getProperty("system.auth.key", "NOKEY");
            String nonce = SecUtils.b64encode( SecUtils.getRandom(8) );
            bld.header("Arctic-Nonce", nonce );
            bld.header("Arctic-Hmac", SecUtils.hmacB64(nonce+body, key, 44));
        }
        return bld;
    }
    
    
 
    public HttpResponse GET(String resource, boolean stream) {
    
        try {
            HttpRequest.Builder bld = HttpRequest.newBuilder()
                .uri(new URI(_url+"/"+resource))
                .headers("Content-Type", "text/plain;charset=UTF-8");
            
            HttpRequest request = addAuth(bld, "")
                .GET()
                .build();
            
            /* Send the request and get the response */
            if (stream) { 
                HttpResponse<InputStream> response = _client
                    .send(request, HttpResponse.BodyHandlers.ofInputStream());
                return response;
            }
            else {
                HttpResponse<String> response = _client
                    .send(request, HttpResponse.BodyHandlers.ofString());
                return response;
            }
            
            
        }
        catch (Exception e) { return null; }
    }
    
    
    public HttpResponse GET(String resource) 
        { return GET(resource, false); }
    
    
    
    public HttpResponse POST(String resource, String body) {
    
        try {
           HttpRequest.Builder bld = HttpRequest.newBuilder()
                .uri(new URI(_url+"/"+resource))
                .headers("Content-Type", "text/plain;charset=UTF-8");
                
            HttpRequest request = addAuth(bld, body)   
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            
            /* Send the request and get the response */
            HttpResponse<String> response = _client
                .send(request, HttpResponse.BodyHandlers.ofString());
            return response;
        }
        catch (Exception e) { e.printStackTrace(System.out); return null; }
    }
    
    
    
    
    public HttpResponse PUT(String resource, String body) {
    
        try {
            HttpRequest.Builder bld = HttpRequest.newBuilder()
                .uri(new URI(_url+"/"+resource))
                .headers("Content-Type", "text/plain;charset=UTF-8");
                
           HttpRequest request = addAuth(bld, body)
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
            
            /* Send the request and get the response */
            HttpResponse<String> response = _client
                .send(request, HttpResponse.BodyHandlers.ofString());
            return response;
        }
        catch (Exception e) { return null; }
    }
}

































































































