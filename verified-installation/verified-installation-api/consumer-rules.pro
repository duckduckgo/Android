# Consumer ProGuard rules for module: verified-installation-api
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the verified-installation-api module
-keep class com.duckduckgo.verified.installation.api.** { *; }
