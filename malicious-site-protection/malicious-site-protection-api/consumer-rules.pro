# Consumer ProGuard rules for module: malicious-site-protection-api
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the malicious-site-protection-api module
-keep class com.duckduckgo.malicious.site.protection.api.** { *; }
