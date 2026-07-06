# Consumer ProGuard rules for module: data-store-impl
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the data-store-impl module
-keep class com.duckduckgo.datastore.impl.** { *; }

# Jetpack Preferences DataStore bundles a SHADED protobuf runtime that resolves
# generated-message fields (e.g. value_) via reflection. If R8 renames those fields
# the app crashes at startup with "Field value_ ... not found"
# (androidx.datastore.preferences.PreferencesProto$Value). Only Tink's shaded protobuf
# ships a keep, not DataStore's — so keep it here in the DataStore module.
-keep class androidx.datastore.preferences.protobuf.** { *; }
-keep class androidx.datastore.preferences.PreferencesProto$* { *; }
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}
