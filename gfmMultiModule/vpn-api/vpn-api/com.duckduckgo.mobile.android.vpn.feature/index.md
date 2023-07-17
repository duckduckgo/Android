//[vpn-api](../../index.md)/[com.duckduckgo.mobile.android.vpn.feature](index.md)

# Package-level declarations

## Types

| Name | Summary |
|---|---|
| [AppTpFeatureConfig](-app-tp-feature-config/index.md) | [androidJvm]<br>interface [AppTpFeatureConfig](-app-tp-feature-config/index.md)<br>[AppTpFeatureConfig](-app-tp-feature-config/index.md) returns the configuration of the AppTP features. The configuration is set from either remote config, or the app AppTp internal settings |
| [AppTpFeatureName](-app-tp-feature-name/index.md) | [androidJvm]<br>enum [AppTpFeatureName](-app-tp-feature-name/index.md) : [Enum](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-enum/index.html)&lt;[AppTpFeatureName](-app-tp-feature-name/index.md)&gt; |
| [AppTpSetting](-app-tp-setting/index.md) | [androidJvm]<br>enum [AppTpSetting](-app-tp-setting/index.md) : [Enum](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-enum/index.html)&lt;[AppTpSetting](-app-tp-setting/index.md)&gt; , [SettingName](-setting-name/index.md) |
| [SettingName](-setting-name/index.md) | [androidJvm]<br>interface [SettingName](-setting-name/index.md) |

## Functions

| Name | Summary |
|---|---|
| [edit](edit.md) | [androidJvm]<br>inline fun [AppTpFeatureConfig](-app-tp-feature-config/index.md).[edit](edit.md)(action: [AppTpFeatureConfig.Editor](-app-tp-feature-config/-editor/index.md).() -&gt; [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html))<br>Convenience extension function to use lambda block |
| [SettingName](-setting-name.md) | [androidJvm]<br>fun [SettingName](-setting-name.md)(block: () -&gt; [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [SettingName](-setting-name/index.md)<br>Fake constructor for [SettingName](-setting-name/index.md) from the passed in [block](-setting-name.md) lambda instead of using the anonymous `object : FeatureName` syntax. |
