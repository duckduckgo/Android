# Consumer ProGuard rules for module: sync-lib
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the sync-lib module
-keep class com.duckduckgo.sync.lib.** { *; }
