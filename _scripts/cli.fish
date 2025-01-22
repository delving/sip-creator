#!/usr/bin/env fish
set -gx MAVEN_OPTS "--add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED"
mvn -e exec:java -pl sip-app -Dexec.mainClass="eu.delving.sip.cli.SIPCLI" \
    -Dexec.args="process /home/kiivihal/PocketMapper/dcn-test/PocketMapper/work/anne-frank-stichting__2024_10_15_11_14.sip.zip"
