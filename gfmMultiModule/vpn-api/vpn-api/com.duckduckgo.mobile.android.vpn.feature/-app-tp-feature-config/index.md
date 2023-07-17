//[vpn-api](../../../index.md)/[com.duckduckgo.mobile.android.vpn.feature](../index.md)/[AppTpFeatureConfig](index.md)

# AppTpFeatureConfig

[androidJvm]\
interface [AppTpFeatureConfig](index.md)

[AppTpFeatureConfig](index.md) returns the configuration of the AppTP features. The configuration is set from either remote config, or the app AppTp internal settings

## Types

| Name | Summary |
|---|---|
| [Editor](-editor/index.md) | [androidJvm]<br>interface [Editor](-editor/index.md) |

## Functions

| Name | Summary |
|---|---|
| [edit](edit.md) | [androidJvm]<br>abstract fun [edit](edit.md)(): [AppTpFeatureConfig.Editor](-editor/index.md) |
| [edit](../edit.md) | [androidJvm]<br>inline fun [AppTpFeatureConfig](index.md).[edit](../edit.md)(action: [AppTpFeatureConfig.Editor](-editor/index.md).() -&gt; [Unit](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html))<br>Convenience extension function to use lambda block |
| [isEnabled](is-enabled.md) | [androidJvm]<br>abstract fun [isEnabled](is-enabled.md)(settingName: [SettingName](../-setting-name/index.md)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
