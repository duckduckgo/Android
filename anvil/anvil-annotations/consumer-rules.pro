# Consumer ProGuard rules for module: anvil-annotations
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the anvil-annotations module
-keep class com.duckduckgo.anvil.annotations.** { *; }
