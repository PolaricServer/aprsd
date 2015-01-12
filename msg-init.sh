#!/bin/bash 

if [ -z "$1" ]; then
  echo "Please specify which language_country suffix(es) you want to merge"
  exit 1
fi
for lang in $*
do
  echo "Initializing template file for $lang"
  if [ -f src/i18n/msgs/$lang.po ]; then
    echo "src/i18n/msgs/$lang.po exists! You probably wanted merge"
    exit
  fi
  msginit --no-translator -l $lang --no-wrap -o src/i18n/msgs/${lang}.po -i src/i18n/msgs/messages.pot
done