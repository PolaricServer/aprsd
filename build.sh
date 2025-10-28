#!/bin/bash

rm lib/*
cd src/filter
jflex filters.lex
byaccj -Jpackage=no.polaric.aprsd.filter filters.y
cd ../..
mvn clean dependency:copy-dependencies -DoutputDirectory=lib -DincludeScope=runtime package 
