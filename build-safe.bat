@echo off
setlocal
pushd "%~dp0"
if errorlevel 1 exit /b 1
call gradlew.bat --no-daemon build > build.log 2>&1
set "BUILD_CODE=%ERRORLEVEL%"
type build.log
echo BUILD_LOG=%CD%\build.log
echo BUILD_EXIT_CODE=%BUILD_CODE%
>> build.log echo BUILD_EXIT_CODE=%BUILD_CODE%
popd
endlocal & exit /b %BUILD_CODE%
