 
#!/bin/bash
echo "Making src/i18n/msgs/messages.pot from all java/scala sources"
find . -name '*.java' > .files-to-extract
find . -name '*.scala' >> .files-to-extract
xgettext -L Java -i --no-wrap --from-code utf-8 -F -f .files-to-extract \
-ktrc:1c,2 -ktrcw:1c,2 -ktrcf:1c,2 -ktrcfw:1c,2 -ktr -ktrj -ktrfj \
-ktrw -ktrf -ktrfw --force-po --omit-header -o src/i18n/msgs/messages.pot
rm .files-to-extract