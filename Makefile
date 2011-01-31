##########################################################################
## Change macros below according to your environment and your needs
##
## CLASSDIR if you want to compile to a class directory instead of generating
##          a jar, by using the 'test' target, you may set the directory here.
##
## CLASSPATH Specify where to find the servlet library and the java-cup
##           library. For Debian Linux platform you wont need to change
##           this.
##
## JAVAC: Java compiler
## JAR:   Jar archiver
##########################################################################
  CLASSDIR = classes
 CLASSPATH = jcoord.jar:utf8-with-fallback.jar:/usr/share/java/RXTXcomm.jar:simple.jar
INSTALLDIR = /usr/local/polaric-aprsd/lib
JSPINSTALL = /var/www/test/bull/jsp
     JAVAC = javac -target 1.5
       JAR = jar


##################################################
##  things below should not be changed
##
##################################################
    LIBDIR = _lib
 JAVAFLAGS =
 PACKAGES  = core httpd scala aprsd 



all: aprs

install: $(INSTALLDIR)/aprs.jar

$(INSTALLDIR)/aprs.jar: aprs.jar
	cp aprs.jar $(INSTALLDIR)/aprs.jar

	
aprs: $(LIBDIR)
	@make TDIR=$(LIBDIR) CLASSPATH=$(LIBDIR):$(CLASSPATH) compile     
	cd $(LIBDIR);jar cvf ../aprs.jar *;cd ..


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
	rm -R $(LIBDIR) ./*~ src/*~ src/httpd/*~
