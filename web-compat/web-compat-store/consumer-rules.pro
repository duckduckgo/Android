# Consumer ProGuard rules for module: web-compat-store
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the web-compat-store module
-keep class com.duckduckgo.web.compat.store.** { *; }
