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

# :app has no consumer-rules.pro (it's the composition root). It parses remote
# config / settings with reflection-based Moshi in ~11 features; those models use
# property names as JSON keys (no @field:Json anywhere in :app), so their packages
# must be kept. Package-scoped (not per-class) to stay robust to nested/sibling
# model types. Everything else in :app is now shrunk/optimized/obfuscated.
# NOTE: verified via the sync E2E + boot only; the non-sync features below need the
# full QA/E2E suite before shipping. Candidates for further per-class narrowing.
-keep class com.duckduckgo.app.trackerdetection.api.** { *; }       # TdsJson family (tracker data set)
-keep class com.duckduckgo.app.pixels.campaign.** { *; }            # AdditionalPixelParamsSettings
-keep class com.duckduckgo.app.startup.** { *; }                    # AppStartupMetricsJson
-keep class com.duckduckgo.app.email.sync.** { *; }                 # DuckAddressSetting
-keep class com.duckduckgo.app.browser.defaultbrowsing.prompts.** { *; }  # FeatureSettingsConfigModel
-keep class com.duckduckgo.app.browser.indexeddb.** { *; }          # IndexedDBSettings
-keep class com.duckduckgo.app.browser.mediaplayback.store.** { *; }      # MediaPlaybackSettingsJson
-keep class com.duckduckgo.app.browser.weblocalstorage.** { *; }    # WebLocalStorageSettings / SettingsJson
-keep class com.duckduckgo.app.browser.trafficquality.remote.** { *; }    # TrafficQualitySettingsJson family
-keep class com.duckduckgo.app.browser.webview.** { *; }            # WebViewCompatFeatureSettings
-keep class com.duckduckgo.app.browser.certificates.remoteconfig.** { *; }  # SSLCertificatesFeature.State

# WorkManager workers are instantiated reflectively by class name in
# DaggerWorkerFactory (Class.forName(workerClassName).newInstance(...)), so keep
# every worker's name + (Context, WorkerParameters) constructor app-wide.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
