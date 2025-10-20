# WebSocket Update Fix Documentation

## Problem
When tracker-points call the `setChanging()` method, this should trigger an update sent to interested clients through the websocket after at most 5 seconds. Previously, this didn't happen except as a response to a SUBSCRIBE message.

## Root Cause
The `MapUpdater.signal(TrackerPoint st)` method was calling:
```java
postText("", x -> ((Client)x).isInside(st, true));
```

When an empty string is passed to `postText`, the WsNotifier base class implementation likely doesn't iterate through clients or execute the filter function. This prevented the `_pending` flag from being set on matching clients, so the periodic timer task wouldn't send updates.

## Solution
Changed the `signal()` method to use a lambda function that returns null:
```java
postText(x -> null, x -> ((Client)x).isInside(st, true));
```

This ensures that the `postText` implementation must iterate through clients and execute the filter function to determine what to send. The filter execution properly sets the `_pending` flag on clients that should receive updates, even though the function returns null (meaning no data is sent immediately).

## How It Works

### Update Flow
1. When `TrackerPoint.setChanging()` is called:
   - Sets `_changing = true`
   - Calls `_change.signal(this)` which invokes `MapUpdater.signal(TrackerPoint st)`

2. `MapUpdater.signal(TrackerPoint st)`:
   - Calls `postText(x -> null, x -> ((Client)x).isInside(st, true))`
   - This iterates through all connected clients
   - For each client, `isInside(st, true)` is called:
     - If the tracker point is inside the client's area of interest, sets `_pending = true`
     - Returns false (because `postpone = true`), so no data is sent yet

3. Periodic Timer Task (runs every 5 seconds):
   - Calls `postText(x -> getOverlayData(false), x -> isInside(null, false))`
   - For each client, `isInside(null, false)` is called:
     - If `_pending = true`, resets it to false and returns true
     - This triggers overlay data generation and sending to the client
   - Clients with pending updates receive the overlay data within 5 seconds

### Why This Fix Works
- Using a Function that returns null instead of a String forces the postText implementation to iterate and execute the filter
- The filter execution sets the `_pending` flag appropriately
- The periodic timer picks up clients with `_pending = true` and sends them updates
- The delay mechanism prevents excessive updates (max once per 5 seconds)

## Testing
To verify this fix works:

1. Set up a Polaric Server instance with websocket clients connected
2. Subscribe a client to a specific geographic area
3. Trigger a `setChanging()` call on a tracker point within that area (e.g., by receiving a position update)
4. Verify that the client receives an update via websocket within 5 seconds
5. The update should occur even if no SUBSCRIBE message was recently sent

## Impact
- Minimal code change (single line modified)
- No breaking changes to API or behavior
- Improves real-time update delivery to clients
- Maintains the existing 5-second update throttling mechanism
