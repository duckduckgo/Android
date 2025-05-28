# Consumer ProGuard rules for module: js-messaging-impl
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the js-messaging-impl module
-keep class com.duckduckgo.js.messaging.impl.** { *; }
