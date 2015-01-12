#!/bin/bash

if [ -z "$1" ]; then
   echo "Please specify which language_country suffix(es) you want to merge"
   exit 1
fi

for lang in $*
do
  echo "Merging template file into $lang"
  msgmerge -i -F --no-wrap -o src/i18n/msgs/${lang}.po src/i18n/msgs/${lang}.po src/i18n/msgs/messages.pot
done
