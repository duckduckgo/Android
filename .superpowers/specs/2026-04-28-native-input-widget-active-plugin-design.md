# NativeInputWidget — ActivePlugin Refactor Design

**Date:** 2026-04-28  
**Status:** Approved  

---

## Context

`NativeInputModeWidget` is a `ConstraintLayout`-based input widget in `:duckchat:duckchat-impl`. It
currently has hardcoded Voice and Image buttons whose visibility and click handling are managed
directly by `NativeInputManager` (in `:app`) via callbacks. This creates:

- A module boundary violation: `:app` imports `NativeInputModeWidget` directly from `duckchat-impl`
  (suppressed in `lint-baseline.xml` as `NoImplImportsInAppModule`)
- Tight coupling: adding a new action button requires changes to the widget, `NativeInputManager`,
  and the callback interface
- No feature-flag gating at the button level

The goal is to replace this with an `ActivePlugin` system so each feature contributes its own
button, controls its own click handling, and contributes state to the prompt at send time — with
show/hide driven by whether the feature is active.

---

## Design

### Core interfaces (in `duckchat-api`)

#### `PromptContribution`

Represents state that a plugin contributes to the prompt when Send is tapped.

```kotlin
sealed class PromptContribution {
    data class AppendedText(val text: String) : PromptContribution()
    data class Attachment(val uri: Uri, val mimeType: String) : PromptContribution()
}
```

New variants can be added in `duckchat-api` as new plugin types are introduced.

#### `NativeInputPlugin`

```kotlin
interface NativeInputPlugin : ActivePlugin {
    @get:IdRes val containerId: Int
    fun createView(context: Context): View
    fun getPromptContribution(): PromptContribution?
}
```

- `containerId` — the `FrameLayout` container in the widget's XML layout that this plugin targets.
  IDs are defined in `duckchat-api/src/main/res/values/ids.xml` so any impl module can reference
  them without depending on `duckchat-impl`.
- `createView` — called once when the widget sets up plugins. The plugin inflates its button,
  attaches its own click listener, and owns all click behaviour (opening a screen, showing a popup,
  etc.).
- `getPromptContribution` — called by the widget when Send is tapped. Returns the plugin's
  contribution to the prompt, or `null` if the plugin has no state to contribute at that point
  (e.g. Voice, which launches the mic and has no inline text to add).

#### Container IDs (`duckchat-api/src/main/res/values/ids.xml`)

```xml
<resources>
    <item name="nativeInputVoiceContainer" type="id"/>
    <item name="nativeInputImageContainer" type="id"/>
</resources>
```

New IDs are added here as new plugin slots are introduced in the widget layout.

---

### Widget layout (`NativeInputModeWidget`)

Each plugin slot is a `FrameLayout` in the widget XML, `visibility="gone"` by default:

```xml
<FrameLayout
    android:id="@id/nativeInputVoiceContainer"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:visibility="gone" />

<FrameLayout
    android:id="@id/nativeInputImageContainer"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:visibility="gone" />
```

Containers are positioned in the layout wherever each button should appear. Placement is a layout
concern — the plugin system does not control it.

---

### Plugin setup in the widget

`NativeInputModeWidget` holds a reference to the `ActivePluginPoint<NativeInputPlugin>` and sets up
plugins after the view is inflated. Active plugins are stored in an instance list so they can be
queried at send time without calling `getPlugins()` again:

```kotlin
private var activePlugins: List<NativeInputPlugin> = emptyList()

private fun setupPlugins() {
    lifecycleScope.launch {
        activePlugins = pluginPoint.getPlugins().toList()
        activePlugins.forEach { plugin ->
            val container = findViewById<FrameLayout>(plugin.containerId)
            container.addView(plugin.createView(context))
            container.isVisible = true
        }
    }
}
```

`getPlugins()` only returns plugins whose feature toggle is enabled and whose `isActive()` returns
`true`. Containers for inactive plugins remain `gone`.

---

### `ActivePluginPoint` declaration (in `duckchat-impl`)

```kotlin
@ContributesActivePluginPoint(
    scope = AppScope::class,
    boundType = NativeInputPlugin::class,
    featureName = "pluginPointNativeInput",
)
private interface NativeInputPluginPointTrigger
```

---

### Prompt building at send time

`NativeInputPrompt` replaces the plain `String` currently passed through send callbacks:

```kotlin
data class NativeInputPrompt(
    val text: String,
    val contributions: List<PromptContribution>,
)
```

When Send is tapped:

```kotlin
private fun onSendTapped() {
    val baseText = inputField.text.toString()
    val contributions = activePlugins.mapNotNull { it.getPromptContribution() }
    val prompt = NativeInputPrompt(text = baseText, contributions = contributions)
    // fire existing callback, now with NativeInputPrompt instead of String
    callbacks.onSearchSubmitted(prompt)   // or onDuckAiChatSubmitted
}
```

`NativeInputManager` and `ContextualNativeInputManager` receive `NativeInputPrompt` and decide how
to use each contribution — e.g. attach an image URI to the chat request.

---

### Migrating existing Voice and Image buttons

The existing hardcoded Voice and Image buttons in `NativeInputModeWidget` and their callback
handling in `NativeInputManager` (`onVoiceSearchPressed`, `onImageButtonPressed`) are replaced by
two plugin implementations.

#### `VoiceNativeInputPlugin` (in `duckchat-impl`)

Voice search availability is already injected into `duckchat-impl` via `VoiceSearchAvailability`.
The plugin lives here to avoid creating a new reverse dependency from `voice-search-impl` back to
`duckchat-api`.

```kotlin
@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = NativeInputPlugin::class,
    featureName = "pluginVoiceInput",
    parentFeatureName = "pluginPointNativeInput",
)
class VoiceNativeInputPlugin @Inject constructor(
    private val voiceSearchAvailability: VoiceSearchAvailability,
    private val globalActivityStarter: GlobalActivityStarter,
) : NativeInputPlugin {

    override val containerId: Int = R.id.nativeInputVoiceContainer

    override fun isActive(): Boolean = voiceSearchAvailability.isVoiceSearchAvailable

    override fun createView(context: Context): View {
        return ImageButton(context).apply {
            setImageResource(R.drawable.ic_voice)
            setOnClickListener { globalActivityStarter.start<VoiceSearchActivity>(context) }
        }
    }

    override fun getPromptContribution(): PromptContribution? = null
}
```

#### `ImageNativeInputPlugin` (in the image search feature module)

```kotlin
@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = NativeInputPlugin::class,
    featureName = "pluginImageInput",
    parentFeatureName = "pluginPointNativeInput",
)
class ImageNativeInputPlugin @Inject constructor(...) : NativeInputPlugin {

    private var selectedAttachment: Attachment? = null

    override val containerId: Int = R.id.nativeInputImageContainer

    override fun isActive(): Boolean = true  // gated by parent feature toggle

    override fun createView(context: Context): View {
        return ImageButton(context).apply {
            setImageResource(R.drawable.ic_image)
            setOnClickListener { /* open image picker, store result in selectedAttachment */ }
        }
    }

    override fun getPromptContribution(): PromptContribution? =
        selectedAttachment?.let { PromptContribution.Attachment(it.uri, it.mimeType) }
}
```

---

### Module structure

| Module | Change |
|---|---|
| `duckchat-api` | Add `NativeInputPlugin`, `PromptContribution`, container IDs in `ids.xml` |
| `duckchat-impl` | Remove hardcoded Voice/Image buttons; add `FrameLayout` slots; set up plugin point; update send callback to use `NativeInputPrompt` |
| `duckchat-impl` | Add `VoiceNativeInputPlugin` |
| Image feature impl | Add `ImageNativeInputPlugin` |
| `:app` | Update `NativeInputManager` callbacks to accept `NativeInputPrompt`; remove `onVoiceSearchPressed` / `onImageButtonPressed` callbacks |

---

### What stays the same

- The `onSearchTextChanged`, `onSearchSubmitted`, `onDuckAiChatSubmitted` callback pattern is
  preserved — only the payload type of the submit callbacks changes from `String` to
  `NativeInputPrompt`.
- `ContextualNativeInputManager` (bottom sheet, contextual chat) uses the same plugin point and
  same prompt building logic.
- The module boundary violation (`:app` importing `NativeInputModeWidget`) is not addressed in this
  change — that is a separate refactor.

---

### Testing

- `NativeInputPlugin` implementations are unit-testable: mock the click dependency, call
  `getPromptContribution()`, assert the returned value.
- Widget setup: use a fake `ActivePluginPoint` returning known plugins; assert containers become
  visible and contain the expected views.
- Prompt building: assert `NativeInputPrompt` is constructed correctly from a mix of null and
  non-null contributions.
