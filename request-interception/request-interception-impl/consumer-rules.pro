# Consumer ProGuard rules for module: request-interception-impl
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the request-interception-impl module
-keep class com.duckduckgo.request.interception.impl.** { *; }
