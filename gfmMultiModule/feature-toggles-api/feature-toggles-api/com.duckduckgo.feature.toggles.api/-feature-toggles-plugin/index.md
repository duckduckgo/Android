//[feature-toggles-api](../../../index.md)/[com.duckduckgo.feature.toggles.api](../index.md)/[FeatureTogglesPlugin](index.md)

# FeatureTogglesPlugin

[jvm]\
interface [~~FeatureTogglesPlugin~~](index.md)---

### Deprecated

Use the new feature flag framework, https://app.asana.com/0/1202552961248957/1203898052213029/f

---

Features that can be enabled/disabled should implement this plugin. The associated plugin point will call the plugins when the [FeatureToggle](../-feature-toggle/index.md) API is used

## Functions

| Name | Summary |
|---|---|
| [isEnabled](is-enabled.md) | [jvm]<br>abstract fun [isEnabled](is-enabled.md)(featureName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), defaultValue: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)?<br>This method will return whether the plugin knows about the [featureName](is-enabled.md) in which case will return whether it is enabled or disabled |
