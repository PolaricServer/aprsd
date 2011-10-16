##########################################################################
## Change macros below according to your environment and your needs
##
## JAVAC: Java compiler
## JAR:   Jar archiver
##########################################################################

  CLASSDIR = classes
 CLASSPATH = jcoord-polaric.jar:utf8-with-fallback-polaric.jar:/usr/share/java/RXTXcomm.jar:simple.jar
# INSTALLDIR = /usr/local/polaric-aprsd
     JAVAC = javac -target 1.6
       JAR = jar

# Review (and if necessary) change these if you are going to 
# install by using this makefile

   INSTALL_JAR = $(DESTDIR)/usr/share/java
   INSTALL_BIN = $(DESTDIR)/usr/bin
INSTALL_CONFIG = $(DESTDIR)/etc/polaric-aprsd
   INSTALL_WEB = $(DESTDIR)/usr/share/polaric
 INSTALL_DATA  = $(DESTDIR)/var/lib/polaric
   INSTALL_LOG = $(DESTDIR)/var/log/polaric


##################################################
##  things below should not be changed
##
##################################################
    LIBDIR = _lib
 JAVAFLAGS =
 PACKAGES  = core httpd scala aprsd



all: aprs

install: polaric-aprsd.jar
	install -d $(INSTALL_CONFIG)
	install -d $(INSTALL_BIN)
	install -d $(INSTALL_JAR)
	install -d $(INSTALL_WEB)/icons $(INSTALL_WEB)/icons/signs $(INSTALL_WEB)/dicons
	install -d $(INSTALL_DATA)
	install -d $(INSTALL_LOG)
	install -m 644 server.ini $(INSTALL_CONFIG)
	install -m 644 symbols $(INSTALL_CONFIG)
	install -m 644 trailcolours $(INSTALL_CONFIG)
	install -m 644 *.jar $(INSTALL_JAR)
	install -m 644 icons/*.png icons/*.gif $(INSTALL_WEB)/icons
	install -m 644 icons/signs/*.png icons/signs/*.gif $(INSTALL_WEB)/icons/signs
	install -m 644 dicons/*.png $(INSTALL_WEB)/dicons
	install -m 644 style.css $(INSTALL_WEB)


aprs: $(LIBDIR)
	@make TDIR=$(LIBDIR) CLASSPATH=$(LIBDIR):$(CLASSPATH) compile     
	cd $(LIBDIR);jar cvf ../polaric-aprsd.jar *;cd ..


compile: $(PACKAGES)
	

$(CLASSDIR): 
	mkdir $(CLASSDIR)
	
		
$(LIBDIR):
	mkdir $(LIBDIR)


.PHONY : aprsd
aprsd: 
	$(JAVAC) -d $(TDIR) $(JAVAFLAGS) src/aprsd/*.java 
	
	
.PHONY : core
core: 
	$(JAVAC) -d $(TDIR) $(JAVAFLAGS) src/*.java 

.PHONY : httpd
httpd: 
	$(JAVAC) -d $(TDIR) $(JAVAFLAGS) src/httpd/*.java 
	
.PHONY : scala
scala:            
	scalac -d $(TDIR) -classpath $(LIBDIR):$(CLASSPATH) src/httpd/*.scala

	
clean:
	@if [ -e ${LIBDIR} ]; then \
		  rm -Rf $(LIBDIR); \
	fi 
	rm -f ./*~ src/*~ src/httpd/*~
