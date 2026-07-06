# Consumer ProGuard rules for module: feature-toggles-impl
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the feature-toggles-impl module
-keep class com.duckduckgo.feature.toggles.impl.** { *; }

# The remote-feature framework parses config with reflection-based Moshi
# (REMOTE_FEATURE_MOSHI) into shared JSON models in feature-toggles-api /
# feature-toggles-internal-api (FeatureException, JsonFeature, JsonToggle, JsonException,
# JsonToggleTarget/Cohort/Rollout, Toggle.State). Those are pure-JVM modules, so their
# own consumer-rules.pro are INERT (consumerProguardFiles only applies to Android libs).
# Keep them here — this Android-library module owns the framework and is always present.
-keep class com.duckduckgo.feature.toggles.api.** { *; }
-keep class com.duckduckgo.feature.toggles.internal.api.** { *; }
