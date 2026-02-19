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

# Source: https://stackoverflow.com/a/44509460
# by: https://stackoverflow.com/users/499581/lll
# "user contributions licensed under CC BY-SA"
# Changes made as noted below to handle source images which are not 1024x1024 pixels.
mkicns() {
    if [[ -z "$*" ]] || [[ "${*##*.}" != "png" ]]; then
        echo "Input file invalid"
    else
        filename="${1%.*}"
        mkdir "$filename".iconset
        for i in 16 32 128 256 ; do
            n=$(( i * 2 ))
            sips -z $i $i "$1" --out "$filename".iconset/icon_${i}x${i}.png
            sips -z $n $n "$1" --out "$filename".iconset/icon_${i}x${i}@2x.png
            [[ $n -eq 512 ]] && \
            sips -z $n $n "$1" --out "$filename".iconset/icon_${n}x${n}.png && \
            # added && \ above and line:
            sips -z 1024 1024 "$1" --out "$filename".iconset/icon_${n}x${n}@2x.png
            (( i++ ))
        done
        # commented out: cp "$1" "$filename".iconset/icon_512x512@2x.png
        iconutil -c icns "$filename".iconset
        rm -r "$filename".iconset
    fi
}

mkicns input/sip-creator-logo.png
mv input/sip-creator-logo.icns ./

echo "Running jpackage..."
jpackage \
--type dmg \
--input input \
--name SIP-Creator \
--main-class eu.delving.sip.Application \
--main-jar "$mainjar" \
--app-version "$version" \
--vendor "Delving BV" \
--description "Delving SIP-Creator" \
--copyright "Copyright 2011-$yearnow Delving BV" \
--java-options "-Dapple.awt.application.appearance=system" \
--java-options "-Dsun.java2d.metal=false" \
--java-options '-splash:$APPDIR/sip-creator-logo.png' \
--mac-package-identifier eu.delving.sip \
--mac-package-name "SIP-Creator" \
--icon sip-creator-logo.icns

echo "Done"
