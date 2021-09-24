#!/bin/bash

src_dir=src
out_dir=bin
compile_flags="-J-Xms2048m -J-Xmx2048m -J-XX:+UseG1GC -Xdiags:verbose -Xlint:all -Xmaxerrs 5 -encoding UTF8 --release 17 -g"

entry_point=Main
launch_file=run.sh
jvm_flags="-ea -Xms2048m -Xmx2048m -XX:+AlwaysPreTouch -XX:+UseG1GC -Xmixed"

if test -d $out_dir; then rm -r $out_dir;mkdir $out_dir; fi
if test -f $launch_file; then rm $launch_file; fi

find $src_dir -type f > sources.txt
javac.exe $compile_flags -d $out_dir -sourcepath $src_dir @sources.txt

if [ $? -eq 0 ]
then
  echo "Build successful"
  echo "java.exe $jvm_flags -cp $out_dir $entry_point" > $launch_file
else
  echo "Build failed"
fi

rm sources.txt
