//[feature-toggles-api](../../../index.md)/[com.duckduckgo.feature.toggles.api](../index.md)/[FeatureToggle](index.md)/[isFeatureEnabled](is-feature-enabled.md)

# isFeatureEnabled

[jvm]\
abstract fun [isFeatureEnabled](is-feature-enabled.md)(featureName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), defaultValue: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) = true): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)

This method takes a [featureName](is-feature-enabled.md) and optionally a default value.

#### Return

`true` if the feature is enabled, `false` if is not

#### Throws

| | |
|---|---|
| [IllegalArgumentException](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-illegal-argument-exception/index.html) | if the feature is not implemented |
