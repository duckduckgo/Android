# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- App-level R8 rules ---
# Most R8 hardening for this branch lives in the owning modules' consumer-rules.pro:
#   ViewBinding + Compose-lint -dontwarn -> :android-design-system:design-system
#   DataStore shaded-protobuf keeps      -> :data-store:data-store-impl
#   feature-toggles reflective JSON models -> :feature-toggles:feature-toggles-impl
#   other pure-JVM -api module keeps      -> each module's -impl consumer-rules.pro
#
# Only genuinely app-scoped rules remain here.

# Jackson databind (transitive, no single feature owner) references optional
# desktop-JVM classes that are never present on Android at runtime.
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient

# The :app module is the composition root and has no consumer-rules.pro; it owns
# reflective Moshi models (TdsJson, SettingsJson, AppStartupMetricsJson, ...).
# Kept broadly for now — should be refined to targeted keeps (like sync-impl).
-keep class com.duckduckgo.app.** { *; }
