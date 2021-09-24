@echo off

set src_dir=src
set out_dir=bin
set libs=""
set compile_flags=-J-Xms2048m -J-Xmx2048m -J-XX:+UseG1GC -Xdiags:verbose -Xlint:all -Xmaxerrs 5 -encoding UTF8 --release 17 -g

set entry_point=Main
set launch_file=run.bat
set jvm_flags=-ea -Xms2048m -Xmx2048m -XX:+AlwaysPreTouch -XX:+UseG1GC -Xmixed

if exist %out_dir% (
    rmdir /s /q %out_dir%
    mkdir %out_dir%
)

if exist %launch_file% del %launch_file%

dir /s /b %src_dir%\*.java > sources.txt
javac.exe %compile_flags% -classpath %libs% -d %out_dir% -sourcepath %src_dir% @sources.txt

if %ERRORLEVEL% == 0 (
    echo Build successful
    echo @echo off > %launch_file%
    echo java.exe %jvm_flags% -cp %libs%;bin %entry_point% >> %launch_file%
) else (
    echo Build failed
)

del sources.txt
