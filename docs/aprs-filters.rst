==================
APRS-IS Filters
==================

Overview
========

Polaric APRSD implements APRS-IS server-side filters to control which packets are delivered to clients. These filters follow the standard APRS-IS filter specification with some Polaric-specific extensions.

Filter Syntax
=============

Basic Syntax
------------

Filters consist of filter commands separated by spaces. Each filter command has the format::

    <command>[/<parameter1>[/<parameter2>...]]

Logical Operators
-----------------

Disjunction (OR)
~~~~~~~~~~~~~~~~

Filters separated by spaces represent a logical OR. The packet passes if ANY filter matches::

    a/59.0/10.0/60.0/11.0 b/LA*

This matches packets in the area OR packets with callsigns starting with "LA".

Conjunction (AND)
~~~~~~~~~~~~~~~~~

Prepend ``&`` to create a conjunction with the immediately preceding filter::

    a/59.0/10.0/60.0/11.0 &t/p

This matches packets that are BOTH in the area AND are position reports.

Multiple conjunctions can be chained::

    a/59.0/10.0/60.0/11.0 &t/p &b/LA*

This matches packets in the area AND are position reports AND have callsigns starting with "LA".

You can combine OR and AND operations::

    a/59.0/10.0/60.0/11.0 &t/p b/LA*

This matches: (packets in area AND position reports) OR (callsigns starting with "LA").

Exception (NOT)
~~~~~~~~~~~~~~~

Prepend ``-`` to create an exception filter. If an exception filter matches, the entire result is false, regardless of other filters::

    t/p -b/NOCALL*

This matches position reports EXCEPT those from callsigns starting with "NOCALL".

Exceptions can be combined with conjunctions::

    -t/x &p/LA

This means NOT(type x AND prefix LA).

Filter Types
============

All Packets (*)
---------------

**Polaric-specific filter**

Matches all packets::

    *

Area Filter (a)
---------------

Filter by geographic area defined by a rectangle.

**Syntax**::

    a/latN/lonW/latS/lonE

**Parameters:**

* ``latN`` - Northern latitude (decimal degrees)
* ``lonW`` - Western longitude (decimal degrees)
* ``latS`` - Southern latitude (decimal degrees)
* ``lonE`` - Eastern longitude (decimal degrees)

**Example**::

    a/59.0/10.0/60.0/11.0

Matches packets with positions between 59°N-60°N and 10°E-11°E.

Range Filter (r)
----------------

Filter by distance from a fixed point.

**Syntax**::

    r/lat/lon/dist

**Parameters:**

* ``lat`` - Center latitude (decimal degrees)
* ``lon`` - Center longitude (decimal degrees)
* ``dist`` - Radius in kilometers

**Example**::

    r/59.5/10.5/50

Matches packets within 50 km of position 59.5°N, 10.5°E.

My Range Filter (m)
-------------------

Filter by distance from the client's position.

**Syntax**::

    m/dist

**Parameters:**

* ``dist`` - Radius in kilometers

**Example**::

    m/100

Matches packets within 100 km of the authenticated client's position.

**Note:** The client's position must be known to the server (typically from their own position reports).

Friend Range Filter (f)
-----------------------

Filter by distance from a specified station's position.

**Syntax**::

    f/call/dist

**Parameters:**

* ``call`` - Callsign of the station to use as reference
* ``dist`` - Radius in kilometers

**Example**::

    f/LA1B-5/50

Matches packets within 50 km of station LA1B-5's position.

Type Filter (t)
---------------

Filter by APRS packet type.

**Syntax**::

    t/types[/call/dist]

**Parameters:**

* ``types`` - String of type characters (see below)
* ``call`` - Optional callsign for range filtering
* ``dist`` - Optional distance in kilometers

**Type Characters:**

* ``p`` - Position reports (!, =, @, /, ', \`)
* ``o`` - Objects (;)
* ``i`` - Items ())
* ``m`` - Messages (:)
* ``M`` - Telemetry (special case: message to self)
* ``q`` - Queries (?)
* ``s`` - Status (>)
* ``t`` - Telemetry (T)
* ``w`` - Weather (_, #, \*)
* ``u`` - User-defined ({)

**Examples**::

    t/p

Matches all position reports.

::

    t/pom

Matches position reports, objects, and messages.

::

    t/p/LA1B/100

Matches position reports within 100 km of station LA1B.

Prefix Filter (p)
-----------------

Filter by callsign prefix.

**Syntax**::

    p/prefix1[/prefix2/...]

**Parameters:**

* ``prefix`` - One or more callsign prefixes (case-insensitive)

**Example**::

    p/LA/OH

Matches callsigns starting with "LA" or "OH".

Budlist Filter (b)
------------------

Filter by callsign patterns using wildcards.

**Syntax**::

    b/pattern1[/pattern2/...]

**Parameters:**

* ``pattern`` - Callsign pattern with wildcards
* ``*`` - Matches any sequence of characters
* ``?`` - Matches any single character

**Examples**::

    b/LA1B*

Matches LA1B, LA1B-1, LA1B-15, etc.

::

    b/LA?B

Matches LA1B, LA2B, LASB, etc.

::

    b/LA*/OH*

Matches callsigns starting with "LA" or "OH".

Unproto Filter (u)
------------------

Filter by APRS destination (TO) field using wildcards.

**Syntax**::

    u/pattern1[/pattern2/...]

**Parameters:**

* ``pattern`` - Destination pattern with wildcards (* and ?)

**Example**::

    u/APRS*

Matches packets with destination starting with "APRS".

Digipeater Filter (d)
---------------------

Filter by digipeater used in the path. Only matches digipeaters that have been used (marked with *).

**Syntax**::

    d/pattern1[/pattern2/...]

**Parameters:**

* ``pattern`` - Digipeater pattern with wildcards (* and ?)

**Example**::

    d/WIDE*

Matches packets digipeated through WIDE1-1*, WIDE2-1*, etc.

Entry Station Filter (e)
------------------------

Filter by entry igate callsign (from Q construct).

**Syntax**::

    e/pattern1[/pattern2/...]

**Parameters:**

* ``pattern`` - Igate callsign pattern with wildcards (* and ?)

**Example**::

    e/LA1B*

Matches packets gated by LA1B, LA1B-1, etc.

Object Filter (o)
-----------------

Filter by object or item name using wildcards.

**Syntax**::

    o/pattern1[/pattern2/...]

**Parameters:**

* ``pattern`` - Object/item name pattern with wildcards (* and ?)

**Example**::

    o/SEARCH*

Matches objects and items with names starting with "SEARCH".

::

    o/*AID*

Matches objects/items containing "AID" in the name.

Strict Object Filter (os)
--------------------------

Filter by object name (excluding items) using wildcards.

**Syntax**::

    os/pattern1[/pattern2/...]

**Parameters:**

* ``pattern`` - Object name pattern with wildcards (* and ?)

**Example**::

    os/FIRE*

Matches only objects (not items) with names starting with "FIRE".

Symbol Filter (s)
-----------------

Filter by APRS symbol.

**Syntax**::

    s/primary[/alternate[/overlay]]

**Parameters:**

* ``primary`` - Primary symbol table characters
* ``alternate`` - Alternate symbol table characters (optional)
* ``overlay`` - Overlay characters (0-9, A-Z) (optional)

**Example**::

    s/->

Matches symbols: car (-) and arrow (>).

::

    s//\\

Matches alternate table symbols: house (\\).

::

    s//#/A

Matches alternate table symbols with overlays: A# (overlay A, # symbol).

Group Message Filter (g)
-------------------------

Filter by message recipient (TO field) using wildcards.

**Syntax**::

    g/pattern1[/pattern2/...]

**Parameters:**

* ``pattern`` - Recipient pattern with wildcards (* and ?)

**Example**::

    g/ALL*

Matches messages to ALL, ALLUSA, etc.

::

    g/NWS*

Matches weather alert messages.

Q Construct Filter (q)
----------------------

Filter by Q construct (APRS-IS routing information).

**Syntax**::

    q/pattern1[/pattern2/...]

**Parameters:**

* ``pattern`` - Q construct pattern with wildcards (* and ?)

**Q Constructs:**

* ``qAC`` - Packet received from client over APRS-IS
* ``qAX`` - Packet received from client over APRS-IS with no verification
* ``qAU`` - Packet received from unverified client over APRS-IS
* ``qAo`` - Packet received from verified client over APRS-IS
* ``qAO`` - Packet received from verified client with object
* ``qAS`` - Packet received from server
* ``qAR`` - Packet received from bidirectional connection
* ``qAZ`` - Packet from verified authentication server
* ``qAI`` - Packet from verified igate

**Example**::

    q/qAC

Matches packets from APRS-IS clients.

::

    q/qA?

Matches all Q constructs starting with qA.

Channel Filter (C)
------------------

**Polaric-specific filter**

Filter by input channel identifier. Used internally to filter packets by their source channel.

**Syntax**::

    C/channel1[/channel2/...]

**Parameters:**

* ``channel`` - Internal channel identifier

**Example**::

    C/APRSIS/TNC1

Matches packets received from channels named "APRSIS" or "TNC1".

Complex Filter Examples
=======================

Position Reports in Area
------------------------

Get all position reports in a specific area::

    a/59.0/10.0/60.0/11.0 &t/p

Position Reports Except One Station
------------------------------------

Get position reports except from a specific station::

    t/p -b/NOCALL

Objects Within Range of Station
--------------------------------

Get objects within 50 km of a station::

    t/o/LA1B/50

Weather Reports in Area
-----------------------

Get weather reports in a specific area::

    a/59.0/10.0/60.0/11.0 &t/w

All Traffic for Specific Stations
----------------------------------

Get all packets from specific callsigns::

    b/LA1B*/OH2*

Position and Object Reports with Prefix
----------------------------------------

Get positions and objects from stations with specific prefixes::

    t/po &p/LA

Messages and Objects, Excluding Test Callsigns
-----------------------------------------------

Get messages and objects, but exclude test stations::

    t/mo -b/TEST*

Implementation Notes
====================

Case Sensitivity
----------------

* Callsign patterns are converted to uppercase for matching
* Pattern matching is case-insensitive

Wildcard Conversion
-------------------

Wildcards in patterns are converted to regular expressions:

* ``*`` becomes ``(.*)`` (matches any sequence)
* ``?`` becomes ``.`` (matches any single character)

Filter Evaluation Order
-----------------------

1. Exception filters (``-``) are evaluated first
2. If any exception filter matches, the result is FALSE
3. Positive filters are evaluated next
4. If any positive filter matches, the result is TRUE
5. If no filters match, the result is FALSE

Performance Considerations
--------------------------

* Range filters require position lookup, which may impact performance
* Wildcard patterns are pre-compiled to regular expressions for efficiency
* Area filters use simple coordinate comparison

See Also
========

* Standard APRS-IS Filter Specification: https://www.aprs-is.net/javAPRSFilter.aspx
* APRS Protocol Specification: http://www.aprs.org/doc/APRS101.PDF
