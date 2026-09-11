@echo off
setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

where java >nul 2>nul
if errorlevel 1 (
  echo Java is required but not found in PATH. Install JDK 17+ and retry.
  exit /b 1
)

for /f "tokens=3" %%A in ('java -version 2^>^&1 ^| findstr /R /C:"version"') do set JAVA_VERSION=%%~A
set JAVA_VERSION=%JAVA_VERSION:"=%

if not "%JAVA_VERSION:~0,1%"=="1" if not "%JAVA_VERSION:~0,1%"=="2" if not "%JAVA_VERSION:~0,1%"=="3" if not "%JAVA_VERSION:~0,1%"=="4" if not "%JAVA_VERSION:~0,1%"=="5" if not "%JAVA_VERSION:~0,1%"=="6" if not "%JAVA_VERSION:~0,1%"=="7" if not "%JAVA_VERSION:~0,1%"=="8" if not "%JAVA_VERSION:~0,1%"=="9" if not "%JAVA_VERSION:~0,1%"=="1" (
  echo JDK 17+ is required.
  exit /b 1
)

if "%ANDROID_HOME%"=="" if "%ANDROID_SDK_ROOT%"=="" (
  echo ANDROID_HOME or ANDROID_SDK_ROOT is not set. Configure Android SDK before building.
  exit /b 1
)

if "%QAHARNESS_STORE_PASS%"=="" set QAHARNESS_STORE_PASS=qaharness123
if "%QAHARNESS_KEY_PASS%"=="" set QAHARNESS_KEY_PASS=qaharness123

if not exist app\qaharness.jks (
  echo Generating release keystore at app\qaharness.jks
  keytool -genkeypair -alias qaharness -keyalg RSA -keysize 2048 -validity 10000 -keystore app\qaharness.jks -storepass "%QAHARNESS_STORE_PASS%" -keypass "%QAHARNESS_KEY_PASS%" -dname "CN=QAHarness, OU=QA, O=Example, L=Local, ST=Local, C=US"
)

call gradlew.bat clean assembleRelease

if exist app\build\outputs\apk\release\app-release.apk (
  echo APK built successfully: app\build\outputs\apk\release\app-release.apk
  dir /b app\build\outputs\apk\release
) else (
  echo APK not found at app\build\outputs\apk\release\app-release.apk
  exit /b 1
)

where apksigner >nul 2>nul
if not errorlevel 1 (
  echo Verifying APK signature with apksigner...
  apksigner verify --verbose app\build\outputs\apk\release\app-release.apk
) else (
  echo apksigner not found in PATH. APK was built but not verified automatically.
)

echo Install command: adb install -r app\build\outputs\apk\release\app-release.apk
