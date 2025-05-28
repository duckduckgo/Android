# Consumer ProGuard rules for module: site-preferences-impl
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the site-preferences-impl module
-keep class com.duckduckgo.site.preferences.impl.** { *; }
