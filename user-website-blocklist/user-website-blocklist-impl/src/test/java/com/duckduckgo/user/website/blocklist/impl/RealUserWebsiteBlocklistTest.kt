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

package com.duckduckgo.user.website.blocklist.impl

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.request.interception.api.RequestBlockerPlugin
import com.duckduckgo.request.interception.api.RequestBlockerRequest
import com.duckduckgo.user.website.blocklist.impl.db.UserBlockedWebsiteEntity
import com.duckduckgo.user.website.blocklist.impl.db.UserBlockedWebsitesDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealUserWebsiteBlocklistTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val toggle: Toggle = mock<Toggle>().apply { whenever(isEnabled()).thenReturn(true) }
    private val feature: UserWebsiteBlocklistFeature = mock<UserWebsiteBlocklistFeature>().apply {
        whenever(self()).thenReturn(toggle)
    }
    private val dao = FakeDao()

    private fun createTestee(featureEnabled: Boolean = true): RealUserWebsiteBlocklist {
        whenever(toggle.isEnabled()).thenReturn(featureEnabled)
        return RealUserWebsiteBlocklist(
            dao = dao,
            feature = feature,
            dispatchers = coroutineTestRule.testDispatcherProvider,
            appCoroutineScope = coroutineTestRule.testScope,
            isMainProcess = true,
        )
    }

    @Test
    fun blockStoresETldPlusOneForSubdomainUrls() = runTest {
        val testee = createTestee()

        testee.block(Uri.parse("https://news.example.com/article"))

        assertTrue(testee.isBlocked("example.com"))
        assertTrue(testee.isBlocked(Uri.parse("https://news.example.com/other")))
        assertTrue(testee.isBlocked(Uri.parse("https://example.com")))
    }

    @Test
    fun isBlockedReturnsFalseForUnrelatedDomains() = runTest {
        val testee = createTestee()
        testee.block(Uri.parse("https://example.com"))

        assertFalse(testee.isBlocked(Uri.parse("https://other.com")))
    }

    @Test
    fun evaluateReturnsBlockOnlyForMainFrameAndBlockedDomain() = runTest {
        val testee = createTestee()
        testee.block(Uri.parse("https://example.com"))

        val blockedMain = testee.evaluate(
            RequestBlockerRequest(
                url = Uri.parse("https://example.com/page"),
                documentUrl = null,
                isForMainFrame = true,
                requestHeaders = emptyMap(),
            ),
        )
        assertTrue(blockedMain is RequestBlockerPlugin.Decision.Block)
        assertEquals("example.com", (blockedMain as RequestBlockerPlugin.Decision.Block).reason.let {
            (it as RequestBlockerPlugin.BlockReason.UserBlocked).blockedDomain
        })

        val subResource = testee.evaluate(
            RequestBlockerRequest(
                url = Uri.parse("https://example.com/api"),
                documentUrl = Uri.parse("https://other.com"),
                isForMainFrame = false,
                requestHeaders = emptyMap(),
            ),
        )
        assertEquals(RequestBlockerPlugin.Decision.Ignore, subResource)
    }

    @Test
    fun evaluateReturnsIgnoreWhenFeatureDisabled() = runTest {
        val testee = createTestee(featureEnabled = false)
        testee.block(Uri.parse("https://example.com"))

        val result = testee.evaluate(
            RequestBlockerRequest(
                url = Uri.parse("https://example.com"),
                documentUrl = null,
                isForMainFrame = true,
                requestHeaders = emptyMap(),
            ),
        )
        assertEquals(RequestBlockerPlugin.Decision.Ignore, result)
    }

    @Test
    fun unblockRemovesEntry() = runTest {
        val testee = createTestee()
        testee.block(Uri.parse("https://example.com"))
        assertTrue(testee.isBlocked("example.com"))

        testee.unblock("example.com")

        assertFalse(testee.isBlocked("example.com"))
    }

    @Test
    fun clearAllEmptiesBlocklist() = runTest {
        val testee = createTestee()
        testee.block(Uri.parse("https://example.com"))
        testee.block(Uri.parse("https://foo.com"))

        testee.clearAll()

        assertFalse(testee.isBlocked("example.com"))
        assertFalse(testee.isBlocked("foo.com"))
    }

    private class FakeDao : UserBlockedWebsitesDao {
        private val entries = linkedMapOf<String, UserBlockedWebsiteEntity>()
        private val flow = MutableStateFlow<List<UserBlockedWebsiteEntity>>(emptyList())

        override fun getAllAsFlow(): Flow<List<UserBlockedWebsiteEntity>> = flow

        override fun getAll(): List<UserBlockedWebsiteEntity> = entries.values.toList()

        override fun insert(entity: UserBlockedWebsiteEntity) {
            entries[entity.domain] = entity
            flow.value = entries.values.toList()
        }

        override fun delete(domain: String) {
            entries.remove(domain)
            flow.value = entries.values.toList()
        }

        override fun clear() {
            entries.clear()
            flow.value = emptyList()
        }
    }
}
