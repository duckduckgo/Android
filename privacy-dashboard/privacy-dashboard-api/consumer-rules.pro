# Consumer ProGuard rules for module: privacy-dashboard-api
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the privacy-dashboard-api module
-keep class com.duckduckgo.privacy.dashboard.api.** { *; }
