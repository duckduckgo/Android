//[vpn-api](../../../../index.md)/[com.duckduckgo.mobile.android.vpn.feature](../../index.md)/[AppTpFeatureConfig](../index.md)/[Editor](index.md)/[setEnabled](set-enabled.md)

# setEnabled

[androidJvm]\
abstract fun [setEnabled](set-enabled.md)(settingName: [SettingName](../../-setting-name/index.md), enabled: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html), isManualOverride: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) = false)

#### Parameters

androidJvm

| | |
|---|---|
| settingName | the name of the setting to set |
| enabled | `true` to set the setting enabled, `false` otherwise |
| isManualOverride | set to `true` to signal that this has been set manually by the user<br>usage:<br>```kotlin val config = appTpFeatureConfig.edit {    setEnabled(settingName, true) } ``` |
