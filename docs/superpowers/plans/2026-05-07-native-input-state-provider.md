# NativeInputStateProvider Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the pull-based `getPromptContribution()` model with a push-based per-tab `NativeInputStateProvider` backed by Room, so plugins write their contributions into `NativeInputState` directly and that state survives app restarts.

**Architecture:** An app-scoped `RealNativeInputStateProvider` holds `Map<TabId, MutableStateFlow<NativeInputState>>` in memory and persists tab-specific fields (`selectedModelId`) in Room. The widget ViewModel calls `setActiveTab` when it configures for a tab; plugin ViewModels inject the provider and call `update(tabId)` when the user makes choices. At send time the widget reads from the provider instead of polling plugins.

**Tech Stack:** Kotlin Coroutines (`StateFlow`, `flatMapLatest`), Anvil/Dagger2 (`@SingleInstanceIn(AppScope::class)`, `@ContributesBinding`), Room (DAO + Entity, `@Upsert`), Mockito-Kotlin for tests, `CoroutineTestRule`.

**Worktree:** `/Users/malmstein/dev/repos/android/duckduckgo/.claude/worktrees/native-input-provider`

---

## File Map

| Action | File | Responsibility |
|---|---|---|
| Modify | `duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/ui/NativeInputState.kt` | Add `selectedModelId`, `attachedImages`, `zero()` |
| Create | `duckchat/duckchat-store/src/main/java/com/duckduckgo/duckchat/store/impl/store/NativeInputTabStateEntity.kt` | Room entity for per-tab persisted state |
| Create | `duckchat/duckchat-store/src/main/java/com/duckduckgo/duckchat/store/impl/store/NativeInputTabStateDao.kt` | CRUD DAO for `native_input_tab_state` table |
| Modify | `duckchat/duckchat-store/src/main/java/com/duckduckgo/duckchat/store/impl/store/DuckAiBridgeDatabase.kt` | Add entity, bump version to 4, add migration |
| Create | `duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/nativeinput/NativeInputStateProvider.kt` | `NativeInputStateProvider` + `MutableNativeInputStateProvider` interfaces |
| Create | `duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/nativeinput/RealNativeInputStateProvider.kt` | App-scoped implementation bound to both interfaces |
| Modify | `duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/nativeinput/NativeInputPlugin.kt` | Add `getTabId()` to `NativeInputHost`; add default `null` impl to `getPromptContribution()` |
| Modify | `duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/ui/nativeinput/views/NativeInputModeWidget.kt` | Store `tabId`; implement `getTabId()`; set tabId in `configure()` / `configureContextual()` |
| Modify | `duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/ui/NativeInputModeWidgetViewModel.kt` | Inject providers; add `tabId` param to `configure()`; call `setActiveTab`; read from provider at send time |
| Modify | `duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/ui/nativeinput/views/ModelPickerViewModel.kt` | Inject providers; add `init(tabId)`; push `selectedModelId` on selection |
| Modify | `duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/ui/nativeinput/plugins/ModelPickerNativeInputPlugin.kt` | Call `host.getTabId()`, pass to ViewModel |
| Create | `duckchat/duckchat-store/src/test/kotlin/com/duckduckgo/duckchat/store/impl/NativeInputTabStateDaoTest.kt` | DAO CRUD tests |
| Create | `duckchat/duckchat-impl/src/test/kotlin/com/duckduckgo/duckchat/impl/nativeinput/RealNativeInputStateProviderTest.kt` | Provider unit tests |
| Modify | `duckchat/duckchat-impl/src/test/kotlin/com/duckduckgo/duckchat/impl/ui/ModelPickerViewModelTest.kt` | Tests for new provider-push behaviour |
| Modify | `duckchat/duckchat-impl/src/test/kotlin/com/duckduckgo/duckchat/impl/ui/NativeInputModeWidgetViewModelTest.kt` | Tests for setActiveTab + provider-read send path |

---

## Task 1: Expand NativeInputState

**Files:**
- Modify: `duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/ui/NativeInputState.kt`
- Test: `duckchat/duckchat-impl/src/test/kotlin/com/duckduckgo/duckchat/impl/ui/NativeInputStateTest.kt` (new file)

- [ ] **Step 1: Write failing tests**

Create `duckchat/duckchat-impl/src/test/kotlin/com/duckduckgo/duckchat/impl/ui/NativeInputStateTest.kt`:

```kotlin
package com.duckduckgo.duckchat.impl.ui

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeInputStateTest {

    @Test
    fun whenZeroThenInputModeIsSearchOnly() {
        assertEquals(NativeInputState.InputMode.SEARCH_ONLY, NativeInputState.zero().inputMode)
    }

    @Test
    fun whenZeroThenInputContextIsBrowser() {
        assertEquals(NativeInputState.InputContext.BROWSER, NativeInputState.zero().inputContext)
    }

    @Test
    fun whenZeroThenInputPositionIsTop() {
        assertEquals(NativeInputState.InputPosition.TOP, NativeInputState.zero().inputPosition)
    }

    @Test
    fun whenZeroThenSelectedModelIdIsNull() {
        assertNull(NativeInputState.zero().selectedModelId)
    }

    @Test
    fun whenZeroThenAttachedImagesIsEmpty() {
        assertTrue(NativeInputState.zero().attachedImages.isEmpty())
    }

    @Test
    fun whenCopyingWithSelectedModelIdThenValueIsPreserved() {
        val state = NativeInputState.zero().copy(selectedModelId = "claude-3")
        assertEquals("claude-3", state.selectedModelId)
    }

    @Test
    fun whenCopyingWithAttachedImagesThenValueIsPreserved() {
        val uri = Uri.parse("content://fake/image.jpg")
        val state = NativeInputState.zero().copy(attachedImages = listOf(uri))
        assertEquals(listOf(uri), state.attachedImages)
    }

    @Test
    fun whenToggleVisibleAndSearchOnlyModeThenToggleNotVisible() {
        val state = NativeInputState.zero() // SEARCH_ONLY
        assertTrue(!state.toggleVisible)
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cd /Users/malmstein/dev/repos/android/duckduckgo/.claude/worktrees/native-input-provider
./gradlew :duckchat-impl:testDebugUnitTest --tests "com.duckduckgo.duckchat.impl.ui.NativeInputStateTest" 2>&1 | tail -20
```

Expected: FAIL — `zero()` method not found, `selectedModelId` not found.

- [ ] **Step 3: Update NativeInputState.kt**

Replace the entire file content:

```kotlin
/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.duckchat.impl.ui

import android.net.Uri

data class NativeInputState(
    val inputMode: InputMode,
    val inputContext: InputContext,
    val inputPosition: InputPosition = InputPosition.TOP,
    /** Model explicitly chosen by the user for this tab. Null = use global default. */
    val selectedModelId: String? = null,
    /** Images attached to the current message. Not persisted across restarts. */
    val attachedImages: List<Uri> = emptyList(),
) {
    enum class InputMode {
        SEARCH_AND_DUCK_AI,
        SEARCH_ONLY,
    }

    enum class InputContext { BROWSER, DUCK_AI, DUCK_AI_CONTEXTUAL }

    enum class ToggleSelection { SEARCH, DUCK_AI }

    enum class InputPosition { TOP, BOTTOM }

    val toggleVisible: Boolean get() = inputMode == InputMode.SEARCH_AND_DUCK_AI && inputContext != InputContext.DUCK_AI_CONTEXTUAL

    val isBottom: Boolean get() = inputPosition == InputPosition.BOTTOM

    val defaultToggleSelection: ToggleSelection
        get() = when (inputContext) {
            InputContext.DUCK_AI, InputContext.DUCK_AI_CONTEXTUAL -> ToggleSelection.DUCK_AI
            InputContext.BROWSER -> ToggleSelection.SEARCH
        }

    companion object {
        fun zero() = NativeInputState(
            inputMode = InputMode.SEARCH_ONLY,
            inputContext = InputContext.BROWSER,
        )
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./gradlew :duckchat-impl:testDebugUnitTest --tests "com.duckduckgo.duckchat.impl.ui.NativeInputStateTest" 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Run full module tests to catch regressions**

```bash
./gradlew :duckchat-impl:testDebugUnitTest 2>&1 | tail -15
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/ui/NativeInputState.kt \
        duckchat/duckchat-impl/src/test/kotlin/com/duckduckgo/duckchat/impl/ui/NativeInputStateTest.kt
git commit -m "Expand NativeInputState with plugin-contributed fields and zero()"
```

---

## Task 2: NativeInputTabState DB layer

**Files:**
- Create: `duckchat/duckchat-store/src/main/java/com/duckduckgo/duckchat/store/impl/store/NativeInputTabStateEntity.kt`
- Create: `duckchat/duckchat-store/src/main/java/com/duckduckgo/duckchat/store/impl/store/NativeInputTabStateDao.kt`
- Create: `duckchat/duckchat-store/src/test/kotlin/com/duckduckgo/duckchat/store/impl/NativeInputTabStateDaoTest.kt`

- [ ] **Step 1: Write failing DAO test**

Create `duckchat/duckchat-store/src/test/kotlin/com/duckduckgo/duckchat/store/impl/NativeInputTabStateDaoTest.kt`:

```kotlin
package com.duckduckgo.duckchat.store.impl

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeDatabase
import com.duckduckgo.duckchat.store.impl.store.NativeInputTabStateDao
import com.duckduckgo.duckchat.store.impl.store.NativeInputTabStateEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeInputTabStateDaoTest {

    private lateinit var db: DuckAiBridgeDatabase
    private lateinit var dao: NativeInputTabStateDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DuckAiBridgeDatabase::class.java,
        ).build()
        dao = db.nativeInputTabStateDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun whenGetUnknownTabThenReturnsNull() {
        assertNull(dao.getTab("unknown-tab"))
    }

    @Test
    fun whenUpsertThenGetReturnsEntity() {
        val entity = NativeInputTabStateEntity(tabId = "tab-1", selectedModelId = "claude-3")
        dao.upsert(entity)

        val result = dao.getTab("tab-1")
        assertEquals("claude-3", result?.selectedModelId)
    }

    @Test
    fun whenUpsertTwiceThenLatestValueWins() {
        dao.upsert(NativeInputTabStateEntity(tabId = "tab-1", selectedModelId = "claude-3"))
        dao.upsert(NativeInputTabStateEntity(tabId = "tab-1", selectedModelId = "gpt-4o"))

        assertEquals("gpt-4o", dao.getTab("tab-1")?.selectedModelId)
    }

    @Test
    fun whenDeleteThenGetReturnsNull() {
        dao.upsert(NativeInputTabStateEntity(tabId = "tab-1", selectedModelId = "claude-3"))
        dao.delete("tab-1")

        assertNull(dao.getTab("tab-1"))
    }

    @Test
    fun whenDeleteUnknownTabThenNoException() {
        dao.delete("never-existed")
    }

    @Test
    fun whenSelectedModelIdIsNullThenPersistedAsNull() {
        dao.upsert(NativeInputTabStateEntity(tabId = "tab-1", selectedModelId = null))
        assertNull(dao.getTab("tab-1")?.selectedModelId)
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
./gradlew :duckchat-store:testDebugUnitTest --tests "com.duckduckgo.duckchat.store.impl.NativeInputTabStateDaoTest" 2>&1 | tail -20
```

Expected: FAIL — `NativeInputTabStateEntity` not found.

- [ ] **Step 3: Create entity**

Create `duckchat/duckchat-store/src/main/java/com/duckduckgo/duckchat/store/impl/store/NativeInputTabStateEntity.kt`:

```kotlin
/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.duckchat.store.impl.store

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists per-tab native input state that should survive app restarts.
 * Only stores fields that represent deliberate user choices (e.g. model selection).
 * Structural fields (inputMode, inputContext) and ephemeral fields (attachedImages) are NOT stored here.
 */
@Entity(tableName = "native_input_tab_state")
data class NativeInputTabStateEntity(
    @PrimaryKey val tabId: String,
    val selectedModelId: String?,
)
```

- [ ] **Step 4: Create DAO**

Create `duckchat/duckchat-store/src/main/java/com/duckduckgo/duckchat/store/impl/store/NativeInputTabStateDao.kt`:

```kotlin
/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.duckchat.store.impl.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NativeInputTabStateDao {

    @Query("SELECT * FROM native_input_tab_state WHERE tabId = :tabId")
    fun getTab(tabId: String): NativeInputTabStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: NativeInputTabStateEntity)

    @Query("DELETE FROM native_input_tab_state WHERE tabId = :tabId")
    fun delete(tabId: String)
}
```

- [ ] **Step 5: Run test to confirm it still fails (database not wired yet)**

```bash
./gradlew :duckchat-store:testDebugUnitTest --tests "com.duckduckgo.duckchat.store.impl.NativeInputTabStateDaoTest" 2>&1 | tail -10
```

Expected: FAIL — `nativeInputTabStateDao()` not found on `DuckAiBridgeDatabase`.

- [ ] **Step 6: Update DuckAiBridgeDatabase — add entity, accessor, migration**

Replace `duckchat/duckchat-store/src/main/java/com/duckduckgo/duckchat/store/impl/store/DuckAiBridgeDatabase.kt`:

```kotlin
/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.duckchat.store.impl.store

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DuckAiBridgeChatEntity::class,
        DuckAiBridgeSettingEntity::class,
        DuckAiBridgeFileMetaEntity::class,
        NativeInputTabStateEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class DuckAiBridgeDatabase : RoomDatabase() {
    abstract fun chatsDao(): DuckAiBridgeChatsDao
    abstract fun settingsDao(): DuckAiBridgeSettingsDao
    abstract fun fileMetaDao(): DuckAiBridgeFileMetaDao
    abstract fun nativeInputTabStateDao(): NativeInputTabStateDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `duck_ai_file_meta` " +
                        "(`uuid` TEXT NOT NULL, `chatId` TEXT NOT NULL, " +
                        "`fileName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, " +
                        "PRIMARY KEY(`uuid`))",
                )
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_duck_ai_file_meta_chatId` ON `duck_ai_file_meta` (`chatId`)",
                )
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `native_input_tab_state` " +
                        "(`tabId` TEXT NOT NULL, `selectedModelId` TEXT, " +
                        "PRIMARY KEY(`tabId`))",
                )
            }
        }
    }
}
```

- [ ] **Step 7: Register the migration in DuckAiBridgeModule**

Edit `duckchat/duckchat-store/src/main/java/com/duckduckgo/duckchat/store/impl/DuckAiBridgeModule.kt` line 40.

Find:
```kotlin
.addMigrations(DuckAiBridgeDatabase.MIGRATION_1_2, DuckAiBridgeDatabase.MIGRATION_2_3)
```

Replace with:
```kotlin
.addMigrations(DuckAiBridgeDatabase.MIGRATION_1_2, DuckAiBridgeDatabase.MIGRATION_2_3, DuckAiBridgeDatabase.MIGRATION_3_4)
```

- [ ] **Step 8: Run DAO tests to confirm they pass**

```bash
./gradlew :duckchat-store:testDebugUnitTest --tests "com.duckduckgo.duckchat.store.impl.NativeInputTabStateDaoTest" 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, all 6 tests pass.

- [ ] **Step 9: Commit**

```bash
git add duckchat/duckchat-store/src/main/java/com/duckduckgo/duckchat/store/impl/store/NativeInputTabStateEntity.kt \
        duckchat/duckchat-store/src/main/java/com/duckduckgo/duckchat/store/impl/store/NativeInputTabStateDao.kt \
        duckchat/duckchat-store/src/main/java/com/duckduckgo/duckchat/store/impl/store/DuckAiBridgeDatabase.kt \
        duckchat/duckchat-store/src/test/kotlin/com/duckduckgo/duckchat/store/impl/NativeInputTabStateDaoTest.kt
git commit -m "Add NativeInputTabState DB entity, DAO, and migration 3→4"
```

---

## Task 3: Provider interfaces

**Files:**
- Create: `duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/nativeinput/NativeInputStateProvider.kt`

No failing test for pure interfaces — they'll be tested via the implementation in Task 4.

- [ ] **Step 1: Create the provider interfaces file**

Create `duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/nativeinput/NativeInputStateProvider.kt`:

```kotlin
/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.duckchat.impl.nativeinput

import com.duckduckgo.duckchat.impl.ui.NativeInputState
import kotlinx.coroutines.flow.StateFlow

/**
 * Read interface for per-tab native input state.
 *
 * Both interfaces live in duckchat-impl (not duckchat-api) because [NativeInputState]
 * contains impl-level types. Expose specific derived values to duckchat-api if an
 * external consumer ever needs them — YAGNI for now.
 */
interface NativeInputStateProvider {

    /**
     * Returns a [StateFlow] for a specific tab's state.
     * Creates an empty entry backed by [NativeInputState.zero] if the tab is not yet known.
     * The flow emits immediately on subscription with the current value.
     */
    fun stateForTab(tabId: String): StateFlow<NativeInputState>

    /**
     * Mirrors the currently active tab's state. For ambient UI consumers that do not
     * track tabs themselves (e.g. NTP background logo).
     * Resets to [NativeInputState.zero] when no tab is active.
     */
    val displayedState: StateFlow<NativeInputState>
}

/**
 * Write interface for per-tab native input state.
 *
 * The widget ViewModel calls [setActiveTab] and plugin ViewModels call [update].
 * Persisted fields ([NativeInputState.selectedModelId]) are written to Room automatically
 * when they change.
 */
interface MutableNativeInputStateProvider {

    /**
     * Called by the widget ViewModel when it attaches to a tab.
     * [structural] carries the fields the widget owns: [NativeInputState.inputMode],
     * [NativeInputState.inputContext], [NativeInputState.inputPosition].
     * These are merged with any persisted state already stored for [tabId].
     * Drives [NativeInputStateProvider.displayedState] to reflect this tab.
     */
    fun setActiveTab(tabId: String, structural: NativeInputState)

    /**
     * Applies [patch] to the current state for [tabId].
     * No-op if [tabId] has already been cleared. Persists any persisted fields that changed.
     *
     * Usage: `mutableProvider.update(tabId) { copy(selectedModelId = newId) }`
     */
    fun update(tabId: String, patch: NativeInputState.() -> NativeInputState)

    /**
     * Called when a tab is closed. Removes the in-memory flow and deletes the DB row.
     * If [tabId] was the active tab, resets [NativeInputStateProvider.displayedState] to
     * [NativeInputState.zero].
     */
    fun clearTab(tabId: String)
}
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew :duckchat-impl:compileDebugKotlin 2>&1 | tail -15
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/nativeinput/NativeInputStateProvider.kt
git commit -m "Add NativeInputStateProvider and MutableNativeInputStateProvider interfaces"
```

---

## Task 4: RealNativeInputStateProvider implementation

**Files:**
- Create: `duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/nativeinput/RealNativeInputStateProvider.kt`
- Create: `duckchat/duckchat-impl/src/test/kotlin/com/duckduckgo/duckchat/impl/nativeinput/RealNativeInputStateProviderTest.kt`

- [ ] **Step 1: Write failing tests**

Create `duckchat/duckchat-impl/src/test/kotlin/com/duckduckgo/duckchat/impl/nativeinput/RealNativeInputStateProviderTest.kt`:

```kotlin
package com.duckduckgo.duckchat.impl.nativeinput

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.ui.NativeInputState
import com.duckduckgo.duckchat.store.impl.store.NativeInputTabStateDao
import com.duckduckgo.duckchat.store.impl.store.NativeInputTabStateEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class RealNativeInputStateProviderTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val dao: NativeInputTabStateDao = mock()
    private lateinit var testee: RealNativeInputStateProvider

    @Before
    fun setUp() {
        whenever(dao.getTab(any())).thenReturn(null)
        testee = RealNativeInputStateProvider(
            dao = dao,
            appScope = coroutineRule.testScope,
            ioDispatcher = coroutineRule.testDispatcherProvider.io(),
        )
    }

    @Test
    fun whenDisplayedStateInitialThenZero() = runTest {
        assertEquals(NativeInputState.zero(), testee.displayedState.value)
    }

    @Test
    fun whenStateForUnknownTabThenZero() = runTest {
        assertEquals(NativeInputState.zero(), testee.stateForTab("new-tab").value)
    }

    @Test
    fun whenSetActiveTabThenDisplayedStateUpdated() = runTest {
        val structural = NativeInputState(
            inputMode = NativeInputState.InputMode.SEARCH_AND_DUCK_AI,
            inputContext = NativeInputState.InputContext.DUCK_AI,
        )
        testee.setActiveTab("tab-1", structural)

        assertEquals(NativeInputState.InputMode.SEARCH_AND_DUCK_AI, testee.displayedState.value.inputMode)
        assertEquals(NativeInputState.InputContext.DUCK_AI, testee.displayedState.value.inputContext)
    }

    @Test
    fun whenSetActiveTabWithPersistedModelThenStateMergesModelId() = runTest {
        whenever(dao.getTab("tab-1")).thenReturn(
            NativeInputTabStateEntity(tabId = "tab-1", selectedModelId = "claude-3"),
        )
        val structural = NativeInputState(
            inputMode = NativeInputState.InputMode.SEARCH_AND_DUCK_AI,
            inputContext = NativeInputState.InputContext.BROWSER,
        )
        testee.setActiveTab("tab-1", structural)

        assertEquals("claude-3", testee.displayedState.value.selectedModelId)
    }

    @Test
    fun whenSetActiveTabWithNoPersistedDataThenSelectedModelIdIsNull() = runTest {
        val structural = NativeInputState(
            inputMode = NativeInputState.InputMode.SEARCH_ONLY,
            inputContext = NativeInputState.InputContext.BROWSER,
        )
        testee.setActiveTab("tab-1", structural)

        assertNull(testee.displayedState.value.selectedModelId)
    }

    @Test
    fun whenUpdateOnActiveTabThenDisplayedStateReflectsChange() = runTest {
        val structural = NativeInputState(
            inputMode = NativeInputState.InputMode.SEARCH_AND_DUCK_AI,
            inputContext = NativeInputState.InputContext.BROWSER,
        )
        testee.setActiveTab("tab-1", structural)

        testee.update("tab-1") { copy(selectedModelId = "gpt-4o") }

        assertEquals("gpt-4o", testee.displayedState.value.selectedModelId)
    }

    @Test
    fun whenUpdateOnActiveTabWithChangedModelIdThenDaoPersists() = runTest {
        val structural = NativeInputState(
            inputMode = NativeInputState.InputMode.SEARCH_ONLY,
            inputContext = NativeInputState.InputContext.BROWSER,
        )
        testee.setActiveTab("tab-1", structural)

        testee.update("tab-1") { copy(selectedModelId = "gpt-4o") }

        verify(dao).upsert(NativeInputTabStateEntity(tabId = "tab-1", selectedModelId = "gpt-4o"))
    }

    @Test
    fun whenUpdateWithUnchangedModelIdThenDaoNotCalled() = runTest {
        whenever(dao.getTab("tab-1")).thenReturn(
            NativeInputTabStateEntity(tabId = "tab-1", selectedModelId = "claude-3"),
        )
        val structural = NativeInputState(
            inputMode = NativeInputState.InputMode.SEARCH_ONLY,
            inputContext = NativeInputState.InputContext.BROWSER,
        )
        testee.setActiveTab("tab-1", structural) // selectedModelId = "claude-3" loaded from DB

        testee.update("tab-1") { copy(selectedModelId = "claude-3") } // same value

        verify(dao, never()).upsert(any())
    }

    @Test
    fun whenUpdateOnUnknownTabThenNoOp() = runTest {
        // Should not throw or crash
        testee.update("never-set-tab") { copy(selectedModelId = "model") }

        // displayedState remains zero
        assertEquals(NativeInputState.zero(), testee.displayedState.value)
    }

    @Test
    fun whenClearActiveTabThenDisplayedStateResetsToZero() = runTest {
        val structural = NativeInputState(
            inputMode = NativeInputState.InputMode.SEARCH_AND_DUCK_AI,
            inputContext = NativeInputState.InputContext.DUCK_AI,
        )
        testee.setActiveTab("tab-1", structural)
        testee.clearTab("tab-1")

        assertEquals(NativeInputState.zero(), testee.displayedState.value)
    }

    @Test
    fun whenClearActiveTabThenDaoDeletes() = runTest {
        val structural = NativeInputState(
            inputMode = NativeInputState.InputMode.SEARCH_ONLY,
            inputContext = NativeInputState.InputContext.BROWSER,
        )
        testee.setActiveTab("tab-1", structural)
        testee.clearTab("tab-1")

        verify(dao).delete("tab-1")
    }

    @Test
    fun whenClearNonActiveTabThenDisplayedStateUnchanged() = runTest {
        val structural = NativeInputState(
            inputMode = NativeInputState.InputMode.SEARCH_AND_DUCK_AI,
            inputContext = NativeInputState.InputContext.DUCK_AI,
        )
        testee.setActiveTab("tab-1", structural)
        testee.setActiveTab("tab-2", structural.copy(inputContext = NativeInputState.InputContext.BROWSER))

        // tab-1 is not the active tab (tab-2 was set last)
        testee.clearTab("tab-1")

        // displayedState should still reflect tab-2
        assertEquals(NativeInputState.InputContext.BROWSER, testee.displayedState.value.inputContext)
    }

    @Test
    fun whenStateForTabAfterUpdateThenReturnsUpdatedState() = runTest {
        val structural = NativeInputState(
            inputMode = NativeInputState.InputMode.SEARCH_ONLY,
            inputContext = NativeInputState.InputContext.BROWSER,
        )
        testee.setActiveTab("tab-1", structural)
        testee.update("tab-1") { copy(selectedModelId = "llama-3") }

        assertEquals("llama-3", testee.stateForTab("tab-1").first().selectedModelId)
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./gradlew :duckchat-impl:testDebugUnitTest --tests "com.duckduckgo.duckchat.impl.nativeinput.RealNativeInputStateProviderTest" 2>&1 | tail -20
```

Expected: FAIL — `RealNativeInputStateProvider` not found.

- [ ] **Step 3: Implement RealNativeInputStateProvider**

Create `duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/nativeinput/RealNativeInputStateProvider.kt`:

```kotlin
/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.duckchat.impl.nativeinput

import com.duckduckgo.anvil.annotations.ContributesBinding
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.ui.NativeInputState
import com.duckduckgo.duckchat.store.impl.store.NativeInputTabStateDao
import com.duckduckgo.duckchat.store.impl.store.NativeInputTabStateEntity
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.duckduckgo.anvil.annotations.SingleInstanceIn

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = NativeInputStateProvider::class)
@ContributesBinding(AppScope::class, boundType = MutableNativeInputStateProvider::class)
class RealNativeInputStateProvider @Inject constructor(
    private val dao: NativeInputTabStateDao,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : NativeInputStateProvider, MutableNativeInputStateProvider {

    private val tabFlows = ConcurrentHashMap<String, MutableStateFlow<NativeInputState>>()
    private val _displayedState = MutableStateFlow(NativeInputState.zero())
    override val displayedState: StateFlow<NativeInputState> = _displayedState.asStateFlow()
    private var activeTabId: String? = null

    override fun stateForTab(tabId: String): StateFlow<NativeInputState> =
        tabFlows.getOrPut(tabId) { MutableStateFlow(NativeInputState.zero()) }.asStateFlow()

    override fun setActiveTab(tabId: String, structural: NativeInputState) {
        activeTabId = tabId
        appScope.launch(dispatchers.io()) {
            val persisted = dao.getTab(tabId)
            val merged = structural.copy(selectedModelId = persisted?.selectedModelId)
            tabFlows.getOrPut(tabId) { MutableStateFlow(NativeInputState.zero()) }.value = merged
            _displayedState.value = merged
        }
    }

    override fun update(tabId: String, patch: NativeInputState.() -> NativeInputState) {
        val flow = tabFlows[tabId] ?: return // tab cleared or never set — no-op
        val old = flow.value
        val new = old.patch()
        flow.value = new
        if (tabId == activeTabId) _displayedState.value = new
        if (old.selectedModelId != new.selectedModelId) {
            appScope.launch(dispatchers.io()) {
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
        appScope.launch(dispatchers.io()) {
            dao.delete(tabId)
        }
    }
}
```

**Note on test constructor:** The test constructs `RealNativeInputStateProvider` directly without Dagger. To make this work, add a secondary constructor or use the primary constructor with test doubles. The test uses `coroutineRule.testScope` as `appScope` and `coroutineRule.testDispatcherProvider.io()` as `ioDispatcher`. Update the class to accept `CoroutineDispatcher` directly (or use `DispatcherProvider`):

The constructor above uses `DispatcherProvider` — update the test to match:

```kotlin
// In the test setUp():
testee = RealNativeInputStateProvider(
    dao = dao,
    appScope = coroutineRule.testScope,
    dispatchers = coroutineRule.testDispatcherProvider,
)
```

And update the test class import/field:
```kotlin
// Replace the setUp line in the test:
testee = RealNativeInputStateProvider(
    dao = dao,
    appScope = coroutineRule.testScope,
    dispatchers = coroutineRule.testDispatcherProvider,
)
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./gradlew :duckchat-impl:testDebugUnitTest --tests "com.duckduckgo.duckchat.impl.nativeinput.RealNativeInputStateProviderTest" 2>&1 | tail -15
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/nativeinput/RealNativeInputStateProvider.kt \
        duckchat/duckchat-impl/src/test/kotlin/com/duckduckgo/duckchat/impl/nativeinput/RealNativeInputStateProviderTest.kt
git commit -m "Add RealNativeInputStateProvider with per-tab state and Room persistence"
```

---

## Task 5: NativeInputHost.getTabId() + widget implementation

**Files:**
- Modify: `duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/nativeinput/NativeInputPlugin.kt`
- Modify: `duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/ui/nativeinput/views/NativeInputModeWidget.kt`

- [ ] **Step 1: Add `getTabId()` to NativeInputHost in NativeInputPlugin.kt**

In `NativeInputPlugin.kt`, update `NativeInputHost`:

```kotlin
interface NativeInputHost {
    /** Submit the current input as a chat message; opens a new chat session if the input is empty. */
    fun submit()

    /** Current input state of the host widget (mode, context, position). */
    fun getInputState(): NativeInputState

    /** The tab ID this widget instance is currently attached to. */
    fun getTabId(): String
}
```

- [ ] **Step 2: Add tabId field to NativeInputWidget interface**

In `NativeInputModeWidget.kt`, update the `NativeInputWidget` interface — change `configure` signature:

Find:
```kotlin
fun configure(isDuckAiMode: Boolean, isBottom: Boolean)
fun configureContextual()
```

Replace with:
```kotlin
fun configure(tabId: String, isDuckAiMode: Boolean, isBottom: Boolean)
fun configureContextual(tabId: String)
```

- [ ] **Step 3: Add tabId storage and getTabId() to NativeInputModeWidget**

In `NativeInputModeWidget`, add a private field after the other private fields:

```kotlin
private var tabId: String = ""
```

Update `configure()`:
```kotlin
override fun configure(tabId: String, isDuckAiMode: Boolean, isBottom: Boolean) {
    this.tabId = tabId
    doOnAttach {
        viewModel.configure(tabId, isDuckAiMode, isBottom)
        viewModel.state.replayCache.lastOrNull()?.let { nativeInputState = it }
        if (isDuckAiMode) selectChatTab()
        applyOmnibarShape(isBottom)
    }
}
```

Update `configureContextual()`:
```kotlin
override fun configureContextual(tabId: String) {
    this.tabId = tabId
    doOnAttach {
        viewModel.configureContextual(tabId)
        selectChatTab()
    }
}
```

Add `getTabId()` implementation after `getInputState()`:
```kotlin
override fun getTabId(): String = tabId
```

- [ ] **Step 4: Compile check**

```bash
./gradlew :duckchat-impl:compileDebugKotlin 2>&1 | grep -E "error:|warning:" | head -30
```

Fix any compilation errors from call sites that now need a `tabId` argument. Run:

```bash
grep -rn "\.configure(" /Users/malmstein/dev/repos/android/duckduckgo/.claude/worktrees/native-input-provider --include="*.kt" | grep -v "test\|NativeInputModeWidget\|NativeInputWidget" | grep -v "configureContextual\|widgetConfig\|configure(isDuckAi"
```

For each call site that calls `widget.configure(isDuckAiMode, isBottom)`, update it to `widget.configure(tabId, isDuckAiMode, isBottom)`. The tabId is typically available from the browser's tab management.

For `configureContextual()` call sites, update similarly.

- [ ] **Step 5: Commit**

```bash
git add duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/nativeinput/NativeInputPlugin.kt \
        duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/ui/nativeinput/views/NativeInputModeWidget.kt
git commit -m "Add getTabId() to NativeInputHost; thread tabId through widget configure()"
```

---

## Task 6: Widget ViewModel — inject provider, call setActiveTab, update send path

**Files:**
- Modify: `duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/ui/NativeInputModeWidgetViewModel.kt`
- Modify: `duckchat/duckchat-impl/src/test/kotlin/com/duckduckgo/duckchat/impl/ui/NativeInputModeWidgetViewModelTest.kt`

- [ ] **Step 1: Write failing tests for new provider behaviour**

Add these tests to `NativeInputModeWidgetViewModelTest.kt` (add to the existing test class, after the `setUp()` method):

First, add the mock fields at the top of the test class:
```kotlin
private val nativeInputStateProvider: NativeInputStateProvider = mock()
private val mutableNativeInputStateProvider: MutableNativeInputStateProvider = mock()
```

Update `setUp()` to pass providers when constructing the ViewModel (you'll need to update the constructor call once the ViewModel is modified):
```kotlin
// After ViewModel constructor changes, update setUp() to include:
// nativeInputStateProvider = nativeInputStateProvider,
// mutableNativeInputStateProvider = mutableNativeInputStateProvider,
```

Add the tests:
```kotlin
@Test
fun whenConfigureCalledThenSetActiveTabCalledOnProvider() = runTest {
    testee.configure("tab-abc", isDuckAiMode = false, isBottom = false)

    verify(mutableNativeInputStateProvider).setActiveTab(
        eq("tab-abc"),
        any(),
    )
}

@Test
fun whenConfigureWithDuckAiModeThenSetActiveTabWithDuckAiContext() = runTest {
    testee.configure("tab-abc", isDuckAiMode = true, isBottom = false)

    verify(mutableNativeInputStateProvider).setActiveTab(
        eq("tab-abc"),
        argThat { inputContext == NativeInputState.InputContext.DUCK_AI },
    )
}

@Test
fun whenGetSelectedModelIdThenReadsFromProvider() = runTest {
    val stateFlow = MutableStateFlow(NativeInputState.zero().copy(selectedModelId = "claude-3"))
    whenever(nativeInputStateProvider.stateForTab("tab-abc")).thenReturn(stateFlow)
    testee.configure("tab-abc", isDuckAiMode = false, isBottom = false)

    assertEquals("claude-3", testee.getSelectedModelId())
}

@Test
fun whenGetSelectedModelIdAndNoTabConfiguredThenReturnsNull() = runTest {
    assertNull(testee.getSelectedModelId())
}
```

- [ ] **Step 2: Run failing tests**

```bash
./gradlew :duckchat-impl:testDebugUnitTest --tests "com.duckduckgo.duckchat.impl.ui.NativeInputModeWidgetViewModelTest.whenConfigureCalledThenSetActiveTabCalledOnProvider" 2>&1 | tail -15
```

Expected: FAIL — ViewModel doesn't inject providers yet.

- [ ] **Step 3: Update NativeInputModeWidgetViewModel**

Add the two provider parameters to the constructor (after `dispatchers`):

```kotlin
@ContributesViewModel(ViewScope::class)
class NativeInputModeWidgetViewModel @Inject constructor(
    private val duckChatInternal: DuckChatInternal,
    duckAiFeatureState: DuckAiFeatureState,
    subscriptions: Subscriptions,
    private val pendingNativePromptStore: PendingNativePromptStore,
    private val chatSuggestionsReader: ChatSuggestionsReader,
    private val nativeInputPlugins: ActivePluginPoint<NativeInputPlugin>,
    autoCompleteFactory: AutoCompleteFactory,
    private val autoCompleteSettings: AutoCompleteSettings,
    private val duckAiChatHistoryFeature: DuckAiChatHistoryFeature,
    private val dispatchers: DispatcherProvider,
    private val inputScreenConfigResolver: InputScreenConfigResolver,
    private val pixel: Pixel,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val nativeInputStateProvider: NativeInputStateProvider,
    private val mutableNativeInputStateProvider: MutableNativeInputStateProvider,
) : ViewModel() {
```

Add a tabId field:
```kotlin
private var tabId: String = ""
```

Update `configure()` to accept tabId and call `setActiveTab`:
```kotlin
fun configure(tabId: String, isDuckAiMode: Boolean, isBottom: Boolean) {
    this.tabId = tabId
    val context = if (isDuckAiMode) NativeInputState.InputContext.DUCK_AI else NativeInputState.InputContext.BROWSER
    val position = if (isBottom) NativeInputState.InputPosition.BOTTOM else NativeInputState.InputPosition.TOP
    widgetConfig.value = WidgetConfig(inputContext = context, inputPosition = position)
    val structural = NativeInputState(
        inputMode = getInputMode(
            isEnabled = true, // will be refined by state flow, but we need something for setActiveTab
            isInputScreenUserSettingEnabled = true,
        ),
        inputContext = context,
        inputPosition = position,
    )
    mutableNativeInputStateProvider.setActiveTab(tabId, structural)
}
```

Update `configureContextual()`:
```kotlin
fun configureContextual(tabId: String) {
    this.tabId = tabId
    widgetConfig.update { it.copy(inputContext = NativeInputState.InputContext.DUCK_AI_CONTEXTUAL) }
    val structural = NativeInputState(
        inputMode = NativeInputState.InputMode.SEARCH_ONLY,
        inputContext = NativeInputState.InputContext.DUCK_AI_CONTEXTUAL,
    )
    mutableNativeInputStateProvider.setActiveTab(tabId, structural)
}
```

Update `getSelectedModelId()` to read from the provider:
```kotlin
fun getSelectedModelId(): String? {
    if (tabId.isEmpty()) return null
    return nativeInputStateProvider.stateForTab(tabId).value.selectedModelId
        ?: _plugins.value.firstNotNullOfOrNull { plugin ->
            // Fallback to legacy getPromptContribution() during migration
            @Suppress("DEPRECATION")
            (plugin.getPromptContribution() as? PromptContribution.ModelSelection)?.modelId
        }
}
```

- [ ] **Step 4: Run failing tests to confirm they pass**

```bash
./gradlew :duckchat-impl:testDebugUnitTest --tests "com.duckduckgo.duckchat.impl.ui.NativeInputModeWidgetViewModelTest" 2>&1 | tail -15
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Run full module tests**

```bash
./gradlew :duckchat-impl:testDebugUnitTest 2>&1 | tail -15
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/ui/NativeInputModeWidgetViewModel.kt \
        duckchat/duckchat-impl/src/test/kotlin/com/duckduckgo/duckchat/impl/ui/NativeInputModeWidgetViewModelTest.kt
git commit -m "Wire widget ViewModel to NativeInputStateProvider; read selectedModelId from provider"
```

---

## Task 7: ModelPickerViewModel — tabId + provider push

**Files:**
- Modify: `duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/ui/nativeinput/views/ModelPickerViewModel.kt`
- Modify: `duckchat/duckchat-impl/src/test/kotlin/com/duckduckgo/duckchat/impl/ui/ModelPickerViewModelTest.kt`

- [ ] **Step 1: Write failing tests**

Add to `ModelPickerViewModelTest.kt`. First add mock fields:

```kotlin
private val nativeInputStateProvider: NativeInputStateProvider = mock()
private val mutableNativeInputStateProvider: MutableNativeInputStateProvider = mock()
```

Update `setUp()` to pass them once the ViewModel constructor changes.

Add tests:

```kotlin
@Test
fun whenInitWithTabIdThenStateForTabObserved() = runTest {
    val tabStateFlow = MutableStateFlow(NativeInputState.zero().copy(selectedModelId = "claude-3"))
    whenever(nativeInputStateProvider.stateForTab("tab-1")).thenReturn(tabStateFlow)

    testee.init("tab-1")

    assertEquals("claude-3", testee.selectedModel.value)
}

@Test
fun whenSelectModelThenPushesSelectedModelIdToProvider() = runTest {
    testee.init("tab-1")
    val model = freeModel("gpt-4o", "GPT-4o")

    testee.selectModel(model)

    verify(mutableNativeInputStateProvider).update(eq("tab-1"), any())
}

@Test
fun whenSelectModelThenAlsoDelegatesToModelManager() = runTest {
    testee.init("tab-1")
    val model = freeModel("gpt-4o", "GPT-4o")

    testee.selectModel(model)

    verify(modelManager).selectModel(model)
}

@Test
fun whenInitWithNoPersistedModelThenSelectedModelFallsBackToGlobalDefault() = runTest {
    whenever(nativeInputStateProvider.stateForTab("tab-1")).thenReturn(
        MutableStateFlow(NativeInputState.zero()), // null selectedModelId
    )
    whenever(modelManager.getSelectedModelId()).thenReturn("global-default-model")

    testee.init("tab-1")

    verify(mutableNativeInputStateProvider).update(eq("tab-1"), any())
}

@Test
fun whenInitWithAlreadyPersistedModelThenDoesNotSeedFromGlobal() = runTest {
    whenever(nativeInputStateProvider.stateForTab("tab-1")).thenReturn(
        MutableStateFlow(NativeInputState.zero().copy(selectedModelId = "existing-model")),
    )
    testee.init("tab-1")

    verify(mutableNativeInputStateProvider, never()).update(any(), any())
}
```

- [ ] **Step 2: Run to confirm tests fail**

```bash
./gradlew :duckchat-impl:testDebugUnitTest --tests "com.duckduckgo.duckchat.impl.ui.ModelPickerViewModelTest.whenSelectModelThenPushesSelectedModelIdToProvider" 2>&1 | tail -15
```

Expected: FAIL.

- [ ] **Step 3: Update ModelPickerViewModel**

Replace the full file content:

```kotlin
/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.duckchat.impl.ui.nativeinput.views

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.models.AIChatModel
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.ModelProvider
import com.duckduckgo.duckchat.impl.models.ModelState
import com.duckduckgo.duckchat.impl.models.UserTier
import com.duckduckgo.duckchat.impl.nativeinput.MutableNativeInputStateProvider
import com.duckduckgo.duckchat.impl.nativeinput.NativeInputStateProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelSection(@StringRes val headerRes: Int?, val models: List<AIChatModel>)

@ContributesViewModel(ViewScope::class)
class ModelPickerViewModel @Inject constructor(
    private val modelManager: DuckAiModelManager,
    private val nativeInputStateProvider: NativeInputStateProvider,
    private val mutableNativeInputStateProvider: MutableNativeInputStateProvider,
) : ViewModel() {

    val state: StateFlow<ModelState> = modelManager.modelState

    var menuShowing = false

    private val _tabId = MutableStateFlow<String?>(null)

    /**
     * The currently selected model ID for this tab.
     * Driven by the per-tab state in [NativeInputStateProvider].
     */
    val selectedModel: StateFlow<String?> = _tabId
        .filterNotNull()
        .flatMapLatest { tabId -> nativeInputStateProvider.stateForTab(tabId).map { it.selectedModelId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Must be called once, immediately after the ViewModel is created, before any other interaction.
     * Seeds the provider from the global default if no per-tab selection exists yet.
     */
    fun init(tabId: String) {
        _tabId.value = tabId
        val existingModelId = nativeInputStateProvider.stateForTab(tabId).value.selectedModelId
        if (existingModelId == null) {
            val globalId = modelManager.getSelectedModelId()
            if (globalId != null) {
                mutableNativeInputStateProvider.update(tabId) { copy(selectedModelId = globalId) }
            }
        }
    }

    /** Legacy read path, kept for consumers that need the raw ID synchronously. */
    fun getSelectedModelId(): String? = _tabId.value?.let {
        nativeInputStateProvider.stateForTab(it).value.selectedModelId
    } ?: modelManager.getSelectedModelId()

    fun fetchModels() {
        viewModelScope.launch {
            modelManager.fetchModels()
        }
    }

    fun selectModel(model: AIChatModel) {
        viewModelScope.launch {
            modelManager.selectModel(model)
            _tabId.value?.let { tabId ->
                mutableNativeInputStateProvider.update(tabId) { copy(selectedModelId = model.id) }
            }
        }
    }

    fun buildSections(state: ModelState): List<ModelSection> =
        if (state.userTier != UserTier.FREE) getSubscriberModels(state.models) else getFreeModels(state.models)

    private fun getSubscriberModels(models: List<AIChatModel>): List<ModelSection> {
        val advanced = models.filter { !it.accessTier.contains(FREE_TIER) }
        val basic = models.filter { it.accessTier.contains(FREE_TIER) }
        return listOfNotNull(
            advanced.toSectionOrNull(R.string.duckAiModelPickerAdvancedModels),
            basic.toSectionOrNull(R.string.duckAiModelPickerBasicModels),
        )
    }

    private fun getFreeModels(models: List<AIChatModel>): List<ModelSection> {
        val accessible = models.filter { it.isAccessible }
        val premium = models.filter { !it.isAccessible }
        return listOfNotNull(
            accessible.toSectionOrNull(headerRes = null),
            premium.toSectionOrNull(headerRes = null),
        )
    }

    private fun List<AIChatModel>.toSectionOrNull(@StringRes headerRes: Int?): ModelSection? =
        takeIf { it.isNotEmpty() }?.let { ModelSection(headerRes, it) }

    @DrawableRes
    fun getIconResForModel(model: AIChatModel): Int? = when (model.provider) {
        ModelProvider.OPENAI -> R.drawable.ic_ai_model_openai_16
        ModelProvider.ANTHROPIC -> R.drawable.ic_ai_model_claude_16
        ModelProvider.MISTRAL -> R.drawable.ic_ai_model_mistral_16
        ModelProvider.META -> R.drawable.ic_ai_model_llama_16
        ModelProvider.OSS -> R.drawable.ic_ai_model_oss_16
        ModelProvider.UNKNOWN -> null
    }

    companion object {
        private const val FREE_TIER = "free"
    }
}
```

- [ ] **Step 4: Update ModelPickerViewModelTest setUp() to pass providers**

In `ModelPickerViewModelTest.kt`, update the `setUp()` and `testee` construction:

```kotlin
@Before
fun setUp() {
    whenever(modelManager.modelState).thenReturn(stateFlow)
    whenever(nativeInputStateProvider.stateForTab(any())).thenReturn(
        MutableStateFlow(NativeInputState.zero()),
    )
    testee = ModelPickerViewModel(modelManager, nativeInputStateProvider, mutableNativeInputStateProvider)
}
```

Also add these imports at the top of the test:
```kotlin
import com.duckduckgo.duckchat.impl.nativeinput.MutableNativeInputStateProvider
import com.duckduckgo.duckchat.impl.nativeinput.NativeInputStateProvider
import com.duckduckgo.duckchat.impl.ui.NativeInputState
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
```

Existing tests (e.g. `whenGetSelectedModelIdThenDelegatesToModelManager`) should still pass since `getSelectedModelId()` still delegates to `modelManager` when no tabId is set.

- [ ] **Step 5: Run all ModelPickerViewModel tests**

```bash
./gradlew :duckchat-impl:testDebugUnitTest --tests "com.duckduckgo.duckchat.impl.ui.ModelPickerViewModelTest" 2>&1 | tail -15
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/ui/nativeinput/views/ModelPickerViewModel.kt \
        duckchat/duckchat-impl/src/test/kotlin/com/duckduckgo/duckchat/impl/ui/ModelPickerViewModelTest.kt
git commit -m "ModelPickerViewModel pushes selectedModelId to NativeInputStateProvider on selection"
```

---

## Task 8: ModelPickerNativeInputPlugin — wire tabId from host

**Files:**
- Modify: `duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/ui/nativeinput/plugins/ModelPickerNativeInputPlugin.kt`

- [ ] **Step 1: Update ModelPickerNativeInputPlugin**

Replace the full file content:

```kotlin
/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.duckchat.impl.ui.nativeinput.plugins

import android.content.Context
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.nativeinput.NativeInputHost
import com.duckduckgo.duckchat.impl.nativeinput.NativeInputPlugin
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.ModelPickerView
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.ModelPickerViewModel
import javax.inject.Inject

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = NativeInputPlugin::class,
    featureName = "pluginModelPickerNativeInput",
    parentFeatureName = "pluginPointNativeInput",
)
class ModelPickerNativeInputPlugin @Inject constructor(
    private val viewModelFactory: ViewViewModelFactory,
) : NativeInputPlugin {

    override val containerId: Int = R.id.modelPickerContainer

    override fun createView(context: Context, host: NativeInputHost): View {
        val tabId = host.getTabId()
        val store = ViewModelStore()
        val vm = ViewModelProvider(store, viewModelFactory)[ModelPickerViewModel::class.java]
        vm.init(tabId)
        return ModelPickerView(context, vm)
    }

    @Deprecated("Contributions are now pushed directly to NativeInputStateProvider. Will be removed once all plugins migrate.")
    override fun getPromptContribution() = null
}
```

- [ ] **Step 2: Compile check**

```bash
./gradlew :duckchat-impl:compileDebugKotlin 2>&1 | grep -E "error:" | head -20
```

Expected: No errors.

- [ ] **Step 3: Run full module tests**

```bash
./gradlew :duckchat-impl:testDebugUnitTest 2>&1 | tail -15
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/ui/nativeinput/plugins/ModelPickerNativeInputPlugin.kt
git commit -m "ModelPickerNativeInputPlugin wires tabId from host to ViewModel"
```

---

## Task 9: Deprecate getPromptContribution() with default impl

**Files:**
- Modify: `duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/nativeinput/NativeInputPlugin.kt`

- [ ] **Step 1: Add default null impl to getPromptContribution()**

In `NativeInputPlugin.kt`, update the interface:

```kotlin
interface NativeInputPlugin : ActivePlugin {

    /** ID of the `FrameLayout` slot in the widget layout this plugin renders into. */
    val containerId: Int

    /** Build the plugin view. Called once at widget setup. The [host] lets the plugin trigger a submit or read widget state. */
    fun createView(context: Context, host: NativeInputHost): View

    /**
     * State to append to the prompt when the user submits.
     *
     * @deprecated Push contributions to [MutableNativeInputStateProvider] directly instead.
     * Default implementation returns null. Will be removed once all plugins migrate.
     */
    @Deprecated("Push contributions to MutableNativeInputStateProvider instead")
    fun getPromptContribution(): PromptContribution? = null
}
```

- [ ] **Step 2: Add import for MutableNativeInputStateProvider to NativeInputPlugin.kt** (for the Deprecated doc reference — it's just a comment so no real import needed, but add if you want the IDE to resolve it).

- [ ] **Step 3: Compile check**

```bash
./gradlew :duckchat-impl:compileDebugKotlin 2>&1 | grep -E "error:" | head -20
```

Expected: No errors.

- [ ] **Step 4: Run all tests**

```bash
./gradlew :duckchat-impl:testDebugUnitTest 2>&1 | tail -15
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add duckchat/duckchat-impl/src/main/java/com/duckduckgo/duckchat/impl/nativeinput/NativeInputPlugin.kt
git commit -m "Deprecate getPromptContribution() with default null impl; migration path to provider"
```

---

## Task 10: Final verification

- [ ] **Step 1: Run all duckchat module tests**

```bash
./gradlew :duckchat-impl:testDebugUnitTest :duckchat-store:testDebugUnitTest 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL, no test failures.

- [ ] **Step 2: Run lint**

```bash
./gradlew :duckchat-impl:lint 2>&1 | grep -E "error|Error" | grep -v "^$" | head -20
```

Expected: No new errors (existing baseline entries are OK).

- [ ] **Step 3: Run spotless check**

```bash
./gradlew :duckchat-impl:spotlessCheck :duckchat-store:spotlessCheck 2>&1 | tail -15
```

If this reports formatting issues, run:
```bash
./gradlew :duckchat-impl:spotlessApply :duckchat-store:spotlessApply
```
Then re-add modified files and amend or create a new formatting commit.

- [ ] **Step 4: Final commit if spotless made changes**

```bash
git add -u
git commit -m "Apply spotless formatting"
```
