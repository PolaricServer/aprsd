##########################################################################
## Change macros below according to your environment and your needs
##
## JAVAC: Java compiler
## JAR:   Jar archiver
##########################################################################

   CLASSDIR = classes
      CODEC = /usr/share/java/commons-codec.jar
    JACKSON = /usr/share/java/jackson-core.jar:/usr/share/java/jackson-databind.jar:/usr/share/java/jackson-annotations.jar
  CLASSPATH = lib/jcoord-polaric.jar:lib/utf8-with-fallback-polaric.jar:/usr/share/java/RXTXcomm.jar:lib/jetty-polaric.jar:/usr/share/java/sl4j-api.jar:/usr/share/java/sl4j-simple.jar:lib/spark-core-polaric.jar:lib/pac4j-core-polaric.jar:lib/pac4j-http-polaric.jar:lib/pac4j-javaee-polaric.jar:lib/spark-pac4j-polaric.jar:lib/jmdns-polaric.jar:lib/nano-cuckoo-polaric.jar:lib/fst-polaric.jar:lib/rtree2-polaric.jar:$(JACKSON):$(CODEC)
      JAVAC = javac -source 11 -target 11
       YACC = byaccj
        LEX = jflex
        JAR = jar

# Review (and if necessary) change these if you are going to 
# install by using this makefile

   INSTALL_JAR = $(DESTDIR)/usr/share/java
   INSTALL_BIN = $(DESTDIR)/usr/bin
INSTALL_CONFIG = $(DESTDIR)/etc/polaric-aprsd
   INSTALL_WEB = $(DESTDIR)/usr/share/polaric
 INSTALL_DATA  = $(DESTDIR)/var/lib/polaric
 INSTALL_BACKUP= $(INSTALL_DATA)/backup
   INSTALL_LOG = $(DESTDIR)/var/log/polaric
  INSTALL_SUDO = $(DESTDIR)/etc/sudoers.d
INSTALL_PLUGDIR= $(INSTALL_CONFIG)/config.d
INSTALL_SCPLUG = $(INSTALL_CONFIG)/script-conf.d
 INSTALL_SCDIR = $(INSTALL_CONFIG)/scripts


##################################################
##  things below should not be changed
##
##################################################
    LIBDIR = _lib
 JAVAFLAGS =
 PACKAGES  = core util channels httpd scala aprsd
 LANGUAGES = no


all: aprs

install: polaric-aprsd.jar
	install -d $(INSTALL_CONFIG)
	install -d $(INSTALL_PLUGDIR)
	install -d $(INSTALL_SCPLUG)
	install -d $(INSTALL_SCDIR)
	install -d $(INSTALL_BIN)
	install -d $(INSTALL_JAR)
	install -d $(INSTALL_WEB)/images
	install -d $(INSTALL_DATA)
	install -d $(INSTALL_BACKUP)
	install -d $(INSTALL_SUDO)
	install -m 755 -d $(INSTALL_LOG)
	install -m 644 passwd $(INSTALL_CONFIG)
	install -m 644 groups $(INSTALL_CONFIG)
	install -m 644 server.ini $(INSTALL_CONFIG)
	install -m 644 scripts.conf $(INSTALL_CONFIG)
	install -m 644 symbols $(INSTALL_CONFIG)
	install -m 644 trailcolours $(INSTALL_CONFIG)
	install -m 644 view.profiles $(INSTALL_CONFIG)
	install -m 644 lib/*.jar $(INSTALL_JAR)
	install -m 644 polaric-aprsd.jar $(INSTALL_JAR)
	install -m 644 images/* $(INSTALL_WEB)/images
	install -m 644 style.css $(INSTALL_WEB)
	install -m 755 scripts/* $(INSTALL_BIN)
	cp sudoers.d $(INSTALL_SUDO)/polaric-aprsd
	chmod 644 $(INSTALL_SUDO)/polaric-aprsd
	

aprs: $(LIBDIR)
	@make TDIR=$(LIBDIR) CLASSPATH=$(LIBDIR):$(CLASSPATH) compile     
	cd $(LIBDIR);jar cvf  ../polaric-aprsd.jar *;cd ..


compile: $(PACKAGES)
	

$(CLASSDIR): 
	mkdir $(CLASSDIR)
	
		
$(LIBDIR):
	mkdir $(LIBDIR)

	
src/filter/Lexer.java : src/filter/filters.lex src/filter/filters.y
	cd src/filter;$(LEX) filters.lex
	
src/filter/Parser.java : src/filter/filters.y
	cd src/filter;$(YACC) -Jpackage=no.polaric.aprsd.filter filters.y
	

.PHONY : util
util: 
	$(JAVAC) -d $(TDIR) $(JAVAFLAGS) src/util/*.java 
	
	
.PHONY : core
core: util src/filter/Parser.java src/filter/Lexer.java
	$(JAVAC) -d $(TDIR) $(JAVAFLAGS) src/*.java  src/filter/*.java
	
	
.PHONY : aprsd
aprsd: 
	$(JAVAC) -d $(TDIR) $(JAVAFLAGS) src/aprsd/*.java 
	

.PHONY : channels
channels: 
	$(JAVAC) -d $(TDIR) $(JAVAFLAGS) src/channels/*.java 
		
	
#filter: core  src/filter/Parser.java src/filter/Lexer.java
#	$(JAVAC) -d $(TDIR) $(JAVAFLAGS) src/filter/*.java

	
.PHONY : httpd
httpd: core
	$(JAVAC) -d $(TDIR) $(JAVAFLAGS) src/httpd/auth/*.java  src/httpd/*.java src/httpd/restapi/*.java
	
	
.PHONY : scala
scala: core           
	scalac -d $(TDIR) -classpath $(LIBDIR):$(CLASSPATH) src/httpd/html/*.scala
	scalac -d $(TDIR) -classpath $(LIBDIR):$(CLASSPATH) src/httpd/webconfig/*.scala

	
clean:
	@if [ -e ${LIBDIR} ]; then \
		  rm -Rf $(LIBDIR); \
	fi 
	rm -f ./*~ src/*~ src/httpd/*~
