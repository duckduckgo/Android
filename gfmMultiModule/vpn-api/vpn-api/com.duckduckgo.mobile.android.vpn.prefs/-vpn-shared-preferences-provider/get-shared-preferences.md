//[vpn-api](../../../index.md)/[com.duckduckgo.mobile.android.vpn.prefs](../index.md)/[VpnSharedPreferencesProvider](index.md)/[getSharedPreferences](get-shared-preferences.md)

# getSharedPreferences

[androidJvm]\
abstract fun [getSharedPreferences](get-shared-preferences.md)(name: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), multiprocess: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) = false, migrate: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) = false): [SharedPreferences](https://developer.android.com/reference/kotlin/android/content/SharedPreferences.html)

Returns an instance of Shared preferences

#### Parameters

androidJvm

| | |
|---|---|
| name | Name of the shared preferences |
| multiprocess | `true` if the shared preferences will be accessed from several processes else `false` |
| migrate | `true` if the shared preferences existed prior to use the [VpnSharedPreferencesProvider](index.md), else `false` |
