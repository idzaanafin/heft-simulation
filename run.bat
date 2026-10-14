@echo off
set "JAVA21=c:\Users\rayha\.antigravity-ide\extensions\redhat.java-1.56.0-win32-x64\jre\21.0.12.1-win32-x86_64\bin\java.exe"
set "CP=target\classes;%USERPROFILE%\.m2\repository\org\cloudbus\cloudsim\cloudsim\7.0.1\cloudsim-7.0.1.jar"

if "%~1"=="" (
    "%JAVA21%" -cp "%CP%" Main sipht 200 80
) else (
    "%JAVA21%" -cp "%CP%" Main %*
)
