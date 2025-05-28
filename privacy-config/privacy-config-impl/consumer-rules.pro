# Consumer ProGuard rules for module: privacy-config-impl
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the privacy-config-impl module
-keep class com.duckduckgo.privacy.config.impl.** { *; }
