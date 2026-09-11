# QA Capture Harness

This Android project is a development/debug viewer for a computer-vision capture pipeline. It captures the screen via MediaProjection, analyzes frames with OpenCV, and renders in-app overlays for debugging and calibration.

## Prerequisites

- JDK 17
- Android SDK 34
- Android Studio latest stable
- OpenCV 4.9 Android SDK

## OpenCV SDK location

Place the official OpenCV Android SDK on disk and point the project at it by editing the `opencvSdkDir` property in `gradle.properties`.

Example:

```properties
opencvSdkDir=C:/OpenCV-android-sdk/sdk
```

The root `settings.gradle` uses this property to include the OpenCV module:

```gradle
include(":opencv")
project(":opencv").projectDir = file(providers.gradleProperty("opencvSdkDir").getOrElse("C:/OpenCV-android-sdk/sdk"))
```

## Build

From the project root:

```bash
./gradlew clean assembleRelease
```

On Windows:

```bat
gradlew.bat clean assembleRelease
```

## Install

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Runtime permissions

The app requests the following when needed:

- Screen capture permission via MediaProjection
- Foreground service permissions
- Notification permission on Android 13+

## Notes

This project is a debug harness only and intentionally does not automate input or game logic.
