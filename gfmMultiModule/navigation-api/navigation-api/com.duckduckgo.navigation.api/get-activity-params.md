//[navigation-api](../../index.md)/[com.duckduckgo.navigation.api](index.md)/[getActivityParams](get-activity-params.md)

# getActivityParams

[androidJvm]\
fun &lt;[T](get-activity-params.md) : [Serializable](https://developer.android.com/reference/kotlin/java/io/Serializable.html)?&gt; [Intent](https://developer.android.com/reference/kotlin/android/content/Intent.html).[getActivityParams](get-activity-params.md)(clazz: [Class](https://developer.android.com/reference/kotlin/java/lang/Class.html)&lt;[T](get-activity-params.md)&gt;): [T](get-activity-params.md)?

This is a convenience method to extract the typed parameters from the launched activity

```kotlin
@ContributeToActivityStarter(ExampleActivityParams::class)
class ExampleActivity() : DuckDuckGoActivity() {
  fun onCreate(...) {
    val params = intent.getActivityParams(ExampleActivityParams::class)
    ...
  }
}
```
