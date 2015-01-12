#!/bin/bash

if [ -z "$1" ]; then
  echo "Please specify which language_country suffix(es) you want to compile (e.g. en_US, fr, etc.)"
  exit 1
fi
mkdir -p _lib > /dev/null 2>&1
for lang in $*
do
  echo "Compiling $lang to package com.example.translations"
  msgfmt --verbose -f -r no.polaric.aprsd.i18n.messages --java2 -d _lib \
  -l ${lang} src/i18n/msgs/${lang}.po
done