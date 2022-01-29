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
http://aprs.no/polaricserver

## System requirements

Linux/Java platform (tested with Debian/Ubuntu) with
* Java Runtime environment version 11 or later. 
* Scala library version 2.11 or later. You will also need scala-xml
  and scala-parser-combinators packages.
* jsvc.
* librxtx-java (if using serial port for communication with TNC or GPS).
* libjackson2 (for JSON processing)
* libcommons-codec
* libgettext-commons

We support automatic installation packages for Debian Linux or derivatives. 
It shouldn't be too hard to port it to e.g. Windows if anyone wants to do the job. 

We also use the following external libraries. jar files are included: 
* Jcoord, with some small modifications. Compiled jar and source included.
* utf8-with-fallback with some small modifications. Compiled jar and source included. 
* Jetty and Spark framework (HTTP server)
* pac4j framework (authentication/authorization)


## Installation

We provide deb (Debian/Ubuntu..) packages. For information on getting started on a Debian based platform please 
see: http://aprs.no/dokuwiki/doku.php/install.dev

More documentation on the project can be found here: 
http://aprs.no/polaricserver

To configure a standalone server, point your browser to: 
http://localhost:8081/config_menu

username=admin, password=polaric. Use the command polaric-password
to change the password and add new users. 

Se also: [Examples of how to use it with Docker](https://github.com/PolaricServer/Dockerfiles).

## Building from source 

Build from the source is done by a plain old makefile. Yes I know :)
Maybe I move to something else a little later. Setup for generating deb
packages is included. You may use the 'debuild' command.

You will need JDK (Oracle or OpenJDK) version 11 or later, the Scala
programming language version 2.11.12 or later (scala, scala-library scala-xml
and scala-parser-combinators packages) and librxtx-java, byacc-j, jflex, gettext, libgettext-commons-java, libjackson2-databind-java, libjackson-annotations-java.
