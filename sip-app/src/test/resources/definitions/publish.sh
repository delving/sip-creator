#!/bin/sh

TMP=/tmp
DEFINITIONS=$TMP/definitions

DIR=`pwd`

if [ $# -ne 2 ]
then
  echo "Usage: publish.sh schema filename"
  exit -1
fi

if [ -e "$1/$2" ]; then

  echo
  echo Going to publish file $2 for schema $1
  echo
  echo Updating definitions repository...
  echo
  if [ -d "$DEFINITIONS" ]; then
    cd $DEFINITIONS
    git pull
  else
    cd $TMP
    git clone git@github.com:delving/definitions.git
    cd $DEFINITIONS
  fi

  echo
  echo Pushing new changes
  echo
  cp $DIR/$1/$2 src/main/resources/definitions/$1
  git add src/main/resources/definitions/$1/$2
  git commit -m "Updating definition $1/$2"
  git push origin master

else
  echo "Could not find $1/$2!"
  exit 0
fi


