#!/bin/bash

# Add commit hash to sip-app.properties
gitcommithash=$(git rev-parse HEAD)
gitcommithashshort=$(git rev-parse --short=8 HEAD)
buildinfo="Built on $(date) by $(whoami)@$(hostname)"
echo "git-commit-hash=${gitcommithash}" >> sip-app/src/main/resources/sip-app.properties
echo "git-commit-hash-short=${gitcommithashshort}" >> sip-app/src/main/resources/sip-app.properties
echo "build-info=${buildinfo}" >> sip-app/src/main/resources/sip-app.properties
