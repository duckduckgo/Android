# Duck.ai Tab Title via ChatSuggestionsReader — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Display the correct duck.ai conversation title on each Duck.ai tab card in the tab switcher by fetching the full chat history from `ChatSuggestionsReader` and matching each tab's `chatID` URL parameter to its chat title.

**Architecture:** `ChatSuggestionsReader` (in `duckchat-impl`) gains a `fetchAllChats()` method — no 7-day filter, no count cap. `TabSwitcherViewModel` calls it once on creation, stores results in `_chatTitles: MutableStateFlow<Map<String, String>>` (chatId → title), and adds `_chatTitles` as a fourth param to the existing inner `combine` inside `tabSwitcherItemsFlow`. `DuckAiTab` gains an optional `chatTitle` field; the adapter casts `Tab` to `DuckAiTab` to read it.

**Tech Stack:** Kotlin, Coroutines, Dagger/Anvil DI, `ChatSuggestionsReader` (headless WebView / JS messaging), `TabSwitcherViewModel`, `TabSwitcherAdapter`, `TabSwitcherItem` sealed class

---

## Chunk 1: Add `fetchAllChats()` to ChatSuggestionsReader

### Task 1: Add `fetchAllChats()` to the interface and implementation

**Files:**
- Modify: `duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/inputscreen/ui/suggestions/reader/ChatSuggestionsReader.kt`
- Test: `duckchat/duckchat-impl/src/test/kotlin/com/duckduckgo/duckchat/impl/ui/inputscreen/suggestions/reader/RealChatSuggestionsReaderTest.kt`

#### Background

`fetchSuggestions(query)` currently:
1. Reads `maxSuggestions` from a feature flag (default 10)
2. Calls `fetchFromDomain(webView, domain, query, maxSuggestions)` for two domains
3. Inside `fetchFromDomain`, calls `buildFetchParams(query, maxSuggestions)` which:
   - Adds `"max_chats": maxSuggestions`
   - Adds `"since": now - 7days` when `query` is empty (JS side: `lastEdit < since` chats excluded)
4. Merges results via `mergeSuggestions(pinned, recent, maxSuggestions)` which does `.take(maxSuggestions)`

`fetchAllChats()` must skip both the `since` filter and the count cap. The minimal refactor:
- Move params-building out of `fetchFromDomain` so each call site controls its own `JSONObject`
- New `fetchAllChats()` passes `"max_chats": 1000` (large but bounded; JS default is 30 unpinned — we need more), no `"since"`, no `"query"`
- Calls `mergeSuggestions(..., Int.MAX_VALUE)` — `take(Int.MAX_VALUE)` on a shorter list returns all items

> **Why 1000 not `Int.MAX_VALUE` for JS `max_chats`?** The JS side does `chats.slice(0, max_chats)`. While `slice(0, 2147483647)` works in practice, using a bounded constant like `1000` makes intent clearer. A user would never have more than 1000 duck.ai chats.

#### Steps

- [ ] **Step 1: Add `fetchAllChats()` to the `ChatSuggestionsReader` interface**

```kotlin
interface ChatSuggestionsReader {
    suspend fun fetchSuggestions(query: String = ""): List<ChatSuggestion>
    suspend fun fetchAllChats(): List<ChatSuggestion>
    fun tearDown()
}
```

This causes a compilation error in `RealChatSuggestionsReader` (interface not fully implemented). That is the red signal.

- [ ] **Step 2: Verify the compilation fails**

```bash
cd /Users/malmstein/dev/repos/android/duckduckgo/.claude/worktrees/duck-ai-tab-manager
./gradlew :duckchat:duckchat-impl:compileDebugKotlin 2>&1 | grep -i "error" | head -10
```

Expected: compilation error about `fetchAllChats` not implemented.

- [ ] **Step 3: Refactor `fetchFromDomain` and implement `fetchAllChats()`**

**3a. Refactor `fetchFromDomain` to accept pre-built params:**

Change the signature from:
```kotlin
private suspend fun fetchFromDomain(webView: WebView, domain: String, query: String, maxSuggestions: Int): DomainResult?
```
to:
```kotlin
private suspend fun fetchFromDomain(webView: WebView, domain: String, params: JSONObject): DomainResult?
```

Inside `fetchFromDomain`, replace the `buildFetchParams(query, maxSuggestions)` call with `params` directly.

**3b. Update `fetchSuggestions` to build params before the domain loop:**

```kotlin
override suspend fun fetchSuggestions(query: String): List<ChatSuggestion> {
    return withContext(dispatchers.main()) {
        val maxSuggestions = getMaxHistoryCount()
        val script = getScript()
        val webView = getOrCreateWebView(script)
        val params = buildFetchParams(query, maxSuggestions)  // <-- build here
        val results = DOMAINS.mapNotNull { domain ->
            fetchFromDomain(webView, domain, params)           // <-- pass pre-built
        }
        val bestResult = results.maxByOrNull { r ->
            (r.pinnedChats + r.recentChats).maxOfOrNull { it.lastEdit } ?: LocalDateTime.MIN
        } ?: return@withContext emptyList()
        mergeSuggestions(bestResult.pinnedChats, bestResult.recentChats, maxSuggestions)
    }
}
```

**3c. Add `fetchAllChats()`:**

```kotlin
override suspend fun fetchAllChats(): List<ChatSuggestion> {
    return withContext(dispatchers.main()) {
        val script = getScript()
        val webView = getOrCreateWebView(script)
        val params = JSONObject().apply {
            put("max_chats", 1000) // return up to 1000 chats — no 'since' filter, no 'query' filter
        }
        val results = DOMAINS.mapNotNull { domain ->
            fetchFromDomain(webView, domain, params)
        }
        val bestResult = results.maxByOrNull { r ->
            (r.pinnedChats + r.recentChats).maxOfOrNull { it.lastEdit } ?: LocalDateTime.MIN
        } ?: return@withContext emptyList()
        mergeSuggestions(bestResult.pinnedChats, bestResult.recentChats, Int.MAX_VALUE)
    }
}
```

- [ ] **Step 4: Write a behavioral test for `mergeSuggestions` with no cap**

In `RealChatSuggestionsReaderTest.kt`, add inside the `mergeSuggestions` test region:

```kotlin
@Test
fun `mergeSuggestions with Int MAX_VALUE returns all items without truncation`() {
    val chats = (1..20).map { i ->
        ChatSuggestion(
            chatId = "id$i",
            title = "Chat $i",
            lastEdit = LocalDateTime.now().minusHours(i.toLong()),
            pinned = false,
        )
    }
    val result = reader.mergeSuggestions(emptyList(), chats, Int.MAX_VALUE)
    assertEquals(20, result.size)
}
```

> The reader instance is named `reader` in this test class (not `testee`). `mergeSuggestions` is already `@VisibleForTesting internal`.

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew :duckchat:duckchat-impl:testDebugUnitTest \
  --tests "com.duckduckgo.duckchat.impl.ui.inputscreen.suggestions.reader.RealChatSuggestionsReaderTest.mergeSuggestions with Int MAX_VALUE returns all items without truncation" 2>&1 | tail -20
```

Expected: PASS

- [ ] **Step 6: Run the full `duckchat-impl` test suite for regressions**

```bash
./gradlew :duckchat:duckchat-impl:testDebugUnitTest 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/inputscreen/ui/suggestions/reader/ChatSuggestionsReader.kt
git add duckchat/duckchat-impl/src/test/kotlin/com/duckduckgo/duckchat/impl/ui/inputscreen/suggestions/reader/RealChatSuggestionsReaderTest.kt
git commit -m "Add fetchAllChats() to ChatSuggestionsReader (no filter, no limit)"
```

---

## Chunk 2: Wire chat titles into the Tab Switcher

### Task 2: Add `chatTitle` to `DuckAiTab`

**Files:**
- Modify: `app/src/main/java/com/duckduckgo/app/tabs/ui/TabSwitcherItem.kt`

No test needed — adding a default-null field to a data class has no observable behaviour to test.

- [ ] **Step 1: Add `chatTitle: String?` with default `null`**

Current:
```kotlin
data class DuckAiTab(
    private val entity: TabEntity,
    val isActive: Boolean,
) : Tab(entity)
```

Change to:
```kotlin
data class DuckAiTab(
    private val entity: TabEntity,
    val isActive: Boolean,
    val chatTitle: String? = null,
) : Tab(entity)
```

The default preserves all existing call sites.

- [ ] **Step 2: Compile to verify no breakage**

```bash
./gradlew :app:compilePlayDebugKotlin 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/duckduckgo/app/tabs/ui/TabSwitcherItem.kt
git commit -m "Add optional chatTitle field to DuckAiTab"
```

---

### Task 3: Resolve chat titles in `TabSwitcherViewModel`

**Files:**
- Modify: `app/src/main/java/com/duckduckgo/app/tabs/ui/TabSwitcherViewModel.kt`
- Modify: `app/src/test/java/com/duckduckgo/app/tabs/ui/TabSwitcherViewModelTest.kt`

#### Background — exact flow structure

```kotlin
// Current tabSwitcherItemsFlow:
private val tabSwitcherItemsFlow = tabRepository.flowTabs
    .debounce(100.milliseconds)
    .conflate()
    .flatMapLatest { tabEntities ->
        combine(
            tabRepository.flowSelectedTab,   // param 1
            _viewState,                       // param 2
            tabSwitcherDataStore.isTrackersAnimationInfoTileHidden(), // param 3
        ) { activeTab, viewState, isAnimationTileDismissed ->
            getTabItems(tabEntities, activeTab, isAnimationTileDismissed, viewState.mode)
        }
    }
```

We add `_chatTitles` as **param 4** to the inner `combine` (within the 5-param limit). When `fetchAllChats()` completes and updates `_chatTitles`, the inner combine re-emits, refreshing the tab list with resolved titles.

#### Current `getTabItems` signature

```kotlin
private suspend fun getTabItems(
    tabEntities: List<TabEntity>,
    activeTab: TabEntity?,
    isTrackersAnimationInfoPanelHidden: Boolean,
    mode: Mode,
): List<TabSwitcherItem>
```

#### Test setup pattern (from existing tests)

- Tabs injected via `whenever(mockTabRepository.flowTabs).thenReturn(flowOf(tabList))` inside `initializeMockTabEntitesData()`
- ViewModel constructed in `initializeViewModel(...)` — must add the new mock parameter
- Tab items observed via `testee.tabSwitcherItemsLiveData.blockingObserve()` (extension from `com.duckduckgo.common.test.blockingObserve`)
- `advanceUntilIdle()` advances coroutines

#### Steps

- [ ] **Step 1: Write the two failing tests**

In `TabSwitcherViewModelTest.kt`, add `mockChatSuggestionsReader` to the mocks section:

```kotlin
private val mockChatSuggestionsReader: ChatSuggestionsReader = mock()
```

Import:
```kotlin
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.reader.ChatSuggestionsReader
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatSuggestion
import java.time.LocalDateTime
```

Extend `initializeViewModel()` to include the new param (after the last existing param `mockOmnibarFeatureRepository`):

```kotlin
private fun initializeViewModel(tabSwitcherDataStore: TabSwitcherDataStore = mockTabSwitcherPrefsDataStore) {
    testee = TabSwitcherViewModel(
        mockTabRepository,
        coroutinesTestRule.testDispatcherProvider,
        mockPixel,
        swipingTabsFeatureProvider,
        duckChatMock,
        duckAiFeatureState = duckAiFeatureStateMock,
        mockWebTrackersBlockedAppRepository,
        tabSwitcherDataStore,
        faviconManager,
        savedSitesRepository,
        mockTrackersAnimationInfoPanelPixels,
        mockOmnibarFeatureRepository,
        mockChatSuggestionsReader,  // <-- new
    )
    testee.command.observeForever(mockCommandObserver)
    testee.tabSwitcherItemsLiveData.observeForever(mockTabSwitcherItemsObserver)
}
```

In the `setUp` (or wherever the default mock behaviours are set), add:

```kotlin
whenever(mockChatSuggestionsReader.fetchAllChats()).thenReturn(emptyList())
```

Add the two new tests (near existing duck.ai tab tests):

```kotlin
@Test
fun `duck ai tab with matching chatId shows resolved chat title`() = runTest {
    val chatId = "abc-123"
    val expectedTitle = "Explain quantum entanglement"
    val duckAiUrl = "https://duck.ai/chat?chatID=$chatId"

    whenever(mockChatSuggestionsReader.fetchAllChats()).thenReturn(
        listOf(
            ChatSuggestion(
                chatId = chatId,
                title = expectedTitle,
                lastEdit = LocalDateTime.now(),
                pinned = false,
            )
        )
    )
    whenever(duckChatMock.isDuckChatUrl(Uri.parse(duckAiUrl))).thenReturn(true)
    tabList.clear()
    tabList.add(TabEntity(tabId = "tab1", url = duckAiUrl))
    initializeMockTabEntitesData()
    initializeViewModel()

    advanceUntilIdle()

    val items = testee.tabSwitcherItemsLiveData.blockingObserve()!!
    val duckAiTab = items.filterIsInstance<TabSwitcherItem.Tab.DuckAiTab>().first()
    assertEquals(expectedTitle, duckAiTab.chatTitle)
}

@Test
fun `duck ai tab with no matching chatId has null chatTitle`() = runTest {
    val duckAiUrl = "https://duck.ai/chat?chatID=unknown-id"

    whenever(mockChatSuggestionsReader.fetchAllChats()).thenReturn(emptyList())
    whenever(duckChatMock.isDuckChatUrl(Uri.parse(duckAiUrl))).thenReturn(true)
    tabList.clear()
    tabList.add(TabEntity(tabId = "tab1", url = duckAiUrl))
    initializeMockTabEntitesData()
    initializeViewModel()

    advanceUntilIdle()

    val items = testee.tabSwitcherItemsLiveData.blockingObserve()!!
    val duckAiTab = items.filterIsInstance<TabSwitcherItem.Tab.DuckAiTab>().first()
    assertNull(duckAiTab.chatTitle)
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/malmstein/dev/repos/android/duckduckgo/.claude/worktrees/duck-ai-tab-manager
./gradlew :app:testPlayDebugUnitTest \
  --tests "com.duckduckgo.app.tabs.ui.TabSwitcherViewModelTest.duck ai tab with matching chatId shows resolved chat title" 2>&1 | tail -30
```

Expected: FAIL (compilation error — `ChatSuggestionsReader` param not in constructor yet)

- [ ] **Step 3: Inject `ChatSuggestionsReader` and add `_chatTitles`**

**3a. Add constructor parameter** (scope is `ActivityScope::class`):

```kotlin
@ContributesViewModel(ActivityScope::class)
class TabSwitcherViewModel @Inject constructor(
    // ... all existing params unchanged ...
    private val chatSuggestionsReader: ChatSuggestionsReader,
) : ViewModel()
```

**3b. Add `_chatTitles` field and populate in `init`:**

```kotlin
private val _chatTitles = MutableStateFlow<Map<String, String>>(emptyMap())

init {
    // after existing init code
    viewModelScope.launch(dispatcherProvider.io()) {
        val titles = chatSuggestionsReader.fetchAllChats()
            .associate { it.chatId to it.title }
        _chatTitles.value = titles
    }
}
```

**3c. Add `_chatTitles` as fourth param in the inner `combine` inside `tabSwitcherItemsFlow`:**

```kotlin
private val tabSwitcherItemsFlow = tabRepository.flowTabs
    .debounce(100.milliseconds)
    .conflate()
    .flatMapLatest { tabEntities ->
        combine(
            tabRepository.flowSelectedTab,
            _viewState,
            tabSwitcherDataStore.isTrackersAnimationInfoTileHidden(),
            _chatTitles,
        ) { activeTab, viewState, isAnimationTileDismissed, chatTitles ->
            getTabItems(tabEntities, activeTab, isAnimationTileDismissed, viewState.mode, chatTitles)
        }
    }
```

**3d. Update `getTabItems` to accept and use `chatTitles`:**

```kotlin
private suspend fun getTabItems(
    tabEntities: List<TabEntity>,
    activeTab: TabEntity?,
    isTrackersAnimationInfoPanelHidden: Boolean,
    mode: Mode,
    chatTitles: Map<String, String>,
): List<TabSwitcherItem> {
    val normalTabs = tabEntities.map { entity ->
        val isActive = entity.tabId == activeTab?.tabId
        if (entity.url != null && duckChat.isDuckChatUrl(Uri.parse(entity.url))) {
            val chatId = Uri.parse(entity.url).getQueryParameter("chatID")
            val resolvedTitle = chatId?.let { chatTitles[it] }
            DuckAiTab(entity, isActive, resolvedTitle)
        } else {
            NormalTab(entity, isActive)
        }
    }
    // ... rest of existing logic unchanged
}
```

- [ ] **Step 4: Run both tests to verify they pass**

```bash
./gradlew :app:testPlayDebugUnitTest \
  --tests "com.duckduckgo.app.tabs.ui.TabSwitcherViewModelTest.duck ai tab with*" 2>&1 | tail -30
```

Expected: both PASS

- [ ] **Step 5: Run full `TabSwitcherViewModelTest` suite for regressions**

```bash
./gradlew :app:testPlayDebugUnitTest \
  --tests "com.duckduckgo.app.tabs.ui.TabSwitcherViewModelTest" 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/duckduckgo/app/tabs/ui/TabSwitcherViewModel.kt
git add app/src/test/java/com/duckduckgo/app/tabs/ui/TabSwitcherViewModelTest.kt
git commit -m "Resolve duck.ai chat titles in TabSwitcherViewModel via ChatSuggestionsReader"
```

---

### Task 4: Display resolved title in `TabSwitcherAdapter`

**Files:**
- Modify: `app/src/main/java/com/duckduckgo/app/tabs/ui/TabSwitcherAdapter.kt`

No tests needed — this is a pure display layer with no logic; the ViewModel test already validates that `chatTitle` is set correctly.

#### Current display logic

Both `bindDuckAiGridTab` and `bindDuckAiListTab` receive `tab: Tab` (the sealed class parent — not `DuckAiTab` directly). At runtime the value is always a `DuckAiTab` when these methods are called. Access `chatTitle` via a safe cast.

- **Grid view** (`bindDuckAiGridTab`): `holder.title.text = tab.tabEntity.title ?: ""`
- **List view** (`bindDuckAiListTab`): `holder.url.text = tab.tabEntity.title ?: ""`

Both fall back to `tabEntity.title` (WebView page title), then empty string.

- [ ] **Step 1: Update `bindDuckAiGridTab`**

Replace:
```kotlin
holder.title.text = tab.tabEntity.title ?: ""
```
With:
```kotlin
val chatTitle = (tab as? DuckAiTab)?.chatTitle
holder.title.text = chatTitle ?: tab.tabEntity.title ?: ""
```

- [ ] **Step 2: Update `bindDuckAiListTab`**

Replace:
```kotlin
holder.url.text = tab.tabEntity.title ?: ""
```
With:
```kotlin
val chatTitle = (tab as? DuckAiTab)?.chatTitle
holder.url.text = chatTitle ?: tab.tabEntity.title ?: ""
```

- [ ] **Step 3: Check selectable ViewHolder methods**

Search for any `bindSelectableDuckAi*` or similar methods that also display the duck.ai title. If found, apply the same `(tab as? DuckAiTab)?.chatTitle` fallback pattern.

- [ ] **Step 4: Compile and run full test suite**

```bash
./gradlew jvm_tests 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/duckduckgo/app/tabs/ui/TabSwitcherAdapter.kt
git commit -m "Use resolved chat title in DuckAiTab adapter views"
```
