 
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



public class RestClient {
    
    private HttpClient _client; 
    private String _url; 
    

    
    RestClient(String url) {
        _url=url;
        _client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    }
    
    
    RestClient(String url, Authenticator auth) {
        _url=url;
        _client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .authenticator(auth)
            .build();
    }
    
    
    
    public String getUrl() {return _url;}
    
    
 
    public HttpResponse GET(String resource) {
    
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(_url+"/"+resource))
                .headers("Content-Type", "text/plain;charset=UTF-8")
                .GET()
                .build();
            
            /* Send the request and get the response */
            HttpResponse<String> response = _client
                .send(request, HttpResponse.BodyHandlers.ofString());
            return response;
        }
        catch (Exception e) { return null; }
    }
    
    
    
    
    public HttpResponse POST(String resource, String body) {
    
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(_url+"/"+resource))
                .headers("Content-Type", "text/plain;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            
            /* Send the request and get the response */
            HttpResponse<String> response = _client
                .send(request, HttpResponse.BodyHandlers.ofString());
            return response;
        }
        catch (Exception e) { return null; }
    }
    
    
    
    
    public HttpResponse PUT(String resource, String body) {
    
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(_url+"/"+resource))
                .headers("Content-Type", "text/plain;charset=UTF-8")
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

































































































