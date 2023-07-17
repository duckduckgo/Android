//[privacy-config-api](../../../index.md)/[com.duckduckgo.privacy.config.api](../index.md)/[Gpc](index.md)/[isEnabled](is-enabled.md)

# isEnabled

[jvm]\
abstract fun [isEnabled](is-enabled.md)(): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)

This takes into account two different inputs.

1. 
   If the user enabled or not the GPC feature
2. 
   If the remote configuration has enabled or not the GPC feature. When disabled, the remote configuration value prevails over the user choice.

#### Return

`true` if the feature is enabled and `false` is is not.
