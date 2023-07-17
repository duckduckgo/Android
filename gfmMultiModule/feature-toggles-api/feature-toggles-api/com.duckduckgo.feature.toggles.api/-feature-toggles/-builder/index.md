//[feature-toggles-api](../../../../index.md)/[com.duckduckgo.feature.toggles.api](../../index.md)/[FeatureToggles](../index.md)/[Builder](index.md)

# Builder

[jvm]\
data class [Builder](index.md)(store: [Toggle.Store](../../-toggle/-store/index.md)? = null, appVersionProvider: () -&gt; [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) = { Int.MAX_VALUE }, featureName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null)

## Constructors

| | |
|---|---|
| [Builder](-builder.md) | [jvm]<br>constructor(store: [Toggle.Store](../../-toggle/-store/index.md)? = null, appVersionProvider: () -&gt; [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) = { Int.MAX_VALUE }, featureName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)? = null) |

## Functions

| Name | Summary |
|---|---|
| [appVersionProvider](app-version-provider.md) | [jvm]<br>fun [appVersionProvider](app-version-provider.md)(appVersionProvider: () -&gt; [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)): [FeatureToggles.Builder](index.md) |
| [build](build.md) | [jvm]<br>fun [build](build.md)(): [FeatureToggles](../index.md) |
| [featureName](feature-name.md) | [jvm]<br>fun [featureName](feature-name.md)(featureName: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [FeatureToggles.Builder](index.md) |
| [store](store.md) | [jvm]<br>fun [store](store.md)(store: [Toggle.Store](../../-toggle/-store/index.md)): [FeatureToggles.Builder](index.md) |
