# Polaric APRSD

The "Polaric Server" is mainly a web based service to present APRS tracking information on maps and where the information is updated in real-time. It is originally targeted for use by radio amateurs in voluntary search and rescue service in Norway. It consists of a web application and a server 
program (APRS daemon). 
 
The APRS daemon gets data from a TNC or APRS-IS or a combination. It can present 
and manipulate the information through a simple HTTP service. The daemon can 
also be set up as an igate (internet gateway) and can be installed independently of the web app. 

More documentation on the project can be found here: 
http://aprs.no/polaricserver

## System requirements

Linux/Java platform (tested with Debian/Ubuntu) with
* Java Runtime environment version 7 or later. 
* Scala library version 2.8 or later. 
* jsvc.
* librxtx-java (if using serial port for communication with TNC or GPS).

We support automatic installation packages for Debian Linux or derivatives. 
It shouldn't be too hard to port it to e.g. Windows if anyone wants to do the job. 

We also use the following external libraries. jar files are included: 
* Jcoord, with some small modifications. Compiled jar and source included.
* utf8-with-fallback with some small modifications. Compiled jar and source included. 
* 'Simple' framework. 

## Installation

We provide Debian packages. For information on getting started on a Debian/Ubuntu/Mint platform please 
see: http://aprs.no/dokuwiki/?id=installation

More documentation on the project can be found here: 
http://aprs.no/dokuwiki/polaricserver

The polaric-webconfig-plugin is recommended. 

## Building from source 

Build from the source is done by a plain old makefile. Yes I know :)
Maybe I move to something else a little later. Setup for generating Debian
packages is included. You may use the 'debuild' command.

You will need JDK (Oracle or OpenJDK) version 7 or later, the Scala
programming language version 2.8 or later (scala and scala-library) and 
librxtx-java. 
