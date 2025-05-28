# Consumer ProGuard rules for module: autofill-store
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the autofill-store module
-keep class com.duckduckgo.autofill.store.** { *; }
