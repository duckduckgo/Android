# Consumer ProGuard rules for module: common-ui
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the common-ui module
-keep class com.duckduckgo.common.ui.** { *; }
