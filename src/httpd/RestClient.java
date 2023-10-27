 
/* 
 * Copyright (C) 2022-2023 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
    private HmacAuthenticator _auth;
    private String _userid;

    
    public RestClient(ServerAPI api, String url, HmacAuthenticator hm, String userid) {
        _api = api;
        _url=url;
        _auth = hm;
        _userid = userid;
        _client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    }
    
    public RestClient(ServerAPI api, String url, String userid)
        { this(api, url, (HmacAuthenticator) null, userid);}
    
    
    public RestClient(ServerAPI api, String url, Authenticator auth) {
        _api=api;
        _url=url;
        _userid=null;
        _client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .authenticator(auth)
            .build();
    }
    
    
    
    public String getUrl() {return _url;}
    
    
    
    /* Add Authorization header */
    private HttpRequest.Builder addAuth(HttpRequest.Builder bld, String body) {
        if (_auth != null) 
            _auth.addAuth(bld, body, _userid);
        return bld;
    }
    
    
    
 
 
    public HttpResponse GET(String resource, boolean stream) 
        throws URISyntaxException, IOException, InterruptedException
    {
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
    
    
    
    public HttpResponse GET(String resource)     
        throws URISyntaxException, IOException, InterruptedException
        { return GET(resource, false); }
    
    
    
    public HttpResponse POST(String resource, String body)
        throws URISyntaxException, IOException, InterruptedException 
    {
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
    
    
    
    
    public HttpResponse PUT(String resource, String body) 
        throws URISyntaxException, IOException, InterruptedException 
    {
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
       
       
       
       
    public HttpResponse DELETE(String resource) 
        throws URISyntaxException, IOException, InterruptedException 
    {
        HttpRequest.Builder bld = HttpRequest.newBuilder()
            .uri(new URI(_url+"/"+resource))
            .headers("Content-Type", "text/plain;charset=UTF-8");
            
        HttpRequest request = addAuth(bld, "")
            .DELETE()
            .build();
        
        /* Send the request and get the response */
        HttpResponse<String> response = _client
            .send(request, HttpResponse.BodyHandlers.ofString());
        return response;
    }   
}

































































































