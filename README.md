# Polaric APRSD 4

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

## Towards version 4
Work is in progress towards a version 4 with major changes to how it is built and to the structure of the source code. it is my hope that the codebase now will be easier to maintain and develop further. 
* We use maven for building
* A part of the codebase, has been factored out to a separate codebase: Arctic-Core. It is a server-framework (mainly contains the webserver stuff). It is the hope that this can be useful in creating also other server-applications.
* The webserver part (now Arcic-Core) has moved from spark-java to *javalin*. 
* The codebase is also somewhat reorganised. More source-files are moved to subpackages and subdirectories.

The functionality of this branch is currently similar to the *main*-branch. Now we need to do testing and debugging and make sure that everything works. Then, when it is considered stable enough, it will become the new *main*-branch (merge). There may be one more release from the current *main*-branch (bugfixes, etc)

Interested? Stay tuned.


