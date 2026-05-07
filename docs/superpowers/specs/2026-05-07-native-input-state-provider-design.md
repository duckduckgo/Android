# NativeInputStateProvider Design

**Goal:** Give every `NativeInputPlugin` a single app-scoped provider it can both observe (`StateFlow<NativeInputState>`) and write to, with per-tab state persisted in Room so it survives app restarts.

**Architecture:** An app-scoped provider (`RealNativeInputStateProvider`) holds a `Map<TabId, MutableStateFlow<NativeInputState>>` in memory, backed by a Room DAO for persisted fields. Plugin ViewModels inject the provider directly and push their contributions via `update(tabId) { copy(...) }`. The widget ViewModel owns structural fields; plugins own their own fields. Neither clobbers the other.

**Tech Stack:** Kotlin Coroutines (`StateFlow`, `stateIn`), Anvil/Dagger2 (`@SingleInstanceIn(AppScope::class)`, `@ContributesBinding`), Room (DAO + Entity), ViewModelStore per plugin instance.

---

## Context

As of PR #8488, `NativeInputPlugin.getPromptContribution()` is the only mechanism plugins have to contribute data at send time. `ModelPickerNativeInputPlugin` holds a `WeakReference<ModelPicker>` and reads `selectedModelId` from the view at the moment the user taps send.

This design replaces that pull model with a push model: plugins write their contributions into `NativeInputState` as the user makes choices. The widget reads the provider at send time instead of polling plugins. `getPromptContribution()` is deprecated and will be removed once all plugins have migrated.

Additionally, `NativeInputState` is expanded to hold per-tab user choices (starting with `selectedModelId`), and those choices are persisted per tab in Room.

---

## Section 1: Provider interfaces

Both interfaces live in `duckchat-impl` (not `duckchat-api`) because `NativeInputState` contains impl-level types. If an external consumer ever needs a specific derived value (e.g. `selectedModelId` for an omnibar feature), that signal is promoted to a dedicated `duckchat-api` interface at that time — YAGNI.

```kotlin
// duckchat-impl: com.duckduckgo.duckchat.impl.nativeinput.NativeInputStateProvider.kt

interface NativeInputStateProvider {
    /** Per-tab state flow. Creates an empty entry if tabId is not yet known. */
    fun stateForTab(tabId: String): StateFlow<NativeInputState>

    /**
     * Mirrors the currently active tab's state. Resets to zero NativeInputState
     * when no tab is active. For ambient UI consumers (e.g. NTP background logo).
     */
    val displayedState: StateFlow<NativeInputState>
}

interface MutableNativeInputStateProvider {
    /**
     * Called by the widget ViewModel when it attaches to a tab.
     * Merges [structural] fields with any persisted state already stored for [tabId].
     * Sets [tabId] as the active tab, driving [displayedState].
     */
    fun setActiveTab(tabId: String, structural: NativeInputState)

    /**
     * Applies [patch] to the current state for [tabId].
     * No-op if [tabId] has been cleared. Persists any persisted fields that changed.
     */
    fun update(tabId: String, patch: NativeInputState.() -> NativeInputState)

    /**
     * Called when a tab is closed. Removes in-memory state and deletes the DB row.
     * If [tabId] was the active tab, resets [displayedState] to zero NativeInputState.
     */
    fun clearTab(tabId: String)
}
```

`RealNativeInputStateProvider` is `@SingleInstanceIn(AppScope::class)` and bound to both interfaces:

```kotlin
@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = NativeInputStateProvider::class)
@ContributesBinding(AppScope::class, boundType = MutableNativeInputStateProvider::class)
class RealNativeInputStateProvider @Inject constructor(
    private val dao: NativeInputTabStateDao,
    @AppCoroutineScope private val appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : NativeInputStateProvider, MutableNativeInputStateProvider {

    private val tabFlows = ConcurrentHashMap<String, MutableStateFlow<NativeInputState>>()
    private val _displayedState = MutableStateFlow(NativeInputState.zero())
    override val displayedState: StateFlow<NativeInputState> = _displayedState.asStateFlow()
    private var activeTabId: String? = null

    override fun stateForTab(tabId: String): StateFlow<NativeInputState> =
        tabFlows.getOrPut(tabId) { MutableStateFlow(NativeInputState.zero()) }.asStateFlow()

    override fun setActiveTab(tabId: String, structural: NativeInputState) {
        activeTabId = tabId
        appScope.launch(ioDispatcher) {
            val persisted = dao.getTab(tabId)
            val merged = structural.copy(selectedModelId = persisted?.selectedModelId)
            tabFlows.getOrPut(tabId) { MutableStateFlow(NativeInputState.zero()) }.value = merged
            _displayedState.value = merged
        }
    }

    override fun update(tabId: String, patch: NativeInputState.() -> NativeInputState) {
        val flow = tabFlows[tabId] ?: return  // tab already cleared, no-op
        val old = flow.value
        val new = old.patch()
        flow.value = new
        if (tabId == activeTabId) _displayedState.value = new
        // Persist only if persisted fields changed
        if (old.selectedModelId != new.selectedModelId) {
            appScope.launch(ioDispatcher) {
                dao.upsert(NativeInputTabStateEntity(tabId = tabId, selectedModelId = new.selectedModelId))
            }
        }
    }

    override fun clearTab(tabId: String) {
        tabFlows.remove(tabId)
        if (activeTabId == tabId) {
            activeTabId = null
            _displayedState.value = NativeInputState.zero()
        }
        appScope.launch(ioDispatcher) { dao.delete(tabId) }
    }
}
```

---

## Section 2: NativeInputState expansion

Plugin-contributed fields are added with nullable/empty defaults so the struct is always valid:

```kotlin
data class NativeInputState(
    val inputMode: InputMode,
    val inputContext: InputContext,
    val inputPosition: InputPosition = InputPosition.TOP,
    // Plugin-contributed fields — null/empty = not yet set
    val selectedModelId: String? = null,
    val attachedImages: List<Uri> = emptyList(),
) {
    companion object {
        fun zero() = NativeInputState(
            inputMode = InputMode.SEARCH_ONLY,
            inputContext = InputContext.BROWSER,
        )
    }
    // ... existing computed properties unchanged
}
```

**Persistence policy per field:**

| Field | Persisted | Reason |
|---|---|---|
| `inputMode` | No | Global capability flag, not per-tab |
| `inputContext` | No | Reconstructed from navigation |
| `inputPosition` | No | Global user preference |
| `selectedModelId` | Yes | User choice, meaningful across restarts |
| `attachedImages` | No | `content://` URIs are ephemeral |

---

## Section 3: DB layer

```kotlin
// duckchat-impl: com.duckduckgo.duckchat.impl.nativeinput.db

@Entity(tableName = "native_input_tab_state")
data class NativeInputTabStateEntity(
    @PrimaryKey val tabId: String,
    val selectedModelId: String?,
)

@Dao
interface NativeInputTabStateDao {
    @Query("SELECT * FROM native_input_tab_state WHERE tabId = :tabId")
    suspend fun getTab(tabId: String): NativeInputTabStateEntity?

    @Upsert
    suspend fun upsert(entity: NativeInputTabStateEntity)

    @Query("DELETE FROM native_input_tab_state WHERE tabId = :tabId")
    suspend fun delete(tabId: String)
}
```

The DAO is registered in the existing DuckChat Room database. A migration adds the table; existing installs get an empty table (all `selectedModelId` values default to null on first launch — the model picker falls back to its own default).

---

## Section 4: NativeInputHost changes

One new method is added so plugins can retrieve the tabId at view-creation time:

```kotlin
interface NativeInputHost {
    fun submit()
    fun getInputState(): NativeInputState  // delegates to provider.stateForTab(tabId).value
    fun getTabId(): String                 // new
}
```

`NativeInputModeWidget` implements `getTabId()` by returning the tabId passed to it when the widget is attached to a tab.

---

## Section 5: Widget ViewModel integration

```kotlin
// NativeInputViewModel — new responsibilities

fun onAttachedToTab(tabId: String) {
    this.tabId = tabId
    mutableProvider.setActiveTab(
        tabId = tabId,
        structural = NativeInputState(
            inputMode = resolveInputMode(),
            inputContext = resolveInputContext(),
            inputPosition = resolveInputPosition(),
        )
    )
}

fun onInputContextChanged(newContext: NativeInputState.InputContext) {
    mutableProvider.update(tabId) { copy(inputContext = newContext) }
}

override fun onCleared() {
    // Reset ephemeral fields; selectedModelId intentionally preserved in DB
    mutableProvider.update(tabId) { copy(attachedImages = emptyList()) }
}
```

**Tab close** (separate from widget detach) calls `mutableProvider.clearTab(tabId)`, wired through the existing tab-close event in the app. The widget ViewModel's `onCleared()` does NOT call `clearTab` — that would delete the DB row prematurely.

**At send time**, the widget ViewModel reads from the provider instead of calling `getPromptContribution()`:

```kotlin
fun onSendTapped() {
    val state = provider.stateForTab(tabId).value
    val modelId = state.selectedModelId ?: defaultModelId
    // build and submit prompt
}
```

---

## Section 6: Plugin ViewModel integration (ModelPickerViewModel as example)

```kotlin
class ModelPickerNativeInputPlugin @Inject constructor(
    private val viewModelFactory: ViewModelProvider.Factory,
) : NativeInputPlugin {

    override val containerId: Int = R.id.modelPickerContainer

    override fun createView(context: Context, host: NativeInputHost): View {
        val tabId = host.getTabId()
        val store = ViewModelStore()
        val vm = ViewModelProvider(store, viewModelFactory)[ModelPickerViewModel::class.java]
        vm.init(tabId)
        return ModelPickerView(context, vm)
    }

    @Deprecated("Contributions are now pushed directly to NativeInputStateProvider")
    override fun getPromptContribution(): PromptContribution? = null
}

@ContributesViewModel(FragmentScope::class)
class ModelPickerViewModel @Inject constructor(
    private val provider: NativeInputStateProvider,
    private val mutableProvider: MutableNativeInputStateProvider,
) : ViewModel() {

    private lateinit var tabId: String

    fun init(tabId: String) {
        this.tabId = tabId
    }

    val selectedModel: StateFlow<String?> =
        provider.stateForTab(tabId)
            .map { it.selectedModelId }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun onModelSelected(modelId: String) {
        mutableProvider.update(tabId) { copy(selectedModelId = modelId) }
    }
}
```

The WeakReference<ModelPicker> in the current implementation is eliminated entirely — the ViewModel never holds a reference to the view.

---

## Section 7: getPromptContribution() migration path

`getPromptContribution()` is deprecated with a default `= null` implementation so existing plugins need no immediate changes:

```kotlin
interface NativeInputPlugin : ActivePlugin {
    val containerId: Int
    fun createView(context: Context, host: NativeInputHost): View

    @Deprecated("Push contributions to NativeInputStateProvider instead. Will be removed once all plugins migrate.")
    fun getPromptContribution(): PromptContribution? = null
}
```

The widget's send path stops calling `getPromptContribution()` immediately after this design lands. Plugins that still implement it are silently ignored.

Final removal: one cleanup PR deletes the method, the `PromptContribution` sealed class, and any remaining stub implementations. No behavior change.

---

## Files to create or modify

| Action | File | Responsibility |
|---|---|---|
| Create | `duckchat-impl/.../nativeinput/NativeInputStateProvider.kt` | Read + write interfaces |
| Create | `duckchat-impl/.../nativeinput/RealNativeInputStateProvider.kt` | App-scoped implementation |
| Create | `duckchat-impl/.../nativeinput/db/NativeInputTabStateEntity.kt` | Room entity |
| Create | `duckchat-impl/.../nativeinput/db/NativeInputTabStateDao.kt` | Room DAO |
| Modify | `duckchat-impl/.../nativeinput/NativeInputPlugin.kt` | Add `getTabId()` to host, deprecate `getPromptContribution()` |
| Modify | `duckchat-impl/.../ui/NativeInputState.kt` | Add `selectedModelId`, `attachedImages`, `zero()` |
| Modify | `duckchat-impl/.../ui/nativeinput/views/NativeInputModeWidget.kt` | Implement `getTabId()`, wire `setActiveTab`, update send path |
| Modify | `duckchat-impl/.../ui/NativeInputViewModel.kt` | Call `setActiveTab`, structural updates, `onCleared()` reset |
| Modify | `duckchat-impl/.../ui/nativeinput/plugins/ModelPickerNativeInputPlugin.kt` | Use `getTabId()`, create ViewModel, remove WeakReference |
| Create | `duckchat-impl/.../ui/nativeinput/ModelPickerViewModel.kt` | Observe + push `selectedModelId` |
| Modify | DuckChat Room database class | Register new entity + migration |
