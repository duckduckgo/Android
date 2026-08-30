# applied to internal builds only, play and fdroid are not minified

# disable obfuscation
-dontobfuscate

# required for internal crash and ANR traces
-keepattributes SourceFile,LineNumberTable

# required for fasterxml.jackson
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient

# required for Moshi (keeps Kotlin's generated default-value constructors)
-keepclassmembers class com.duckduckgo.** {
    <init>(...);
}

# required for reflective Dagger injection (used by AndroidInjector)
-keepclassmembers class * {
    void inject(***);
}

# required for layout inflation
-keepclassmembers class * implements androidx.viewbinding.ViewBinding {
    public static *** inflate(...);
    public static *** bind(android.view.View);
}
