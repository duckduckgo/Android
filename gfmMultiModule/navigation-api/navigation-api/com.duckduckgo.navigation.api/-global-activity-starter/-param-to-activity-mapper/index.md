//[navigation-api](../../../../index.md)/[com.duckduckgo.navigation.api](../../index.md)/[GlobalActivityStarter](../index.md)/[ParamToActivityMapper](index.md)

# ParamToActivityMapper

[androidJvm]\
interface [ParamToActivityMapper](index.md)

Implement this mapper that will return [Activity](https://developer.android.com/reference/kotlin/android/app/Activity.html) class for the given parameters. Once implemented it, you need to contribute it as a multibinding using ContributesMultibinding into the AppScope.

```kotlin
@ContributesMultibinding(AppScope::class)
class ExampleParamToActivityMapper @Inject constructor(...) : ParamToActivityMapper {
  fun fun map(params: ActivityParams): Class<out AppCompatActivity>? {
    return if (params is ExampleActivityParams) {
      ExampleActivity::class.java
    }
    else {
      null
    }
  }
}
data class ExampleActivityParams(...) : ActivityParams

class ExampleActivity() : DuckDuckGoActivity() {...}
```

Alternatively you can also use the ContributeToActivityStarter annotation to autogenerate the parap to activity mapper above.

```kotlin
@ContributeToActivityStarter(ExampleActivityParams::class)
class ExampleActivity() : DuckDuckGoActivity() {...}
```

## Functions

| Name | Summary |
|---|---|
| [map](map.md) | [androidJvm]<br>abstract fun [map](map.md)(activityParams: [GlobalActivityStarter.ActivityParams](../-activity-params/index.md)): [Class](https://developer.android.com/reference/kotlin/java/lang/Class.html)&lt;out [AppCompatActivity](https://developer.android.com/reference/kotlin/androidx/appcompat/app/AppCompatActivity.html)&gt;? |
