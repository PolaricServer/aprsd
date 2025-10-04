====================================
APRS-IS Configuration and Usage
====================================

Overview
========

APRS-IS (Automatic Packet Reporting System - Internet Service) is a network of servers that provide worldwide connectivity for APRS data. Polaric APRSD can connect to APRS-IS servers to receive and send APRS packets over the internet.

The APRS-IS service in Polaric APRSD is implemented through the ``InetChannel`` class, which provides:

* Connection to APRS-IS servers (e.g., aprs.no, rotate.aprs2.net)
* Authentication with callsign and passcode
* Server-side filtering to reduce bandwidth
* Automatic reconnection on connection loss
* Integration with the igate for RF-to-Internet gating

Basic Configuration
===================

Configuration File
------------------

APRS-IS channels are configured in the ``server.ini`` file. The configuration follows a channel-based approach where each channel has a unique identifier.

Minimal Configuration
~~~~~~~~~~~~~~~~~~~~~

The simplest APRS-IS configuration::

    # Define the channel list
    channels = aprsIS
    
    # Set as default internet channel
    channel.default.inet = aprsIS
    
    # Channel configuration
    channel.aprsIS.on = true
    channel.aprsIS.type = APRSIS
    channel.aprsIS.host = rotate.aprs2.net
    channel.aprsIS.port = 14580

Configuration Parameters
------------------------

Required Parameters
~~~~~~~~~~~~~~~~~~~

``channel.<id>.type``
    **Value:** ``APRSIS``
    
    Specifies that this channel is an APRS-IS connection.

``channel.<id>.host``
    **Value:** hostname or IP address
    
    **Examples:** ``aprs.no``, ``rotate.aprs2.net``, ``euro.aprs2.net``
    
    The APRS-IS server hostname. Common servers include:
    
    * ``rotate.aprs2.net`` - Global tier 2 round-robin
    * ``euro.aprs2.net`` - European tier 2 round-robin
    * ``asia.aprs2.net`` - Asian tier 2 round-robin
    * ``noam.aprs2.net`` - North American tier 2 round-robin
    * ``soam.aprs2.net`` - South American tier 2 round-robin
    * ``aunz.aprs2.net`` - Australia/New Zealand tier 2 round-robin
    * ``aprs.no`` - Norwegian server with Nordic data

``channel.<id>.port``
    **Value:** port number (typically 14580)
    
    **Default:** 14580
    
    The APRS-IS server port. Standard ports:
    
    * **14580** - Full stream with filtering (most common)
    * **10152** - Full stream without filtering (high bandwidth)
    * **20152** - Client-only port (cannot send)

Optional Parameters
~~~~~~~~~~~~~~~~~~~

``channel.<id>.on``
    **Value:** ``true`` or ``false``
    
    **Default:** ``false``
    
    Enables or disables the channel. Set to ``true`` to activate the connection.

``channel.<id>.user``
    **Value:** callsign
    
    **Default:** Uses ``default.mycall`` value
    
    The callsign to authenticate with. If not specified, uses the ``default.mycall`` setting.

``channel.<id>.pass``
    **Value:** APRS-IS passcode (numeric)
    
    **Default:** -1 (read-only)
    
    The APRS-IS passcode for authentication. A passcode of ``-1`` or any negative number provides read-only access. To send packets, you need a valid passcode generated for your callsign.
    
    **Important:** Never share your passcode or commit it to public repositories.

``channel.<id>.filter``
    **Value:** APRS-IS filter string
    
    **Default:** empty (no filtering, receives all data)
    
    Server-side filter to limit the data received. See the `Filter Configuration`_ section and ``aprs-filters.rst`` for detailed filter syntax.

``channel.<id>.rfilter``
    **Value:** regular expression
    
    **Default:** empty
    
    Local receive filter using regular expressions to match packet content. Applied after server-side filtering.

Authentication
==============

APRS-IS uses a simple authentication mechanism based on callsigns and passcodes.

Read-Only Access
----------------

For read-only access (receiving packets only), no valid passcode is needed::

    channel.aprsIS.user = MYCALL
    channel.aprsIS.pass = -1

With read-only access, you can:

* Receive packets from APRS-IS
* Apply filters
* Display data on maps

You cannot:

* Send packets to APRS-IS
* Gate packets from RF to internet

Full Access (Sending)
----------------------

To send packets to APRS-IS (required for igating), you need a valid passcode::

    default.mycall = LA1B-5
    channel.aprsIS.pass = 12345

**Generating a Passcode**

Passcodes are generated from your callsign using a specific algorithm. You can generate your passcode:

1. Online: https://apps.magicbug.co.uk/passcode/
2. Command-line tools: Various implementations exist

**Security Notes:**

* Keep your passcode private
* Don't commit passcodes to version control
* Use read-only access unless you need to send data
* Consider using environment variables or separate config files for passcodes

User Identification String
---------------------------

When connecting, Polaric APRSD sends an identification string::

    user CALLSIGN pass PASSCODE vers Polaric-APRSD VERSION

This identifies the software and version to the APRS-IS server. The server logs this information.

Filter Configuration
====================

Server-side filters significantly reduce bandwidth by limiting which packets are sent to your client. Without filtering, you would receive the entire APRS-IS stream (potentially thousands of packets per minute).

Basic Filter Examples
---------------------

**Geographic Area (Norway)**::

    channel.aprsIS.filter = r/60/10/500

Receives packets within 500 km of position 60°N, 10°E.

**Callsign Prefix (Norwegian stations)**::

    channel.aprsIS.filter = p/LA/LB/LD

Receives packets from callsigns starting with LA, LB, or LD.

**Combined Filters**::

    channel.aprsIS.filter = p/LA/LB/LD r/60/10/500

Receives Norwegian callsigns OR packets within 500 km of Norway.

**Area with Specific Types**::

    channel.aprsIS.filter = r/60/10/500 &t/p

Receives only position reports within the range (AND operation).

Common Filter Patterns
----------------------

**Local Area Monitoring**::

    channel.aprsIS.filter = r/59.5/10.5/100

Monitor 100 km radius around your location.

**National Coverage**::

    channel.aprsIS.filter = p/LA/LB/LC/LD/JW/JX

All Norwegian amateur radio prefixes.

**Multiple Areas**::

    channel.aprsIS.filter = r/60/10/200 r/65/25/200

Two separate monitoring areas.

**Object Tracking**::

    channel.aprsIS.filter = o/SEARCH* o/FIRE*

Track specific objects (search and rescue, fire operations).

**Exclude Test Stations**::

    channel.aprsIS.filter = p/LA -b/TEST* -b/NOCALL*

Norwegian stations except test callsigns.

Filter Documentation
--------------------

For comprehensive filter syntax and capabilities, see the ``aprs-filters.rst`` document in the same directory. It covers:

* All filter types (area, range, prefix, type, etc.)
* Logical operators (AND, OR, NOT)
* Advanced filter combinations
* Wildcard patterns
* Performance considerations

Channel Management
==================

Multiple Channels
-----------------

You can configure multiple APRS-IS channels for different purposes::

    channels = aprsIS, aprsIS_backup, aprsIS_full
    
    # Primary channel with filtering
    channel.aprsIS.on = true
    channel.aprsIS.type = APRSIS
    channel.aprsIS.host = aprs.no
    channel.aprsIS.port = 14580
    channel.aprsIS.filter = p/LA/LB
    
    # Backup channel (different server)
    channel.aprsIS_backup.on = false
    channel.aprsIS_backup.type = APRSIS
    channel.aprsIS_backup.host = euro.aprs2.net
    channel.aprsIS_backup.port = 14580
    channel.aprsIS_backup.filter = p/LA/LB
    
    # Full stream for specific area (high bandwidth)
    channel.aprsIS_full.on = false
    channel.aprsIS_full.type = APRSIS
    channel.aprsIS_full.host = rotate.aprs2.net
    channel.aprsIS_full.port = 14580
    channel.aprsIS_full.filter = r/60/10/50

Default Channels
----------------

Polaric APRSD uses the concept of default internet and RF channels::

    channel.default.inet = aprsIS
    channel.default.rf = radio

The default internet channel is used by:

* Message processor (for sending messages)
* Own position reporting
* Own objects
* Igate (internet side)

Changing Channels at Runtime
-----------------------------

Channels can be managed through the REST API and web interface:

* Start/stop channels
* Modify filter settings
* View channel statistics
* Switch default channels

The configuration changes made through the web interface are saved when the server shuts down gracefully.

Igate Configuration
===================

An igate (Internet Gateway) bridges RF (radio) and APRS-IS, allowing packets to flow between the two networks. Polaric APRSD includes a full-featured bidirectional igate.

Basic Igate Setup
-----------------

To enable igate functionality::

    # Internet channel
    channels = aprsIS, radio
    channel.default.inet = aprsIS
    channel.default.rf = radio
    
    # APRS-IS configuration
    channel.aprsIS.on = true
    channel.aprsIS.type = APRSIS
    channel.aprsIS.host = rotate.aprs2.net
    channel.aprsIS.port = 14580
    channel.aprsIS.pass = 12345
    channel.aprsIS.filter = m/500
    
    # RF channel configuration
    channel.radio.on = true
    channel.radio.type = TNC2
    # (TNC-specific settings...)
    
    # Igate settings
    igate.mycall = LA1B-5
    igate.rfgate.allow = true
    igate.rfgate.objects = true

Igate Settings
--------------

``igate.mycall``
    **Value:** callsign
    
    **Default:** Uses ``default.mycall``
    
    The callsign used in Q construct when gating packets. Typically your station's callsign with SSID.

``igate.rfgate.allow``
    **Value:** ``true`` or ``false``
    
    **Default:** ``true``
    
    Enables bidirectional gating (Internet to RF). When ``true``, uses ``qAR`` (bidirectional). When ``false``, uses ``qAO`` (receive-only).
    
    **Warning:** Gating to RF may be restricted by local regulations. Check your license terms.

``igate.rfgate.objects``
    **Value:** ``true`` or ``false``
    
    **Default:** ``false``
    
    Allows gating of APRS objects to RF.

``objects.rfgate.path``
    **Value:** digipeater path
    
    **Default:** empty
    
    **Example:** ``WIDE1-1``
    
    Digipeater path to use when gating objects to RF.

``objects.rfgate.range``
    **Value:** distance in kilometers
    
    **Default:** 0 (disabled)
    
    Only gate objects within this range of your position to RF.

``message.rfpath``
    **Value:** digipeater path
    
    **Default:** ``WIDE1-1``
    
    Default path for messages sent to RF.

``message.alwaysRf``
    **Value:** regular expression
    
    **Default:** empty
    
    Callsign pattern (regex) for stations that should always receive messages via RF instead of internet.

Igate Filtering
---------------

The igate automatically filters out certain packets to prevent loops and respect APRS conventions:

**Blocked from RF to Internet:**

* Query packets (type ``?``)
* Packets with ``TCPIP*``, ``TCPXX*``, ``NOGATE``, ``RFONLY``, or ``NO_TX`` in path

**Q Constructs:**

* ``qAR`` - Bidirectional igate
* ``qAO`` - Receive-only igate

The igate adds the appropriate Q construct and your callsign to packets gated to internet.

Logging
-------

The igate maintains a separate log file (``igate.log``) that records all gated packets::

    [radio>aprsIS] LA1B>APRS,WIDE1-1:!6010.00N/01020.00E>Test

This helps in debugging and monitoring igate activity.

Advanced Configuration
======================

Connection Management
---------------------

``channel.<id>.retry``
    **Value:** seconds
    
    **Default:** 60
    
    Time to wait before attempting to reconnect after connection failure.

``channel.<id>.timeout``
    **Value:** seconds
    
    **Default:** 300
    
    Socket timeout for read operations.

Encoding
--------

``channel.<id>.encoding.rx``
    **Value:** character encoding
    
    **Default:** UTF-8
    
    Character encoding for received data.

``channel.<id>.encoding.tx``
    **Value:** character encoding
    
    **Default:** UTF-8
    
    Character encoding for transmitted data.

Packet Handling
---------------

``channel.logpackets``
    **Value:** ``true`` or ``false``
    
    **Default:** ``false``
    
    Log all received APRS packets. Useful for debugging but generates large logs.

REST API Integration
====================

Polaric APRSD provides REST API endpoints for managing APRS-IS channels dynamically.

Channel Status
--------------

**Endpoint:** ``GET /channel/<channel-id>``

Returns channel status and statistics::

    {
        "type": "APRSIS",
        "host": "aprs.no",
        "port": 14580,
        "pass": 12345,
        "filter": "p/LA/LB",
        "heard": 1234,
        "heardpackets": 5678,
        "duplicates": 42,
        "sentpackets": 100
    }

**Statistics:**

* ``heard`` - Number of unique stations heard
* ``heardpackets`` - Total packets received
* ``duplicates`` - Duplicate packets detected
* ``sentpackets`` - Packets sent to APRS-IS

Channel Configuration
---------------------

**Endpoint:** ``PUT /channel/<channel-id>/config``

Update channel configuration dynamically. Changes take effect on next connection::

    {
        "host": "euro.aprs2.net",
        "port": 14580,
        "pass": 12345,
        "filter": "r/60/10/500"
    }

**Note:** Passcode updates require appropriate permissions and should be done securely.

Channel Control
---------------

**Start Channel:** ``POST /channel/<channel-id>/start``

**Stop Channel:** ``POST /channel/<channel-id>/stop``

Allows dynamic control of channel activation without restarting the server.

Complete Configuration Example
===============================

This example shows a complete configuration for a Norwegian igate with APRS-IS connection::

    # Default callsign
    default.mycall = LA1B-5
    
    # Channel list
    channels = aprsIS, radio
    channel.default.inet = aprsIS
    channel.default.rf = radio
    
    # APRS-IS channel configuration
    channel.aprsIS.on = true
    channel.aprsIS.type = APRSIS
    channel.aprsIS.host = aprs.no
    channel.aprsIS.port = 14580
    channel.aprsIS.user = LA1B-5
    channel.aprsIS.pass = 12345
    
    # Filter for Norwegian coverage + nearby areas
    channel.aprsIS.filter = p/LA/LB/LC/LD/JW/JX r/60/10/500
    
    # RF channel (TNC2 example)
    channel.radio.on = true
    channel.radio.type = TNC2
    channel.radio.port = /dev/ttyUSB0
    channel.radio.baud = 9600
    
    # Igate configuration
    igate.mycall = LA1B-5
    igate.rfgate.allow = true
    igate.rfgate.objects = false
    message.rfpath = WIDE1-1
    
    # Object gating
    objects.rfgate.path = WIDE1-1
    objects.rfgate.range = 100

Troubleshooting
===============

Connection Issues
-----------------

**Symptom:** Cannot connect to APRS-IS server

**Checks:**

1. Verify hostname is correct and reachable::

       ping rotate.aprs2.net

2. Check port is not blocked by firewall

3. Review log files for connection errors::

       grep "InetChannel" /var/log/polaric/aprsd.log

4. Test with telnet::

       telnet rotate.aprs2.net 14580

**Common causes:**

* Incorrect hostname or port
* Firewall blocking outbound connections
* Network connectivity issues
* Server maintenance/downtime

Authentication Problems
-----------------------

**Symptom:** Connected but not receiving data or cannot send

**Checks:**

1. Verify callsign format (uppercase, no spaces)

2. Check passcode is correct for your callsign

3. For sending, ensure passcode is positive (not -1)

4. Review server messages in log::

       # logresp: LA1B-5 unverified

**Resolution:**

* Verify passcode matches your callsign
* Use passcode generator to confirm correct value
* Check for typos in callsign

No Data Received
----------------

**Symptom:** Connected but no packets received

**Checks:**

1. Verify filter is not too restrictive::

       # Try without filter temporarily
       channel.aprsIS.filter =

2. Check if server is sending data::

       # Look for server status messages
       # javAPRSSrvr...

3. Monitor packet statistics through REST API

**Resolution:**

* Adjust filter to be less restrictive
* Test with broad filter first (e.g., ``m/1000``)
* Verify there is actually traffic in your filter area

High Bandwidth Usage
--------------------

**Symptom:** Excessive network traffic

**Resolution:**

1. Implement restrictive server-side filters

2. Use geographic filters instead of prefix filters when possible

3. Avoid using port 10152 (full stream)

4. Monitor statistics to verify filter effectiveness

Performance Issues
------------------

**Symptom:** High CPU usage, slow response

**Checks:**

1. Review number of packets being processed::

       # Check heardpackets in channel statistics

2. Verify filters are appropriate

3. Check for excessive logging::

       channel.logpackets = false

**Resolution:**

* Optimize filters to reduce packet volume
* Disable packet logging in production
* Consider increasing JVM heap size

Igate Not Gating
----------------

**Symptom:** Igate enabled but packets not flowing between RF and internet

**Checks:**

1. Verify both channels are active and running

2. Check default channel settings::

       channel.default.inet = aprsIS
       channel.default.rf = radio

3. Review igate log::

       tail -f /var/log/polaric/igate.log

4. Verify passcode for internet sending

5. Check Q construct filtering isn't blocking packets

**Resolution:**

* Ensure proper channel configuration
* Verify valid passcode for sending to APRS-IS
* Check RF channel is actually receiving packets
* Review igate allow settings

Log Analysis
------------

**Important log messages:**

**Connection successful:**
    ``# javAPRSSrvr 2.1.0-g9f9f7f1``

**Authentication:**
    ``# logresp: LA1B-5 verified``

**Filter acknowledged:**
    ``# Note: Server filter: r/60/10/500``

**Disconnection:**
    ``Disconnected from APRS server 'aprs.no'``

Best Practices
==============

Filter Design
-------------

1. **Start restrictive, expand as needed**
   
   Begin with narrow filters and widen if you're missing data.

2. **Use geographic filters for local coverage**
   
   Range filters (``r/``) are more efficient than prefix filters for local areas.

3. **Combine filters effectively**
   
   Use AND operations (``&``) to create specific combinations.

4. **Test filters before deploying**
   
   Verify filter behavior on test systems first.

Security
--------

1. **Protect passcodes**
   
   * Never commit to version control
   * Use file permissions to restrict access
   * Consider separate config files

2. **Use read-only access when possible**
   
   Only use valid passcodes when you need to send data.

3. **Regular security audits**
   
   Review configuration files for exposed credentials.

Operational
-----------

1. **Monitor channel statistics**
   
   Regular checks help identify issues early.

2. **Maintain logs**
   
   Keep logs for troubleshooting but rotate regularly.

3. **Use stable servers**
   
   Prefer round-robin servers (rotate.aprs2.net) for reliability.

4. **Plan for redundancy**
   
   Configure backup channels for critical deployments.

5. **Document your setup**
   
   Maintain notes on filter rationale and configuration decisions.

Igate Operation
---------------

1. **Understand local regulations**
   
   Verify you're allowed to operate an igate in your jurisdiction.

2. **Monitor igate activity**
   
   Review igate logs regularly to ensure proper operation.

3. **Conservative object gating**
   
   Use range limits to prevent unnecessary RF traffic.

4. **Coordinate with local community**
   
   Avoid duplicate igates in the same area.

Resources
=========

APRS-IS Network
---------------

* APRS-IS Servers: http://www.aprs-is.net/
* Server Status: http://www.aprs-is.net/aprsmap.aspx
* Tier 2 Servers: http://www.aprs2.net/

APRS Documentation
------------------

* APRS Protocol Reference: http://www.aprs.org/doc/APRS101.PDF
* APRS-IS Filter Guide: http://www.aprs-is.net/javAPRSFilter.aspx
* Polaric Server Documentation: https://polaricserver.readthedocs.io

Related Documentation
---------------------

* ``aprs-filters.rst`` - Detailed filter specification
* Polaric APRSD Web Interface - Channel management GUI
* Server configuration reference: http://aprs.no/dokuwiki/doku.php?id=aprd_config_reference

Support
-------

* GitHub Issues: https://github.com/PolaricServer/aprsd
* Polaric Server Website: http://aprs.no/polaricserver
* APRS Community: https://groups.io/g/aprsisce

Appendix: Server List
=====================

Global Tier 2 Servers
---------------------

These servers provide worldwide coverage with round-robin DNS:

* **rotate.aprs2.net** (14580) - Global rotation
* **noam.aprs2.net** (14580) - North America
* **soam.aprs2.net** (14580) - South America
* **euro.aprs2.net** (14580) - Europe
* **asia.aprs2.net** (14580) - Asia
* **aunz.aprs2.net** (14580) - Australia/New Zealand
* **africa.aprs2.net** (14580) - Africa

Regional Servers
----------------

Select a server close to your location for best performance:

**Europe:**

* aprs.no (Norway)
* aprs.fi (Finland)
* aprs.oe.oevsv.at (Austria)

**North America:**

* noam.aprs2.net (round-robin)
* rotate.aprs.net (legacy)

**Asia/Pacific:**

* asia.aprs2.net (round-robin)
* aunz.aprs2.net (Australia/NZ)

For a complete and current list, visit: http://www.aprs2.net/

Appendix: Q Construct Reference
================================

Q constructs are added by APRS-IS servers to indicate the source and verification status of packets:

**From RF to Internet (Igate Added):**

* ``qAR`` - Bidirectional igate (can receive from internet)
* ``qAO`` - Receive-only igate (cannot receive from internet)

**From Internet to RF:**

* ``qAI`` - Verified igate

**Client Connections:**

* ``qAC`` - Client connection (verified)
* ``qAX`` - Client connection (unverified)
* ``qAU`` - Unverified client
* ``qAo`` - Verified client
* ``qAZ`` - Verified authentication server

**Server Connections:**

* ``qAS`` - Server connection
* ``qAR`` - Server connection with RF capability

Polaric APRSD automatically adds the appropriate Q construct when gating packets to internet based on the ``igate.rfgate.allow`` setting.
