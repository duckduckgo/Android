//[content-scope-scripts-api](../../../index.md)/[com.duckduckgo.contentscopescripts.api](../index.md)/[ContentScopeConfigPlugin](index.md)

# ContentScopeConfigPlugin

[androidJvm]\
interface [ContentScopeConfigPlugin](index.md)

Implement this interface and contribute it as a multibinding to get called upon downloading remote privacy config

Usage:

```kotlin
@ContributesMultibinding(AppScope::class)
class MuFeaturePlugin @Inject constructor(...) : PrivacyFeaturePlugin {

}
```

## Functions

| Name | Summary |
|---|---|
| [config](config.md) | [androidJvm]<br>abstract fun [config](config.md)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [preferences](preferences.md) | [androidJvm]<br>abstract fun [preferences](preferences.md)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? |
