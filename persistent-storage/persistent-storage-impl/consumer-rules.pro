# Consumer ProGuard rules for module: persistent-storage-impl
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the persistent-storage-impl module
-keep class com.duckduckgo.persistentstorage.impl.** { *; }
