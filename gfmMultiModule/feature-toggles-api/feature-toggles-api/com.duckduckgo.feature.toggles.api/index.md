//[feature-toggles-api](../../index.md)/[com.duckduckgo.feature.toggles.api](index.md)

# Package-level declarations

## Types

| Name | Summary |
|---|---|
| [FeatureExceptions](-feature-exceptions/index.md) | [jvm]<br>object [FeatureExceptions](-feature-exceptions/index.md) |
| [FeatureSettings](-feature-settings/index.md) | [jvm]<br>object [FeatureSettings](-feature-settings/index.md) |
| [FeatureToggle](-feature-toggle/index.md) | [jvm]<br>interface [~~FeatureToggle~~](-feature-toggle/index.md)<br>Any feature toggles implemented in any module should implement [FeatureToggle](-feature-toggle/index.md) |
| [FeatureToggles](-feature-toggles/index.md) | [jvm]<br>class [FeatureToggles](-feature-toggles/index.md) |
| [FeatureTogglesPlugin](-feature-toggles-plugin/index.md) | [jvm]<br>interface [~~FeatureTogglesPlugin~~](-feature-toggles-plugin/index.md)<br>Features that can be enabled/disabled should implement this plugin. The associated plugin point will call the plugins when the [FeatureToggle](-feature-toggle/index.md) API is used |
| [RemoteFeatureStoreNamed](-remote-feature-store-named/index.md) | [jvm]<br>@Qualifier<br>annotation class [RemoteFeatureStoreNamed](-remote-feature-store-named/index.md)(val value: [KClass](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.reflect/-k-class/index.html)&lt;*&gt; = Unit::class) |
| [Toggle](-toggle/index.md) | [jvm]<br>interface [Toggle](-toggle/index.md) |
