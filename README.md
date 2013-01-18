# Polaric APRSD

The "Polaric Server" is mainly a web based service to present APRS 
tracking information on maps and where the information is updated at real-
time. It is originally targeted for use by radio amateurs in voluntary search
and rescue service in Norway. It consists of a web application and a server 
program (APRS daemon). 
 
The APRS daemon gathers data from a TNC or APRS-IS or both. It can present 
and manipulate the information through a simple HTTP service. The daemon can 
also be set up as an igate (internet gateway) and can be installed 
independently of the web app. 

More documentation on the project can be found here: 
http://aprs.no/dokuwiki/doku.php?id=polaricserver

## System requirements

Linux/Java platform (tested with Debian/Ubuntu) with
* Java Runtime environment version 6 or later. 
* Scala library version 2.7.7 (sorry, there are some problems with 2.8 or later). 
* jsvc
* librxtx-java (if using serial port for communication with TNC or GPS).

We support automatic installation packages for Debian/Ubuntu. 
It shouldn't be too hard to port it to e.g. Windows if anyone wants to 
do the job. 

## Installation

We provide Debian files for the stable branch (and soon the development branch 
as well). For information on getting started on a Debian/Ubuntu platform please 
see: http://aprs.no/dokuwiki/doku.php?id=installation

More documentation on the project can be found here: 
http://aprs.no/dokuwiki/doku.php?id=polaricserver

## Building from source 

Build from the source is done by a plain old makefile. Yes I know :)
Maybe I move to Maven a little later. Setup for generating Debian
packages is included. You may use the 'debuild' command.

You will need JDK (Oracle or OpenJDK) version 6 or later, the Scala
programming language version 2.7.7 (scala and scala-library) and 
librxtx-java. 
