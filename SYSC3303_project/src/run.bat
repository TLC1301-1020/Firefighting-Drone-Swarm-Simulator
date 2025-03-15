@echo off
REM Delete all .class files in the current directory
del /q *.class

REM Compile all Java files in the current directory
javac *.java

REM Launch Scheduler in a new cmd window with the current directory
start cmd /k "cd /d %cd% && java Scheduler"

REM Launch DroneSubsystem in a new cmd window with the current directory
start cmd /k "cd /d %cd% && java DroneSubsystem"

REM Launch FireIncidentSubsystem in a new cmd window with the current directory
start cmd /k "cd /d %cd% && java FireIncidentSubsystem"

pause