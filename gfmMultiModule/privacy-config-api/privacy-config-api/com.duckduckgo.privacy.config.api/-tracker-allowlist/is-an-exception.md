//[privacy-config-api](../../../index.md)/[com.duckduckgo.privacy.config.api](../index.md)/[TrackerAllowlist](index.md)/[isAnException](is-an-exception.md)

# isAnException

[jvm]\
abstract fun [isAnException](is-an-exception.md)(documentURL: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)

This method takes a [documentURL](is-an-exception.md) and a [url](is-an-exception.md) and returns `true` or `false` depending if the [url](is-an-exception.md) and [documentURL](is-an-exception.md) match the rules in the allowlist.

#### Return

`true` if the given [url](is-an-exception.md) and [documentURL](is-an-exception.md) match the rules in the allowlist
