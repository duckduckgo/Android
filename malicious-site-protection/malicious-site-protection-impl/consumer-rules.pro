# Consumer ProGuard rules for module: malicious-site-protection-impl
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the malicious-site-protection-impl module
-keep class com.duckduckgo.malicious.site.protection.impl.** { *; }
