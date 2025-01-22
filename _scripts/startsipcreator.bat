@echo off

REM Set the path to the correct Java version (JDK 8 latest version)
set JAVA_HOME=C:\Program Files\Java\jdk1.8.0_371

REM Set the path to the pocket mapper
set POCKET_MAPPER_HOME=C:\path\to\pocket\mapper

REM Set the maximum memory allocation pool for the JVM
set JVM_OPTIONS=-Xmx2048M

REM Set the path to the SIP Creator JAR file for version 1.2.8
set SIP_CREATOR_JAR=C:\path\to\sipcreator\sip-app-1.2.8-exejar.jar

REM Start the application
"%JAVA_HOME%\bin\java.exe" -Duser.home="%POCKET_MAPPER_HOME%" %JVM_OPTIONS% -jar "%SIP_CREATOR_JAR%"
