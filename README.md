DuckDuckGo! The Browser that does not track you. 
==================================================

This is the official repository for Android client of the famous [DuckDuckGo!](https://duckduckgo.com/) search engine.
It is avaliable [in the Google Play Store](https://play.google.com/store/apps/details?id=com.duckduckgo.mobile.android&hl=en). If you want to be a contributor, you are more than welcome to explore this project.

This is a Gradle project, and can be built via the provided ``gradlew`` or Android Studio.

**Index**
---------------


-  [Getting Started](https://github.com/abhaymaniyar/Android#getting-started)
-  [Build instructions (Android Studio)](https://github.com/abhaymaniyar/Android#build-instructions-android-studio)
-  [Build instructions (without Android Studio)](https://github.com/abhaymaniyar/Android#build-instructions-without-android-studio)


Getting Started
------------------------------------

- First, clone this repository and follow the Android Studio build
  instructions (below) to compile the project.  

Build instructions (Android Studio)
-----------------------------------

0. Fork the Android repository from GitHub and clone your fork.

1. Open the project in the IDE.
    a) From the "Welcome to Android Studio" menu, select "Open an existing Android Studio project" option, or
    b) If you already have an opened project, select "File > Open..."

If you have a device running Android go to the settings and enable USB debugging in developer options. Then plug your device in the computer and select "Run > Run...". You will be shown "Device chooser" window. Select your device in the given list and press "OK".

If you do not have an Android device you will have to run it on an emulator. Here are instructions for creating an Android virtual device (AVD):

http://developer.android.com/tools/devices/managing-avds.html#createavd

Build instructions (without Android Studio)
-------------------------------------------

1. Install the Android SDK including at least the API 16 (Android 4.1),
   Build Tools, API Platform, Google APIs, Google Play Services,
   Android Support Library, the Local Maven Repository for Support and
   the Google Repository.

   All of these can be installed, together with their dependencies,
   using the Android SDK manager.

2. Run ``./gradlew`` (or ``gradlew.bat`` on Windows). This should
   automatically build the application, downloading anything it
   needs to do so.

   If you get a failed build with
   ``A problem occurred configuring project ':app'.`` then you might
   not have all the required SDK libraries. Make sure that you have
   all the dependencies of the libraries listed above, and that all
   versions match precisely.

   If the appropriate tools cannot be found by gradle, make sure that
   ``ANDROID_HOME`` is properly set (this should point to the root
   directory for the Android SDK i.e. the one which contains the add-ons,
   build-tools, docs and other directories).

3. To build the APK, run ``./gradlew assemble``. Your APKs will be
   placed in ``app/build/outputs/apk``.

   The ``app-debug.apk`` can be installed directly on the device, or
   loaded over USB using ``./gradlew installDebug`` or
   ``adb install /path/to/app/build/outputs/apk/app-debug.apk``.

   Note that ``app-release-unsigned.apk`` will **not** install by
   default because it is unsigned. You will be told the APK cannot be
   parsed.