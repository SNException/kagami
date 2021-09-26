@echo off

set src_dir=src
set out_dir=bin
set libs=""
set compile_flags=-J-Xms2048m -J-Xmx2048m -J-XX:+UseG1GC -Xdiags:verbose -Xlint:all -Xmaxerrs 5 -encoding UTF8 --release 17 -g

set entry_point=Main
set jvm_flags=-ea -Xms2048m -Xmx2048m -XX:+AlwaysPreTouch -XX:+UseG1GC -Xmixed

IF "%1"==""    goto build
IF "%1"=="run" goto run

:build
if exist %out_dir% (
    rmdir /s /q %out_dir%
    mkdir %out_dir%
)

dir /s /b %src_dir%\*.java > sources.txt
"%JAVA_HOME%\bin\javac.exe" %compile_flags% -classpath %libs% -d %out_dir% -sourcepath %src_dir% @sources.txt

if %ERRORLEVEL% == 0 (
    echo Build successful
) else (
    echo Build failed
)
del sources.txt
goto end

:run
"%JAVA_HOME%\bin\java.exe" %jvm_flags% -cp %libs%;bin %entry_point%
goto end

:end

