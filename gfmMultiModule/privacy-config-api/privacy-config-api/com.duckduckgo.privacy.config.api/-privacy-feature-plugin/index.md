//[privacy-config-api](../../../index.md)/[com.duckduckgo.privacy.config.api](../index.md)/[PrivacyFeaturePlugin](index.md)

# PrivacyFeaturePlugin

[jvm]\
interface [PrivacyFeaturePlugin](index.md)

Implement this interface and contribute it as a multibinding to get called upon downloading remote privacy config

Usage:

```kotlin
@ContributesMultibinding(AppScope::class)
class MuFeaturePlugin @Inject constructor(...) : PrivacyFeaturePlugin {

}
```

## Properties

| Name | Summary |
|---|---|
| [featureName](feature-name.md) | [jvm]<br>abstract val [featureName](feature-name.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |

## Functions

| Name | Summary |
|---|---|
| [store](store.md) | [jvm]<br>abstract fun [store](store.md)(featureName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), jsonString: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
