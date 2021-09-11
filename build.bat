@echo off

set SRC_DIR=src
set OUT_DIR=bin
set LIBS=""
set COMPILE_FLAGS=-J-Xms2048m -J-Xmx2048m -J-XX:+UseG1GC -Xdiags:verbose -Xlint:all -Xmaxerrs 5 -encoding UTF8 --release 11 -g

if exist %OUT_DIR% (
    rmdir /s /q %OUT_DIR%
    mkdir %OUT_DIR%
)

dir /s /b %SRC_DIR% > sources.txt
javac.exe %COMPILE_FLAGS% -classpath %LIBS% -d %OUT_DIR% -sourcepath %SRC_DIR% @sources.txt

if %ERRORLEVEL% == 0 (
    echo Build successful
) else (
    echo Build failed
)

del sources.txt
