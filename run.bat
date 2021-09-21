@echo off 
java.exe -ea -Xms2048m -Xmx2048m -XX:+AlwaysPreTouch -XX:+UseG1GC -cp "";bin Main 
