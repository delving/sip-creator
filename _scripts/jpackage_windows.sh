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

# Convert .png logo to .ico
cp input/sip-creator-logo.png ./
# Using ImageMagick 7 - this worked on Windows x64 but not on Windows aarch64
magick sip-creator-logo.png -define 'icon:auto-resize=512,256,128,64,48,32,24,16' sip-creator-logo.ico

echo "Running jpackage (app-image)..."
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
--java-options '-splash:$APPDIR\sip-creator-logo.png' \
--icon sip-creator-logo.ico
7z a "SIP-Creator-$version.zip" '.\SIP-Creator\*'

echo "Running jpackage (msi)..."
# If msi build isn't possible this command may fail
jpackage \
--type msi \
--input input \
--name SIP-Creator \
--main-class eu.delving.sip.Application \
--main-jar "$mainjar" \
--app-version "$version" \
--vendor "Delving BV" \
--description "Delving SIP-Creator" \
--copyright "Copyright 2011-$yearnow Delving BV" \
--java-options '-splash:$APPDIR\sip-creator-logo.png' \
--icon sip-creator-logo.ico \
--win-dir-chooser \
--win-help-url "https://github.com/delving/sip-creator" \
--win-update-url "https://download.delving.io/build/sip-creator/releases/" \
--win-menu \
--win-menu-group "Delving" \
--win-per-user-install \
--win-shortcut \
--win-shortcut-prompt \
--win-upgrade-uuid "ac13c302-6a35-43f5-aeed-2dafe67bfd5a"

echo "Done"
