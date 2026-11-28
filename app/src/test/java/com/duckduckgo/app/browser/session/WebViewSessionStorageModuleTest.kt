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

package com.duckduckgo.app.browser.session

import android.os.Bundle
import android.webkit.WebView
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.fire.db.FireModeDatabase
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.browser.feature.toggles.AndroidBrowserConfigFeature
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class WebViewSessionStorageModuleTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val toggle: Toggle = mock()
    private val browserConfig: AndroidBrowserConfigFeature = mock {
        on { webViewSessionPersistence() } doReturn toggle
    }
    private val webView: WebView = mock {
        on { saveState(any()) } doAnswer { invocation ->
            invocation.getArgument<Bundle>(0).putString("key", "value")
            null
        }
    }

    private lateinit var appDatabase: AppDatabase
    private lateinit var fireDatabase: FireModeDatabase
    private lateinit var regular: WebViewSessionStorage
    private lateinit var fire: WebViewSessionStorage

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        appDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        fireDatabase = Room.inMemoryDatabaseBuilder(context, FireModeDatabase::class.java).allowMainThreadQueries().build()
        appDatabase.tabsDao().insertTab(TabEntity(tabId = REGULAR_TAB, position = 0))
        fireDatabase.tabsDao().insertTab(TabEntity(tabId = FIRE_TAB, position = 0))

        regular = WebViewSessionStorageModule.provideRegularWebViewSessionStorage(
            appDatabase.webViewSessionDao(),
            coroutineRule.testScope,
            coroutineRule.testDispatcherProvider,
            mock(),
            browserConfig,
        )
        fire = WebViewSessionStorageModule.provideFireWebViewSessionStorage(
            fireDatabase.webViewSessionDao(),
            coroutineRule.testScope,
            coroutineRule.testDispatcherProvider,
            mock(),
            browserConfig,
        )
    }

    @After
    fun tearDown() {
        appDatabase.close()
        fireDatabase.close()
    }

    @Test
    fun whenPersistenceOnThenEachModeSavesOnlyToItsOwnDatabase() = runTest {
        whenever(toggle.isEnabled()).thenReturn(true)

        fire.saveSession(webView, FIRE_TAB)
        regular.saveSession(webView, REGULAR_TAB)
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        assertNotNull(fireDatabase.webViewSessionDao().get(FIRE_TAB))
        assertNull(appDatabase.webViewSessionDao().get(FIRE_TAB))
        assertNotNull(appDatabase.webViewSessionDao().get(REGULAR_TAB))
        assertNull(fireDatabase.webViewSessionDao().get(REGULAR_TAB))
    }

    @Test
    fun whenPersistenceOffThenFireDeleteAllLeavesRegularInMemorySession() = runTest {
        whenever(toggle.isEnabled()).thenReturn(false)
        regular.saveSession(webView, REGULAR_TAB)
        fire.saveSession(webView, FIRE_TAB)

        fire.deleteAllSessions()

        assertTrue(regular.restoreSession(webView, REGULAR_TAB))
        assertFalse(fire.restoreSession(webView, FIRE_TAB))
    }

    @Test
    fun whenActivityModeResolvedThenMatchingStorageReturned() {
        assertSame(regular, WebViewSessionStorageActivityModule.provideCurrentModeWebViewSessionStorage(regular, fire, BrowserMode.REGULAR))
        assertSame(fire, WebViewSessionStorageActivityModule.provideCurrentModeWebViewSessionStorage(regular, fire, BrowserMode.FIRE))
    }

    private companion object {
        const val REGULAR_TAB = "regular-tab"
        const val FIRE_TAB = "fire-tab"
    }
}
