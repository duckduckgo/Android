# Consumer ProGuard rules for module: statistics-impl
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the statistics-impl module
-keep class com.duckduckgo.statistics.impl.** { *; }

# statistics-api is a pure-JVM module (package com.duckduckgo.app.statistics), so its
# own consumer-rules.pro is inert (consumerProguardFiles only applies to Android libs).
-keep class com.duckduckgo.app.statistics.** { *; }
