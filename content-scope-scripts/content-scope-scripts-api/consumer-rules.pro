# Consumer ProGuard rules for module: content-scope-scripts-api
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the content-scope-scripts-api module
-keep class com.duckduckgo.contentscope.scripts.api.** { *; }
