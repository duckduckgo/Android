# Consumer ProGuard rules for module: settings-api
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the settings-api module
-keep class com.duckduckgo.settings.api.** { *; }
