//[privacy-config-api](../../../index.md)/[com.duckduckgo.privacy.config.api](../index.md)/[TrackerAllowlist](index.md)

# TrackerAllowlist

[jvm]\
interface [TrackerAllowlist](index.md)

Public interface for the Tracker Allowlist feature

## Functions

| Name | Summary |
|---|---|
| [isAnException](is-an-exception.md) | [jvm]<br>abstract fun [isAnException](is-an-exception.md)(documentURL: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), url: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>This method takes a [documentURL](is-an-exception.md) and a [url](is-an-exception.md) and returns `true` or `false` depending if the [url](is-an-exception.md) and [documentURL](is-an-exception.md) match the rules in the allowlist. |
