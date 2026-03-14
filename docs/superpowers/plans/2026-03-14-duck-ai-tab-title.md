# Duck.ai Tab Title Retrieval Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow the duck.ai web app to push a conversation title to native Android so it appears in the tab switcher.

**Architecture:** The duck.ai web app sends a JS message (`aiChat.setTabTitle`) with a `title` field. Native registers this method in the existing `DuckChatContentScopeJsMessageHandler`, and `BrowserTabViewModel.processJsCallbackMessage` intercepts it to call the already-existing `updateTabTitle(tabId, title)`. No new classes needed — this is a small addition to two files.

**Tech Stack:** Kotlin, JS messaging (`ContentScopeJsMessageHandlersPlugin`), `BrowserTabViewModel`, Room via `TabRepository`

---

## Chunk 1: Register and handle `setTabTitle`

### Task 1: Add `setTabTitle` method constant to `RealDuckChatJSHelper`

**Files:**
- Modify: `duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/helper/DuckChatJSHelper.kt`

The method name constant must live next to the other method constants so it can be imported wherever needed.

- [ ] **Step 1: Add the constant to `RealDuckChatJSHelper.Companion`**

In `DuckChatJSHelper.kt`, find the `companion object` block of `RealDuckChatJSHelper` and add:

```kotlin
const val METHOD_SET_TAB_TITLE = "setTabTitle"
```

Place it after line `const val METHOD_GET_PAGE_CONTEXT = "getAIChatPageContext"` (line ~327) and before `const val METHOD_OPEN_KEYBOARD = "openKeyboard"`.

> **Note:** There is no test for this step — adding a constant has no behaviour to test. This is intentional.

- [ ] **Step 2: Compile to verify no issues**

```bash
cd /Users/malmstein/dev/repos/android/duckduckgo/.claude/worktrees/duck-ai-tab-manager
./gradlew :duckchat:duckchat-impl:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
cd /Users/malmstein/dev/repos/android/duckduckgo/.claude/worktrees/duck-ai-tab-manager
git add duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/helper/DuckChatJSHelper.kt
git commit -m "Add METHOD_SET_TAB_TITLE constant to RealDuckChatJSHelper"
```

---

### Task 2: Register `setTabTitle` in the JS message handler

**Files:**
- Modify: `duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/messaging/DuckChatContentScopeJsMessageHandler.kt`

> **Important:** Target the file in the `messaging/` package. There is a similarly-named file at `duckchat-impl/src/main/java/.../ui/settings/` — do NOT edit that one.

The handler's `methods` list tells the content scope JS bridge which method names to route to this handler's `JsMessageCallback`. Without registering it here, the web app's call is silently dropped before it reaches the ViewModel.

- [ ] **Step 1: Write the failing test**

In `duckchat/duckchat-impl/src/test/java/com/duckduckgo/duckchat/impl/messaging/DuckChatContentScopeJsMessageHandlerTest.kt` (create if it doesn't exist), add:

```kotlin
package com.duckduckgo.duckchat.impl.messaging

import com.duckduckgo.duckchat.impl.helper.RealDuckChatJSHelper.Companion.METHOD_SET_TAB_TITLE
import org.junit.Assert.assertTrue
import org.junit.Test

class DuckChatContentScopeJsMessageHandlerTest {

    private val handler = DuckChatContentScopeJsMessageHandler()

    @Test
    fun `setTabTitle is in the registered methods list`() {
        val methods = handler.getJsMessageHandler().methods
        assertTrue("setTabTitle must be registered", methods.contains(METHOD_SET_TAB_TITLE))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :duckchat:duckchat-impl:testDebugUnitTest \
  --tests "com.duckduckgo.duckchat.impl.messaging.DuckChatContentScopeJsMessageHandlerTest"
```

Expected: FAIL — `setTabTitle` is not in the list yet.

- [ ] **Step 3: Add `METHOD_SET_TAB_TITLE` to the methods list**

In `DuckChatContentScopeJsMessageHandler.kt`, add the import:

```kotlin
import com.duckduckgo.duckchat.impl.helper.RealDuckChatJSHelper.Companion.METHOD_SET_TAB_TITLE
```

And in the `methods` list, append:

```kotlin
METHOD_SET_TAB_TITLE,
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew :duckchat:duckchat-impl:testDebugUnitTest \
  --tests "com.duckduckgo.duckchat.impl.messaging.DuckChatContentScopeJsMessageHandlerTest"
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/messaging/DuckChatContentScopeJsMessageHandler.kt
git add duckchat/duckchat-impl/src/test/java/com/duckduckgo/duckchat/impl/messaging/DuckChatContentScopeJsMessageHandlerTest.kt
git commit -m "Register setTabTitle in DuckChatContentScopeJsMessageHandler"
```

---

### Task 3: Handle `setTabTitle` in `BrowserTabViewModel`

**Files:**
- Modify: `app/src/main/java/com/duckduckgo/app/browser/BrowserTabViewModel.kt`
- Modify: `app/src/test/java/com/duckduckgo/app/browser/BrowserTabViewModelTest.kt`

When the `aiChat.setTabTitle` message arrives, `processJsCallbackMessage` routes it via the `DUCK_CHAT_FEATURE_NAME` when-branch. We add an early check: if `method == METHOD_SET_TAB_TITLE`, extract the title from `data`, call `updateTabTitle(tabId, title)`, and return — skipping the `duckChatJSHelper` delegation (which would just log-and-ignore it anyway).

The web app sends:
```json
{ "title": "Explain quantum entanglement" }
```

- [ ] **Step 1: Write the failing test**

Find the section of `BrowserTabViewModelTest.kt` that contains `processJsCallbackMessage` tests (around line 5330) and add:

```kotlin
@Test
fun `when setTabTitle message received then tab title is updated`() =
    runTest {
        loadUrl("https://duck.ai/chat")
        val title = "Explain quantum entanglement"
        testee.processJsCallbackMessage(
            DUCK_CHAT_FEATURE_NAME,
            METHOD_SET_TAB_TITLE,
            null,
            JSONObject("""{"title":"$title"}"""),
            false,
        ) { "https://duck.ai/chat" }
        val siteCaptor = argumentCaptor<Site>()
        verify(mockTabRepository).update(any(), siteCaptor.capture())
        assertEquals(title, siteCaptor.firstValue.title)
    }

@Test
fun `when setTabTitle message received with empty title then tab title is not updated`() =
    runTest {
        loadUrl("https://duck.ai/chat")
        testee.processJsCallbackMessage(
            DUCK_CHAT_FEATURE_NAME,
            METHOD_SET_TAB_TITLE,
            null,
            JSONObject("""{"title":""}"""),
            false,
        ) { "https://duck.ai/chat" }
        verify(mockTabRepository, never()).update(any(), argThat { it?.title?.isEmpty() == true })
    }
```

> **Note:** The mock is `mockTabRepository` (no trailing `s`). `updateTabTitle` calls `tabRepository.update(tabId, site)` after mutating `site.title` — verify against `update`, not `updateUrlAndTitle`.

Add the import at the top of the test file:
```kotlin
import com.duckduckgo.duckchat.impl.helper.RealDuckChatJSHelper.Companion.METHOD_SET_TAB_TITLE
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew :app:testPlayDebugUnitTest \
  --tests "com.duckduckgo.app.browser.BrowserTabViewModelTest.when setTabTitle message received then tab title is updated"
```

Expected: FAIL

- [ ] **Step 3: Add the import and handler in `BrowserTabViewModel`**

Add import near the other DuckChatJSHelper imports:
```kotlin
import com.duckduckgo.duckchat.impl.helper.RealDuckChatJSHelper.Companion.METHOD_SET_TAB_TITLE
```

In `processJsCallbackMessage`, find the `DUCK_CHAT_FEATURE_NAME ->` block (around line 4190) and add the `setTabTitle` check before the existing `viewModelScope.launch`:

```kotlin
DUCK_CHAT_FEATURE_NAME -> {
    if (method == METHOD_SET_TAB_TITLE) {
        val title = data?.optString("title")?.takeIf { it.isNotEmpty() } ?: return
        viewModelScope.launch(dispatchers.io()) { updateTabTitle(tabId, title) }
        return
    }
    viewModelScope.launch(dispatchers.io()) {
        val response = duckChatJSHelper.processJsCallbackMessage(
            featureName,
            method,
            id,
            data,
        )
        withContext(dispatchers.main()) {
            response?.let {
                command.value = SendResponseToJs(it)
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew :app:testPlayDebugUnitTest \
  --tests "com.duckduckgo.app.browser.BrowserTabViewModelTest.when setTabTitle*"
```

Expected: both tests PASS

- [ ] **Step 5: Run the full app test suite to check for regressions**

```bash
./gradlew jvm_tests
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/duckduckgo/app/browser/BrowserTabViewModel.kt
git add app/src/test/java/com/duckduckgo/app/browser/BrowserTabViewModelTest.kt
git commit -m "Handle aiChat.setTabTitle JS message to update Duck.ai tab title"
```
