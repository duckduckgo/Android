# Consumer ProGuard rules for module: dax-prompts-api
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the dax-prompts-api module
-keep class com.duckduckgo.dax.prompts.api.** { *; }
