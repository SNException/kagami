#!/bin/bash

SRC_DIR=src
OUT_DIR=bin
COMPILE_FLAGS="-J-Xms2048m -J-Xmx2048m -J-XX:+UseG1GC -Xdiags:verbose -Xlint:all -Xmaxerrs 5 -encoding UTF8 --release 17 -g"

if test -d $OUT_DIR; then rm -r $OUT_DIR;mkdir $OUT_DIR; fi

find $SRC_DIR -type f > sources.txt
javac.exe $COMPILE_FLAGS -d $OUT_DIR -sourcepath $SRC_DIR @sources.txt

if [ $? -eq 0 ]
then
  echo "Build successful"
else
  echo "Build failed"
fi

rm sources.txt
