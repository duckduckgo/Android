# Navigating between screens

Navigation goes through `GlobalActivityStarter` (from `navigation-api`) so screens stay decoupled
across modules.

## Registering a screen

Define an `ActivityParams` type and annotate the activity. Anvil codegen generates the mapper.

```kotlin
// In the feature's -api module
data class MyScreenParams(val id: String) : GlobalActivityStarter.ActivityParams

// In the feature's -impl module
@ContributeToActivityStarter(MyScreenParams::class)
class MyActivity : DuckDuckGoActivity() {
    private val params by lazy { intent.getActivityParams(MyScreenParams::class.java) }
}
```

Use `screenName` to opt into deeplink support:

```kotlin
@ContributeToActivityStarter(MyScreenParams::class, screenName = "myScreen")
```

## Choosing the overload

For `ActivityParams`:

| Situation | Use |
|---|---|
| Fire and forget | `globalActivityStarter.start(context, params)` |
| Need a result back | `globalActivityStarter.startForResult(context, params, launcher)` |
| Need the raw `Intent` (e.g. `PendingIntent` for a notification) | `globalActivityStarter.startIntent(context, params)` |

Each takes an optional trailing `options: Bundle?`, and each has a `DeeplinkActivityParams`
counterpart for deeplink entry points.

`FLAG_ACTIVITY_NEW_TASK` is added automatically when `context` is not an `Activity` (Services,
broadcast receivers, JS message handlers, etc.) — do not add it manually.
