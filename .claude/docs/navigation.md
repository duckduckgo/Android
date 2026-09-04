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

Use `deeplinkScreenName` to opt into deeplink support:

```kotlin
@ContributeToActivityStarter(MyScreenParams::class, deeplinkScreenName = "myScreen")
```

Only declare one for screens that are a sensible entry point — settings screens, feature landing
screens. Do not declare one for screens in the middle of a flow, screens that need caller context
(a tab, a credential), screens that load a caller-provided URL, or screens in `*-internal` modules
and internal build variants. Names follow `<feature>.<subScreen>` with each segment camelCase
(`vpn.geoswitching`), or a single segment when the screen has no parent feature (`bookmarks`), and
must be unique: when two mappers claim the same name the winner is undefined.

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
