//[vpn-api](../../../index.md)/[com.duckduckgo.mobile.android.vpn.feature](../index.md)/[AppTpFeatureConfig](index.md)/[isEnabled](is-enabled.md)

# isEnabled

[androidJvm]\
abstract fun [isEnabled](is-enabled.md)(settingName: [SettingName](../-setting-name/index.md)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)

#### Return

`true` if the setting is enabled, else `false`

usage:

```kotlin
val enabled = appTpFeatureConfig.isEnabled(settingName)
```

#### Parameters

androidJvm

| | |
|---|---|
| settingName | the [SettingName](../-setting-name/index.md) |
