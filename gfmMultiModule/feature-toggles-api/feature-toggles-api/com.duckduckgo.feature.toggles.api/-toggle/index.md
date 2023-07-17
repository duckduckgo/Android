//[feature-toggles-api](../../../index.md)/[com.duckduckgo.feature.toggles.api](../index.md)/[Toggle](index.md)

# Toggle

[jvm]\
interface [Toggle](index.md)

## Types

| Name | Summary |
|---|---|
| [DefaultValue](-default-value/index.md) | [jvm]<br>@[Target](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.annotation/-target/index.html)(allowedTargets = [[AnnotationTarget.FUNCTION](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.annotation/-annotation-target/-f-u-n-c-t-i-o-n/index.html)])<br>annotation class [DefaultValue](-default-value/index.md)(val defaultValue: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)) |
| [State](-state/index.md) | [jvm]<br>data class [State](-state/index.md)(val enable: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) = false, val minSupportedVersion: [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html)? = null, val enabledOverrideValue: [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)? = null) |
| [Store](-store/index.md) | [jvm]<br>interface [Store](-store/index.md) |

## Functions

| Name | Summary |
|---|---|
| [isEnabled](is-enabled.md) | [jvm]<br>abstract fun [isEnabled](is-enabled.md)(): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
| [setEnabled](set-enabled.md) | [jvm]<br>abstract fun [setEnabled](set-enabled.md)(state: [Toggle.State](-state/index.md)) |
