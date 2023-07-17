//[feature-toggles-api](../../../index.md)/[com.duckduckgo.feature.toggles.api](../index.md)/[FeatureToggle](index.md)

# FeatureToggle

[jvm]\
interface [~~FeatureToggle~~](index.md)---

### Deprecated

Use the new feature flag framework, https://app.asana.com/0/1202552961248957/1203898052213029/f

---

Any feature toggles implemented in any module should implement [FeatureToggle](index.md)

## Functions

| Name | Summary |
|---|---|
| [isFeatureEnabled](is-feature-enabled.md) | [jvm]<br>abstract fun [isFeatureEnabled](is-feature-enabled.md)(featureName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), defaultValue: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) = true): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>This method takes a [featureName](is-feature-enabled.md) and optionally a default value. |
