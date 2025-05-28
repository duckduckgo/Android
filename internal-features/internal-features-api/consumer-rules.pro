# Consumer ProGuard rules for module: internal-features-api
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the internal-features-api module
-keep class com.duckduckgo.internal.features.api.** { *; }
