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
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;

/**
 * Periodically checks if up to 3 hosts are reachable.
 * If all configured hosts are unreachable, the status is set to offline.
 * Provides callbacks for when the offline status changes.
 */
public class OfflineDetector {
    
    private final ServerConfig _config;
    private final List<String> _hosts;
    private final long _checkInterval;
    private final int _timeout;
    private boolean _isOffline;
    private final List<StatusChangeCallback> _callbacks;
    private final ScheduledExecutorService _scheduler;
    private ScheduledFuture<?> _checkTask;
    private final Object _statusLock = new Object();
    
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
        _hosts = new ArrayList<>();
        _callbacks = new CopyOnWriteArrayList<>();
        _isOffline = false;
        
        // Read configuration
        String host1 = config.getProperty("offlinedetector.host1", "").trim();
        String host2 = config.getProperty("offlinedetector.host2", "").trim();
        String host3 = config.getProperty("offlinedetector.host3", "").trim();
        
        if (!host1.isEmpty()) _hosts.add(host1);
        if (!host2.isEmpty()) _hosts.add(host2);
        if (!host3.isEmpty()) _hosts.add(host3);
        
        // Default check interval: 60 seconds, minimum 5 seconds to prevent excessive CPU usage
        int interval = config.getIntProperty("offlinedetector.interval", 60);
        _checkInterval = Math.max(interval, 5);
        
        // Default timeout for host check: 5 seconds, must be positive and reasonable
        int timeout = config.getIntProperty("offlinedetector.timeout", 5000);
        _timeout = Math.max(Math.min(timeout, 60000), 1000);  // Between 1 and 60 seconds
        
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
        if (_hosts.isEmpty()) {
            _config.log().warn("OfflineDetector", "No hosts configured, detector will not start");
            return;
        }
        
        _config.log().info("OfflineDetector", 
            "Starting with hosts: " + String.join(", ", _hosts) + 
            ", interval: " + _checkInterval + "s");
        
        _checkTask = _scheduler.scheduleAtFixedRate(
            this::checkHosts,
            0,  // Initial delay
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
     * Check if all configured hosts are reachable.
     */
    private void checkHosts() {
        try {
            boolean allUnreachable = true;
            
            for (String host : _hosts) {
                if (isHostReachable(host)) {
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
            _config.log().error("OfflineDetector", "Error during host check: " + e.getMessage());
        }
    }
    
    /**
     * Check if a single host is reachable.
     * Uses InetAddress.isReachable() which may have platform-specific limitations.
     * 
     * Note: On Linux, this method requires CAP_NET_RAW capability to send ICMP packets.
     * The Debian package installation automatically grants this capability to the Java binary
     * using setcap. Without this capability, the method will fall back to TCP-based checks
     * which may not work reliably through firewalls or in some network configurations.
     * 
     * @param host The hostname or IP address
     * @return true if reachable, false otherwise
     */
    private boolean isHostReachable(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            boolean reachable = address.isReachable(_timeout);
            
            _config.log().debug("OfflineDetector", 
                "Host " + host + " is " + (reachable ? "reachable" : "unreachable"));
            
            return reachable;
        } catch (IOException e) {
            _config.log().debug("OfflineDetector", 
                "Failed to check host " + host + ": " + e.getMessage());
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
