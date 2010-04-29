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
 CLASSPATH = jcoord.jar:/usr/share/java/RXTXcomm.jar
INSTALLDIR = /usr/local/polaric-aprsd
JSPINSTALL = /var/www/test/bull/jsp
     JAVAC = javac -target 1.5
       JAR = jar


##################################################
##  things below should not be changed
##
##################################################
    LIBDIR = _lib
 JAVAFLAGS =
 PACKAGES  = core scala



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


.PHONY : core
core: 
	$(JAVAC) -d $(TDIR) $(JAVAFLAGS) src/*.java 

.PHONY : scala
scala:            
	scalac -d $(TDIR) -classpath $(LIBDIR):$(CLASSPATH) src/*.scala

	
clean:
	rm -R $(LIBDIR) ./*~ src/*~
