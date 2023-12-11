# Polaric APRSD

The "Polaric Server" is mainly a web based service to present APRS tracking information on maps and where 
the information is updated in real-time. It is originally targeted for use by radio amateurs in voluntary 
search and rescue service in Norway. It consists of a web application and a server program (APRS daemon). 
 
The APRS daemon gets data from a TNC or APRS-IS or a combination. It can present 
and manipulate the information through a simple HTTP service (REST API). The daemon can 
also be set up as an igate (internet gateway) and can be installed and run independently 
of the web app. It has its own webserver. 

It is recommended to combine it with polaric-webapp2. It supports CORS to allow it to be used 
with a polaric-webapp2 on another location.

http://aprs.no runs a recent version of this software. More documentation on the project can be found here: 
http://aprs.no/polaricserver or https://polaricserver.readthedocs.io

## System requirements

Linux/Java platform (tested with Debian/Ubuntu) with
* Java Runtime environment version 17 or later. 
* jsvc.
* librxtx-java (if using serial port for communication with TNC or GPS).
* libjackson2 (for JSON processing)
* libcommons-codec

We support deb installation packages for Linux distros that can use it (Debian, Ubuntu, ..) 
It shouldn't be too hard to port it to e.g. Windows if anyone wants to do the job. 

We also use the following external libraries. jar files are included: 
* Jetty and Spark framework (HTTP server)
* pac4j framework (authentication/authorization)
* nano-cuckoo with lz4
* jMDNS


## Installation

We provide deb (Debian/Ubuntu..) packages. For information on getting started on a Debian based platform please 
see: https://polaricserver.readthedocs.io/en/latest/gettingstarted.html

Documentation on the project can be found here: 
http://aprs.no/polaricserver

To configure a server, use the webapp2. Some command-line scripts are also available: 
polaric-setcall, polaric-password, 

For first-time login use account: username=admin, password=polaric. Remember to change the password at your fist
convenience. 

Se also: [Examples of how to use it with Docker](https://github.com/PolaricServer/Dockerfiles).


## Building from source 

Build from the source is done by a plain old makefile. Yes I know :)
Maybe I move to something else (Maven, Gradle) later. Setup for generating deb
packages is included. You may use the 'debuild' command.

You will need JDK (Oracle or OpenJDK) version 17 or later. 
