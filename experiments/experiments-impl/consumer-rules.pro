# Consumer ProGuard rules for module: experiments-impl
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the experiments-impl module
-keep class com.duckduckgo.experiments.impl.** { *; }
