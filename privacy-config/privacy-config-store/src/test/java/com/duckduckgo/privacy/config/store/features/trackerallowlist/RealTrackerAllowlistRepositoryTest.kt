/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.privacy.config.store.features.trackerallowlist

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.privacy.config.store.AllowlistRuleEntity
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import com.duckduckgo.privacy.config.store.TrackerAllowlistEntity
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealTrackerAllowlistRepositoryTest {

    @get:Rule var coroutineRule = CoroutineTestRule()

    lateinit var testee: RealTrackerAllowlistRepository

    private val mockDatabase: PrivacyConfigDatabase = mock()
    private val mockTrackerAllowlistDao: TrackerAllowlistDao = mock()

    @Before
    fun before() {
        whenever(mockDatabase.trackerAllowlistDao()).thenReturn(mockTrackerAllowlistDao)
        testee =
            RealTrackerAllowlistRepository(
                mockDatabase,
                TestScope(),
                coroutineRule.testDispatcherProvider,
                isMainProcess = true,
            )
    }

    @Test
    fun whenRepositoryIsCreatedThenExceptionsLoadedIntoMemory() {
        givenHttpsDaoContainsExceptions()

        testee =
            RealTrackerAllowlistRepository(
                mockDatabase,
                TestScope(),
                coroutineRule.testDispatcherProvider,
                isMainProcess = true,
            )

        assertEquals(trackerAllowlistEntity, testee.exceptions.first())
    }

    @Test
    fun whenUpdateAllThenUpdateAllCalled() =
        runTest {
            testee =
                RealTrackerAllowlistRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                    isMainProcess = true,
                )

            testee.updateAll(listOf())

            verify(mockTrackerAllowlistDao).updateAll(anyList())
        }

    @Test
    fun whenUpdateAllThenPreviousExceptionsAreCleared() =
        runTest {
            givenHttpsDaoContainsExceptions()
            testee =
                RealTrackerAllowlistRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                    isMainProcess = true,
                )
            assertEquals(1, testee.exceptions.size)
            reset(mockTrackerAllowlistDao)

            testee.updateAll(listOf())

            assertEquals(0, testee.exceptions.size)
        }

    @Test
    fun whenUpdateAllThenRulesByDomainReflectsNewData() =
        runTest {
            whenever(mockTrackerAllowlistDao.getAll()).thenReturn(listOf(trackerAllowlistEntity))
            testee.updateAll(listOf(trackerAllowlistEntity))

            assertEquals(setOf("domain"), testee.rulesByDomain.keys)
            assertEquals(1, testee.rulesByDomain["domain"]?.size)
            assertNotNull(testee.rulesByDomain["domain"]?.first()?.regex)

            val replacement = TrackerAllowlistEntity(
                domain = "other.com",
                rules = listOf(AllowlistRuleEntity(rule = "other.com/x", domains = listOf("<all>"), reason = "")),
            )
            whenever(mockTrackerAllowlistDao.getAll()).thenReturn(listOf(replacement))
            testee.updateAll(listOf(replacement))

            assertEquals(setOf("other.com"), testee.rulesByDomain.keys)
        }

    @Test
    fun whenRuleRegexInvalidThenCompiledRuleHasNullRegex() {
        val bad = TrackerAllowlistEntity(
            domain = "bad.com",
            rules = listOf(AllowlistRuleEntity(rule = "bad.com/[unterminated", domains = listOf("<all>"), reason = "")),
        )

        val result = buildRulesByDomain(listOf(bad))

        assertNull(result["bad.com"]?.first()?.regex)
    }

    @Test
    fun whenEntitiesShareNormalizedKeyThenRulesAreMerged() {
        val a = TrackerAllowlistEntity(
            domain = "tracker.com",
            rules = listOf(AllowlistRuleEntity(rule = "tracker.com/a", domains = listOf("<all>"), reason = "")),
        )
        val b = TrackerAllowlistEntity(
            domain = "www.tracker.com",
            rules = listOf(AllowlistRuleEntity(rule = "tracker.com/b", domains = listOf("<all>"), reason = "")),
        )

        val result = buildRulesByDomain(listOf(a, b))

        assertEquals(setOf("tracker.com"), result.keys)
        assertEquals(2, result["tracker.com"]?.size)
    }

    private fun givenHttpsDaoContainsExceptions() {
        whenever(mockTrackerAllowlistDao.getAll()).thenReturn(listOf(trackerAllowlistEntity))
    }

    companion object {
        val trackerAllowlistEntity =
            TrackerAllowlistEntity(
                domain = "domain",
                rules =
                listOf(
                    AllowlistRuleEntity(
                        rule = "rule",
                        domains = listOf("domain"),
                        reason = "reason",
                    ),
                ),
            )
    }
}
