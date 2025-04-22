#!/bin/bash

# See: https://www.devdungeon.com/content/use-jpackage-create-native-java-app-installers
# See for jpackage $ variables: https://bugs.openjdk.org/browse/JDK-8231910

cd sip-app/target

mainjar=$(ls -1 sip-app-*-exejar.jar | head -n 1 | xargs -n 1 basename)
version=$(echo "$mainjar" | sed -r 's/sip\-app\-([0-9\.]+)\-.*/\1/')
yearnow=$(date '+%Y')
echo "jar: $mainjar"
echo "version (x.y.z digits only): $version"
echo "year (using 'date'): $yearnow"

# In input dir, put the current sip-app-*.jar and sip-creator-logo.png
mkdir input
cp "$mainjar" input/
cp "../src/main/resources/sip-creator-logo.png" input/

# Use the logo .png as icon without conversion
cp input/sip-creator-logo.png ./

echo "Running jpackage..."
jpackage \
--type app-image \
--input input \
--name SIP-Creator \
--main-class eu.delving.sip.Application \
--main-jar "$mainjar" \
--app-version "$version" \
--vendor "Delving BV" \
--description "Delving SIP-Creator" \
--copyright "Copyright 2011-$yearnow Delving BV" \
--java-options '-splash:$APPDIR/sip-creator-logo.png' \
--icon sip-creator-logo.png
mv "SIP-Creator" "sip-creator-$version"
tar cJf "sip-creator-$version.tar.xz" "sip-creator-$version"

echo "Done"
