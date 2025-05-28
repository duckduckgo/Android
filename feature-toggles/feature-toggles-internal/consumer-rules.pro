# Consumer ProGuard rules for module: feature-toggles-internal
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the feature-toggles-internal module
-keep class com.duckduckgo.feature.toggles.internal.** { *; }
