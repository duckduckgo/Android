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

# disable obfuscation
-dontobfuscate

# required for crash and ANR traces
-keepattributes SourceFile,LineNumberTable

# required for fasterxml.jackson
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient

# required for Moshi (keeps generated default-value constructors and reflectively-set fields)
-keepclassmembers class com.duckduckgo.** {
    <init>(...);
    <fields>;
}

# required for reflective injection (used by AndroidInjector)
-keepclassmembers class * {
    void inject(***);
}

# required for layout inflation
-keepclassmembers class * implements androidx.viewbinding.ViewBinding {
    public static *** inflate(...);
    public static *** bind(android.view.View);
}

# required for org.json (Android uses its own copy at runtime, so optimizing the bundled one causes NoSuchMethodError)
-keep class org.json.** { *; }

# required for jjwt
-keep class io.jsonwebtoken.** { *; }

# required for netguard
-keep class com.duckduckgo.vpn.network.impl.RealVpnNetwork { *; }
-keep class com.duckduckgo.vpn.network.impl.models.** { *; }

# required for wireguard-go
-keep class com.wireguard.android.backend.GoBackend { *; }
