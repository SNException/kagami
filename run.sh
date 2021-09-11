#!/bin/bash

JVM_FLAGS="-ea -Xms2048m -Xmx2048m -XX:+AlwaysPreTouch -XX:+UseG1GC"
java.exe $JVM_FLAGS -cp bin Main test.kagami
