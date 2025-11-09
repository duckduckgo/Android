# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

#===============================================================================
# OPTIMIZATION FLAGS
#===============================================================================
# Enable aggressive optimizations
-optimizationpasses 5
-dontusemixedcaseclassnames
-verbose

# Preserve line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations for runtime processing
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

#===============================================================================
# ANDROID FRAMEWORK
#===============================================================================
# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep custom view constructors
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep Activity methods for proper lifecycle
-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelables
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

#===============================================================================
# WEBVIEW & JAVASCRIPT INTERFACES
#===============================================================================
# DuckDuckGo uses WebView extensively - keep all JS interfaces
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep WebView classes
-keep class android.webkit.** { *; }
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public boolean *(android.webkit.WebView, java.lang.String);
}
-keepclassmembers class * extends android.webkit.WebChromeClient {
    public void *(android.webkit.WebView, java.lang.String);
}

#===============================================================================
# DEPENDENCY INJECTION - DAGGER/ANVIL
#===============================================================================
# Keep Dagger components and modules
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class **$$ModuleAdapter
-keep class **$$InjectAdapter
-keep class **$$StaticInjection
-keep class com.squareup.anvil.** { *; }

# Keep all @Inject constructors
-keepclasseswithmembernames class * {
    @javax.inject.Inject <init>(...);
}

# Keep all @Inject fields
-keepclasseswithmembernames class * {
    @javax.inject.Inject <fields>;
}

# Keep all @Inject methods
-keepclasseswithmembernames class * {
    @javax.inject.Inject <methods>;
}

# Keep Dagger generated code
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }
-keep class **_ComponentImpl { *; }

#===============================================================================
# RETROFIT & OKHTTP
#===============================================================================
# Retrofit does reflection on generic parameters
-keepattributes Signature
-keepattributes Exceptions

# Keep Retrofit interfaces and models
-keep interface retrofit2.** { *; }
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp platform used only on JVM and when Conscrypt dependency is available.
-dontwarn okhttp3.internal.platform.ConscryptPlatform
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE

#===============================================================================
# MOSHI - JSON SERIALIZATION
#===============================================================================
# Keep Moshi adapters
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-keepclassmembers class ** {
    @com.squareup.moshi.* <methods>;
}

# Keep models for Moshi serialization
-keep class * {
    @com.squareup.moshi.Json <fields>;
}

# Keep generated JsonAdapters
-keepclasseswithmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}

-keep @com.squareup.moshi.JsonQualifier interface *
-keepclassmembers @com.squareup.moshi.JsonClass class * extends java.lang.Enum {
    <fields>;
    **[] values();
}

#===============================================================================
# ROOM DATABASE
#===============================================================================
# Keep Room database and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public static ** getDatabase(***);
}

# Keep DAO interfaces and implementations
-keep interface * extends androidx.room.Dao
-keep class * extends androidx.room.Dao

# Keep database entities and their fields
-keepclassmembers @androidx.room.Entity class * {
    <fields>;
    <init>(...);
}

#===============================================================================
# KOTLIN & COROUTINES
#===============================================================================
# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt

# Coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.coroutines.SafeContinuation {
    volatile <fields>;
}

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

#===============================================================================
# RXJAVA
#===============================================================================
# RxJava
-dontwarn java.util.concurrent.Flow*
-keep class io.reactivex.** { *; }
-keep interface io.reactivex.** { *; }

#===============================================================================
# COMPOSE
#===============================================================================
# Keep Compose runtime classes
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** {
    <methods>;
    <fields>;
}

#===============================================================================
# DUCKDUCKGO SPECIFIC
#===============================================================================
# Keep all DuckDuckGo model classes (used for serialization)
-keep class com.duckduckgo.**.model.** { *; }
-keep class com.duckduckgo.**.entity.** { *; }
-keep class com.duckduckgo.**.api.** { *; }

# Keep privacy tracking and filtering classes
-keep class com.duckduckgo.app.trackerdetection.** { *; }
-keep class com.duckduckgo.privacy.config.** { *; }

# Keep autofill classes (security-sensitive)
-keep class com.duckduckgo.autofill.** { *; }

# Keep VPN classes
-keep class com.duckduckgo.networkprotection.** { *; }
-keep class com.duckduckgo.mobile.android.vpn.** { *; }

#===============================================================================
# SECURITY & CRYPTOGRAPHY
#===============================================================================
# Keep security classes
-keep class javax.crypto.** { *; }
-keep class javax.security.** { *; }

#===============================================================================
# REMOVE LOGGING IN RELEASE
#===============================================================================
# Remove all Log calls (improves performance and reduces APK size)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Remove Timber logging
-assumenosideeffects class timber.log.Timber* {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

#===============================================================================
# WARNINGS TO IGNORE
#===============================================================================
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn javax.lang.model.**
-dontwarn sun.misc.Unsafe
