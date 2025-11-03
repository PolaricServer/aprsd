/* 
 * Copyright (C) 2025 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 */

package no.polaric.aprsd;

import no.polaric.core.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Periodically checks if up to 3 Polaric APRSD instances are reachable via HTTP(s).
 * Uses the /system/ping endpoint which returns "Ok".
 * If all configured instances are unreachable, the status is set to offline.
 * Provides callbacks for when the offline status changes.
 */
public class OfflineDetector {
    
    private final ServerConfig _config;
    private final List<String> _baseUrls;
    private final long _checkInterval;
    private final int _timeout;
    private boolean _isOffline;
    private final List<StatusChangeCallback> _callbacks;
    private final ScheduledExecutorService _scheduler;
    private ScheduledFuture<?> _checkTask;
    private final Object _statusLock = new Object();
    private final HttpClient _httpClient;
    
    /**
     * Callback interface for offline status changes.
     */
    @FunctionalInterface
    public interface StatusChangeCallback {
        void onStatusChange(boolean isOffline);
    }
    
    /**
     * Constructor.
     * @param config Server configuration
     */
    public OfflineDetector(ServerConfig config) {
        _config = config;
        _baseUrls = new ArrayList<>();
        _callbacks = new CopyOnWriteArrayList<>();
        _isOffline = false;
        
        // Read configuration - now expecting base URLs instead of hostnames
        String baseUrl1 = config.getProperty("offlinedetector.host1", "").trim();
        String baseUrl2 = config.getProperty("offlinedetector.host2", "").trim();
        String baseUrl3 = config.getProperty("offlinedetector.host3", "").trim();
        
        if (!baseUrl1.isEmpty()) _baseUrls.add(baseUrl1);
        if (!baseUrl2.isEmpty()) _baseUrls.add(baseUrl2);
        if (!baseUrl3.isEmpty()) _baseUrls.add(baseUrl3);
        
        // Default check interval: 60 seconds, minimum 5 seconds to prevent excessive CPU usage
        int interval = config.getIntProperty("offlinedetector.interval", 120);
        _checkInterval = Math.max(interval, 10);
        
        // Default timeout for host check: 5 seconds, must be positive and reasonable
        int timeout = config.getIntProperty("offlinedetector.timeout", 5000);
        _timeout = Math.max(Math.min(timeout, 60000), 1000);  // Between 1 and 60 seconds
        
        // Create HTTP client with timeout configuration
        _httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(_timeout))
            .build();
        
        _scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "OfflineDetector");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * Start the periodic checking.
     */
    public void start() {
        if (_baseUrls.isEmpty()) {
            _config.log().warn("OfflineDetector", "No base URLs configured, detector will not start");
            return;
        }
        
        _config.log().info("OfflineDetector", 
            "Starting with base URLs: " + String.join(", ", _baseUrls) + 
            ", interval: " + _checkInterval + "s");
        
        _checkTask = _scheduler.scheduleAtFixedRate(
            this::checkHosts,
            10,  // Initial delay
            _checkInterval,
            TimeUnit.SECONDS
        );
    }
    
    /**
     * Stop the periodic checking.
     */
    public void stop() {
        if (_checkTask != null) {
            _checkTask.cancel(false);
        }
        _scheduler.shutdown();
        try {
            if (!_scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                _scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            _scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Check if all configured Polaric APRSD instances are reachable.
     */
    private void checkHosts() {
        try {
            boolean allUnreachable = true;
            
            for (String baseUrl : _baseUrls) {
                if (isInstanceReachable(baseUrl)) {
                    allUnreachable = false;
                    break;
                }
            }
            
            synchronized (_statusLock) {
                boolean wasOffline = _isOffline;
                _isOffline = allUnreachable;
                
                if (wasOffline != _isOffline) {
                    _config.log().info("OfflineDetector", 
                        "Status changed to: " + (_isOffline ? "OFFLINE" : "ONLINE"));
                    notifyCallbacks(_isOffline);
                }
            }
        } catch (Exception e) {
            _config.log().error("OfflineDetector", "Error during instance check: " + e.getMessage());
        }
    }
    
    /**
     * Check if a single Polaric APRSD instance is reachable via HTTP(s).
     * Makes a GET request to the /system/ping endpoint.
     * @param baseUrl The base URL of the Polaric APRSD instance (e.g., "http://example.com:8081")
     * @return true if reachable and returns "Ok", false otherwise
     */
    private boolean isInstanceReachable(String baseUrl) {
        try {
            // Ensure base URL doesn't end with a slash
            String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            url = url + "/system/ping";
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(_timeout))
                .GET()
                .build();
            
            HttpResponse<String> response = _httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean reachable = (response.statusCode() == 200 && "Ok".equals(response.body().trim()));
            
            _config.log().debug("OfflineDetector", 
                "Instance " + baseUrl + " is " + (reachable ? "reachable" : "unreachable") +
                " (status: " + response.statusCode() + ")");
            
            return reachable;
        } catch (IOException | InterruptedException e) {
            _config.log().debug("OfflineDetector", 
                "Failed to check instance " + baseUrl + ": " + e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            _config.log().warn("OfflineDetector", 
                "Invalid base URL " + baseUrl + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get the current offline status.
     * @return true if offline (all hosts unreachable), false otherwise
     */
    public boolean isOffline() {
        synchronized (_statusLock) {
            return _isOffline;
        }
    }
    
    /**
     * Register a callback to be notified when the offline status changes.
     * @param callback The callback function
     */
    public void registerCallback(StatusChangeCallback callback) {
        if (callback != null) {
            _callbacks.add(callback);
        }
    }
    
    /**
     * Notify all registered callbacks of a status change.
     * @param isOffline The new offline status
     */
    private void notifyCallbacks(boolean isOffline) {
        for (StatusChangeCallback callback : _callbacks) {
            try {
                callback.onStatusChange(isOffline);
            } catch (Exception e) {
                _config.log().error("OfflineDetector", 
                    "Error in status change callback: " + e.getMessage());
            }
        }
    }
}
