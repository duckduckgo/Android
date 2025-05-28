# Consumer ProGuard rules for module: remote-messaging-api
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the remote-messaging-api module
-keep class com.duckduckgo.remote.messaging.api.** { *; }
