# Consumer ProGuard rules for module: test-seeder-internal
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the test-seeder-internal module
-keep class com.duckduckgo.testseeder.internal.** { *; }
