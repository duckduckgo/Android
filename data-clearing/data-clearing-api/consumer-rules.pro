# Consumer ProGuard rules for module: data-clearing-api
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the data-clearing-api module
-keep class com.duckduckgo.dataclearing.api.** { *; }
