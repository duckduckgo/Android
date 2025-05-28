# Consumer ProGuard rules for module: new-tab-page-impl
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the new-tab-page-impl module
-keep class com.duckduckgo.newtabpage.impl.** { *; }
