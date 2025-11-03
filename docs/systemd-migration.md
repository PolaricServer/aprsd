# Migration from init.d to systemd

## Overview

Starting with version 4.0, Polaric APRSD uses systemd for service management instead of the legacy init.d scripts. This provides better integration with modern Linux distributions and improved service management capabilities.

The package includes both systemd service files and init.d scripts for backward compatibility, but systemd is the preferred and primary method on modern systems.

## What Changed

### Service Management

The service is now managed using systemd commands:

**Starting the service:**
```bash
sudo systemctl start polaric-aprsd.service
```

**Stopping the service:**
```bash
sudo systemctl stop polaric-aprsd.service
```

**Restarting the service:**
```bash
sudo systemctl restart polaric-aprsd.service
# or use the convenience script:
sudo polaric-restart
```

**Checking service status:**
```bash
sudo systemctl status polaric-aprsd.service
```

**Enabling service to start on boot:**
```bash
sudo systemctl enable polaric-aprsd.service
```

**Disabling service from starting on boot:**
```bash
sudo systemctl disable polaric-aprsd.service
```

### Log Viewing

With systemd, you can view logs using journalctl:

```bash
# View service logs
sudo journalctl -u polaric-aprsd.service

# Follow logs in real-time
sudo journalctl -u polaric-aprsd.service -f

# View logs since last boot
sudo journalctl -u polaric-aprsd.service -b
```

Logs are still also written to `/var/log/polaric/aprsd.log` as before.

## Backward Compatibility

The `polaric-restart` script has been updated to use systemctl, so existing scripts and workflows that use this command will continue to work.

## Upgrade Notes

When upgrading from a previous version:

1. The old init.d script will be automatically removed during the upgrade
2. The systemd service will be automatically enabled and started
3. No manual intervention should be required

If you experience any issues during the upgrade, you can manually start the service:

```bash
sudo systemctl daemon-reexec
sudo systemctl enable polaric-aprsd.service
sudo systemctl start polaric-aprsd.service
```

## Security Improvements

The systemd service includes security hardening options:
- NoNewPrivileges: Prevents privilege escalation
- PrivateTmp: Provides isolated /tmp directory
- ProtectSystem: Protects system directories from modification
- ProtectHome: Restricts access to home directories
- LimitNOFILE: Sets file descriptor limits

These options improve the security posture of the service without affecting normal operation.

### ICMP Packet Capabilities

Starting with version 4.0.1, the package installation automatically grants the Java binary the `CAP_NET_RAW` capability to allow sending and receiving ICMP packets (used by the OfflineDetector feature). This is done using the `setcap` command during installation:

```bash
setcap cap_net_raw+ep /path/to/java
```

This allows the OfflineDetector to check if configured hosts are reachable without requiring the entire service to run as root. The capability is applied only to the Java binary itself, maintaining the principle of least privilege.

If you need to manually verify or reapply this capability:

```bash
# Check current capabilities
getcap /usr/bin/java

# Manually set capability (requires root)
sudo setcap cap_net_raw+ep $(readlink -f /usr/bin/java)
```

Note: If the `setcap` utility is not available during installation, a warning will be displayed, and the OfflineDetector ICMP checks may not function correctly.

## Troubleshooting

If the service fails to start:

1. Check the service status:
   ```bash
   sudo systemctl status polaric-aprsd.service
   ```

2. View recent logs:
   ```bash
   sudo journalctl -u polaric-aprsd.service -n 50
   ```

3. Verify file permissions:
   ```bash
   ls -la /var/log/polaric/
   ls -la /var/lib/polaric/
   ls -la /etc/polaric-aprsd/
   ```

4. Ensure the polaric user exists:
   ```bash
   id polaric
   ```

If you need to temporarily revert to manual starting:
```bash
sudo -u polaric /usr/bin/polaric-aprsd-start
```
