# FirstScreenHandler: Idle Return New Tab Page

## Problem

When a user returns to the app after a period of inactivity, the app currently shows whatever was on screen when they left (or follows the "show on app launch" user preference). We want to optionally show a fresh New Tab Page when the idle period exceeds a server-configured timeout.

## Solution

Introduce a `FirstScreenHandler` that wraps the existing `ShowOnAppLaunchOptionHandler`. It becomes the single entry point from `BrowserViewModel` for deciding what screen to show on app launch. The idle timeout is controlled by a new remote feature flag toggle on `AndroidBrowserConfigFeature`.

## Feature Flag

Add `showNTPAfterIdleReturn()` toggle to the existing `AndroidBrowserConfigFeature` interface.

- Default: `FALSE`
- Settings JSON: `{"timeoutMinutes": <Long>}` — the idle threshold in minutes
- When enabled, `FirstScreenHandler` reads the timeout from settings
- When disabled or settings missing, the feature is skipped entirely

## Components

### FirstScreenHandler (new)

**Package:** `com.duckduckgo.app.generalsettings.showonapplaunch`

**Interface:**
```kotlin
interface FirstScreenHandler {
    suspend fun handleFirstScreen()
}
```

**Implementation:** `FirstScreenHandlerImpl`

**Dependencies:**
- `AndroidBrowserConfigFeature` — check `showNTPAfterIdleReturn()` toggle and read timeout from settings
- `ShowOnAppLaunchFeature` — gate the delegation to `ShowOnAppLaunchOptionHandler`
- `SettingsDataStore` — read/write own `lastSessionBackgroundTimestamp` (new key, uses `System.currentTimeMillis()`)
- `TabRepository` — call `add()` to create a new tab when timed out
- `ShowOnAppLaunchOptionHandler` — delegate when not timed out
- `DispatcherProvider` — coroutine dispatching

**Own timestamp:** `FirstScreenHandler` manages its own `lastSessionBackgroundTimestamp` in `SettingsDataStore`, separate from `AutomaticDataClearer`'s `appBackgroundedTimestamp`. This avoids the race condition where the data clearer clears its timestamp before `FirstScreenHandler` reads it. Uses `System.currentTimeMillis()` so the value survives device reboots.

`FirstScreenHandler` implements `BrowserLifecycleObserver`:
- `onClose()`: writes `System.currentTimeMillis()` to `settingsDataStore.lastSessionBackgroundTimestamp`
- `handleFirstScreen()`: reads the stored timestamp to compute idle duration

**Logic:**
1. On `handleFirstScreen()`:
   a. Check `showNTPAfterIdleReturn().isEnabled()`
      - If disabled: fall through to step 1d
   b. Read timeout from `showNTPAfterIdleReturn().getSettings()`:
      - If settings is null, fall through to step 1d
      - Parse via `JSONObject(settings).getLong("timeoutMinutes")` wrapped in `runCatching`
      - If missing/malformed, fall through to step 1d
      - Convert to milliseconds: `timeoutMinutes * 60 * 1000`
   c. Compute elapsed time: `System.currentTimeMillis() - lastSessionBackgroundTimestamp`
      - If elapsed >= timeout → `tabRepository.add()` and return
   d. If `showOnAppLaunchFeature.self().isEnabled()` → delegate to `ShowOnAppLaunchOptionHandler.handleAppLaunchOption()`
2. On app backgrounded (`onClose`): write `System.currentTimeMillis()` to `settingsDataStore.lastSessionBackgroundTimestamp`

### BrowserViewModel (modified)

Replace direct usage of `ShowOnAppLaunchOptionHandler` and `ShowOnAppLaunchFeature` with `FirstScreenHandler`.

**Before:**
```kotlin
fun handleShowOnAppLaunchOption() {
    if (showOnAppLaunchFeature.self().isEnabled()) {
        viewModelScope.launch {
            showOnAppLaunchOptionHandler.handleAppLaunchOption()
        }
    }
}
```

**After:**
```kotlin
fun handleShowOnAppLaunchOption() {
    viewModelScope.launch {
        firstScreenHandler.handleFirstScreen()
    }
}
```

The `showOnAppLaunchFeature.self().isEnabled()` gate moves inside `FirstScreenHandler`. When idle return is disabled (or not timed out), `FirstScreenHandler` checks `showOnAppLaunchFeature.self().isEnabled()` before delegating to `ShowOnAppLaunchOptionHandler`, preserving the existing gate behavior.

`FirstScreenHandler` implements `BrowserLifecycleObserver` to write its own timestamp on `onClose()`.

### ShowOnAppLaunchOptionHandler (unchanged)

No modifications. Continues to handle LastOpenedTab, NewTabPage, and SpecificPage options as before.

### AndroidBrowserConfigFeature (modified)

Add one toggle:

```kotlin
@Toggle.DefaultValue(DefaultFeatureValue.FALSE)
fun showNTPAfterIdleReturn(): Toggle
```

## Data Flow

```
App backgrounded:
  └── FirstScreenHandler.onClose() — write System.currentTimeMillis() to lastSessionBackgroundTimestamp

App launch:
  └── BrowserActivity.launchNewSearchOrQuery()
        └── BrowserViewModel.handleShowOnAppLaunchOption()
              └── FirstScreenHandler.handleFirstScreen()
                    ├── [idle return enabled + timed out] → tabRepository.add()
                    └── [idle return disabled OR not timed out]
                          └── [showOnAppLaunchFeature enabled?]
                                └── ShowOnAppLaunchOptionHandler.handleAppLaunchOption()
```

## Timestamp Details

- `FirstScreenHandler` owns its own timestamp: `lastSessionBackgroundTimestamp` (new key in `SettingsDataStore`)
- Written by `FirstScreenHandler.onClose()` using `System.currentTimeMillis()` — survives device reboots
- Independent from `AutomaticDataClearer`'s `appBackgroundedTimestamp` (which uses `SystemClock.elapsedRealtime()` and is cleared on foreground)
- Default value of 0 means first app launch ever will compute a large elapsed time, triggering a new tab — acceptable since the app already starts with a new tab

## Edge Cases

- **Device reboot:** Timestamp survives (uses `System.currentTimeMillis()`). Elapsed time computed correctly.
- **First app launch ever:** No timestamp recorded (0), same as reboot case. New tab opens. Acceptable since the app already starts with a new tab.
- **Feature flag disabled:** Falls through to existing `ShowOnAppLaunchOptionHandler` behavior.
- **Malformed settings JSON:** Caught by `runCatching`, delegates to `ShowOnAppLaunchOptionHandler`.
- **Missing timeoutMinutes in settings:** Caught by `runCatching`, delegates to `ShowOnAppLaunchOptionHandler`.

## Testing

**FirstScreenHandlerImplTest** covering:
1. Feature disabled → delegates to `ShowOnAppLaunchOptionHandler` (if `showOnAppLaunchFeature` enabled)
2. Feature disabled + `showOnAppLaunchFeature` disabled → does nothing
3. Feature enabled, elapsed > timeout → `tabRepository.add()` called
4. Feature enabled, elapsed <= timeout → delegates to `ShowOnAppLaunchOptionHandler`
5. Feature enabled, no timestamp (0) → `tabRepository.add()` called
6. Feature enabled, settings null → delegates to `ShowOnAppLaunchOptionHandler`
7. Feature enabled, malformed settings JSON → delegates to `ShowOnAppLaunchOptionHandler`
8. Feature enabled, missing timeoutMinutes key → delegates to `ShowOnAppLaunchOptionHandler`
9. `onClose()` writes timestamp correctly

Existing `ShowOnAppLaunchOptionHandlerImplTest` remains unchanged.

## Files Changed

| File | Change |
|---|---|
| `AndroidBrowserConfigFeature.kt` | Add `showNTPAfterIdleReturn()` toggle |
| `SettingsDataStore.kt` | Add `lastSessionBackgroundTimestamp` property |
| `FirstScreenHandler.kt` (new) | Interface + implementation |
| `BrowserViewModel.kt` | Replace handler with `FirstScreenHandler` |
| `FirstScreenHandlerImplTest.kt` (new) | Tests for new handler |

## Future Work

- UI button to navigate back to the previously selected tab (separate task)
