# Consumer ProGuard rules for module: vpn-network-impl
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the vpn-network-impl module
-keep class com.duckduckgo.vpn.network.impl.** { *; }

# vpn-network-api is a pure-JVM module, so its own consumer-rules.pro is inert
# (consumerProguardFiles only applies to Android libraries). Keep its classes here.
-keep class com.duckduckgo.vpn.network.api.** { *; }
