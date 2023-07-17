//[app-build-config-api](../../../index.md)/[com.duckduckgo.appbuildconfig.api](../index.md)/[AppBuildConfig](index.md)

# AppBuildConfig

[jvm]\
interface [AppBuildConfig](index.md)

## Properties

| Name | Summary |
|---|---|
| [applicationId](application-id.md) | [jvm]<br>abstract val [applicationId](application-id.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [buildType](build-type.md) | [jvm]<br>abstract val [buildType](build-type.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [deviceLocale](device-locale.md) | [jvm]<br>abstract val [deviceLocale](device-locale.md): [Locale](https://docs.oracle.com/javase/8/docs/api/java/util/Locale.html) |
| [flavor](flavor.md) | [jvm]<br>abstract val [flavor](flavor.md): [BuildFlavor](../-build-flavor/index.md) |
| [isDebug](is-debug.md) | [jvm]<br>abstract val [isDebug](is-debug.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
| [isDefaultVariantForced](is-default-variant-forced.md) | [jvm]<br>abstract val [isDefaultVariantForced](is-default-variant-forced.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
| [isPerformanceTest](is-performance-test.md) | [jvm]<br>abstract val [isPerformanceTest](is-performance-test.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
| [isTest](is-test.md) | [jvm]<br>abstract val [isTest](is-test.md): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
| [manufacturer](manufacturer.md) | [jvm]<br>abstract val [manufacturer](manufacturer.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [model](model.md) | [jvm]<br>abstract val [model](model.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [sdkInt](sdk-int.md) | [jvm]<br>abstract val [sdkInt](sdk-int.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |
| [versionCode](version-code.md) | [jvm]<br>abstract val [versionCode](version-code.md): [Int](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/index.html) |
| [versionName](version-name.md) | [jvm]<br>abstract val [versionName](version-name.md): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |

## Functions

| Name | Summary |
|---|---|
| [isInternalBuild](../is-internal-build.md) | [jvm]<br>fun [AppBuildConfig](index.md).[isInternalBuild](../is-internal-build.md)(): [Boolean](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)<br>Convenience extension function |
