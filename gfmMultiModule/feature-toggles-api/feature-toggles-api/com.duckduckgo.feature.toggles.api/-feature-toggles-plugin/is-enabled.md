//[feature-toggles-api](../../../index.md)/[com.duckduckgo.feature.toggles.api](../index.md)/[FeatureTogglesPlugin](index.md)/[isEnabled](is-enabled.md)

# isEnabled

[jvm]\
abstract fun [isEnabled](is-enabled.md)(featureName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), defaultValue: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)?

This method will return whether the plugin knows about the [featureName](is-enabled.md) in which case will return whether it is enabled or disabled

#### Return

`true` if the feature is enable. `false` when disabled. `null` if the plugin does not know the featureName. [defaultValue](is-enabled.md) if the plugin knows featureName but is not set
