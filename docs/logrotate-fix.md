# Logrotate Configuration Fix

## Problem
When aprsd was restarted automatically by logrotate weekly, the service would stop instead of restarting properly. This was caused by the log rotation strategy that required a service restart.

## Root Cause
The startup script (`/usr/bin/polaric-aprsd-start`) redirects stdout and stderr to a log file:
```bash
exec $JAVA ... >> $LOGFILE 2>&1
```

When logrotate rotates logs using the move strategy and then restarts the service:
1. The old log file is moved/renamed
2. The service is restarted
3. If the new log file doesn't exist or has permission issues, the redirect fails
4. The service fails to start

## Solution
Changed the logrotate configuration to use `copytruncate` instead of service restart:

### Before (debian/polaric-aprsd.logrotate):
```
/var/log/polaric/*.log {
    weekly
    missingok
    rotate 52
    postrotate
        systemctl restart polaric-aprsd.service > /dev/null
    endscript
    compress
}
```

### After:
```
/var/log/polaric/*.log {
    weekly
    missingok
    rotate 52
    copytruncate
    compress
    delaycompress
    notifempty
}
```

### Key Changes:
- **copytruncate**: Copies the log file before truncating it in place, avoiding the need to restart
- **delaycompress**: Delays compression until the next rotation cycle (compatible with copytruncate)
- **notifempty**: Don't rotate empty log files
- **Removed postrotate**: No longer need to restart the service

### Additional Improvements (scripts/polaric-aprsd-start):
Added safeguards to ensure the log file exists:
```bash
# Ensure log directory exists
mkdir -p "$LOGDIR"

# Ensure log file exists and is writable
touch "$LOGFILE" 2>/dev/null || true
```

This ensures that even if the log file is missing for any reason, the service can still start.

## Benefits
1. **No service interruption**: Service continues running during log rotation
2. **More reliable**: Eliminates the restart failure scenario
3. **Better for monitoring**: No gaps in service availability
4. **Simpler**: Fewer moving parts = fewer failure points

## Testing
To test the fix:
1. Manually trigger logrotate: `sudo logrotate -f /etc/logrotate.d/polaric-aprsd`
2. Verify service is still running: `sudo systemctl status polaric-aprsd.service`
3. Check logs are rotated: `ls -la /var/log/polaric/`

## References
- logrotate man page: `man logrotate`
- copytruncate documentation: Copies log file and truncates original in place
