# Consumer ProGuard rules for module: settings-impl
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the settings-impl module
-keep class com.duckduckgo.settings.impl.** { *; }
