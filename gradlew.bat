@echo off
setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

set DEFAULT_JVM_OPTS=

find_java () (
  if defined JAVA_HOME (
    set JAVA_HOME=%JAVA_HOME:"=%
    set JAVA_EXE=%JAVA_HOME%/bin/java.exe
    if exist "%JAVA_EXE%" goto init
  )
  set JAVA_EXE=java
  %JAVA_EXE% -version >NUL 2>&1
  if "%ERRORLEVEL%" == "0" goto init
  echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
  exit /b 1
)

:init
set GRADLE_USER_HOME=%USERPROFILE%\.gradle

"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" org.gradle.launcher.GradleMainWrapper %*

:end
