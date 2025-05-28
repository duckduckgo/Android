# Consumer ProGuard rules for module: attributed-metrics-internal
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the attributed-metrics-internal module
-keep class com.duckduckgo.attributed.metrics.internal.** { *; }
