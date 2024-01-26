/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.trackerdetection

import android.net.Uri
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.trackerdetection.Client.ClientName.TDS
import com.duckduckgo.app.trackerdetection.model.Action.BLOCK
import com.duckduckgo.app.trackerdetection.model.Action.IGNORE
import com.duckduckgo.app.trackerdetection.model.Options
import com.duckduckgo.app.trackerdetection.model.Rule
import com.duckduckgo.app.trackerdetection.model.RuleExceptions
import com.duckduckgo.app.trackerdetection.model.TdsTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class TdsClientTest {

    private val mockUrlToTypeMapper: UrlToTypeMapper = mock()

    @Test
    fun whenUrlHasSameDomainAsTrackerEntryAndDefaultActionBlockThenMatchesIsTrue() {
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, emptyList()))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://tracker.com/script.js", DOCUMENT_URL, mapOf())
        assertTrue(result.matches)
    }

    @Test
    fun whenUrlHasSameDomainAsTrackerEntryAndDefaultActionBlockThenMatchesIsTrue2() {
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, emptyList()))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches(Uri.parse("http://tracker.com/script.js"), DOCUMENT_URL, mapOf())
        assertTrue(result.matches)
    }

    @Test
    fun whenUrlHasSameDomainAsTrackerEntryAndDefaultActionIgnoreThenMatchesIsFalse() {
        val data = listOf(TdsTracker("tracker.com", IGNORE, OWNER, CATEGORY, emptyList()))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://tracker.com/script.js", DOCUMENT_URL, mapOf())
        assertFalse(result.matches)
    }

    @Test
    fun whenUrlHasSameDomainAsTrackerEntryAndDefaultActionIgnoreThenMatchesIsFalse2() {
        val data = listOf(TdsTracker("tracker.com", IGNORE, OWNER, CATEGORY, emptyList()))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches(Uri.parse("http://tracker.com/script.js"), DOCUMENT_URL, mapOf())
        assertFalse(result.matches)
    }

    @Test
    fun whenUrlIsSubdomainOfTrackerEntryAndDefaultActionBlockThenMatchesIsTrue() {
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, emptyList()))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://subdomian.tracker.com/script.js", DOCUMENT_URL, mapOf())
        assertTrue(result.matches)
    }

    @Test
    fun whenUrlIsNotDomainOrSubDomainOfTrackerEntryThenMatchesIsFalse() {
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, emptyList()))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://nontracker.com/script.js", DOCUMENT_URL, mapOf())
        assertFalse(result.matches)
    }

    @Test
    fun whenUrlIsNotDomainOrSubDomainOfTrackerEntryThenMatchesIsFalse2() {
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, emptyList()))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches(Uri.parse("http://nontracker.com/script.js"), DOCUMENT_URL, mapOf())
        assertFalse(result.matches)
    }

    @Test
    fun whenUrlIsAParentDomainOfATrackerEntryThenMatchesIsFalse() {
        val data = listOf(TdsTracker("subdomain.tracker.com", BLOCK, OWNER, CATEGORY, emptyList()))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://tracker.com/script.js", DOCUMENT_URL, mapOf())
        assertFalse(result.matches)
    }

    @Test
    fun whenUrlIsAParentDomainOfATrackerEntryThenMatchesIsFalse2() {
        val data = listOf(TdsTracker("subdomain.tracker.com", BLOCK, OWNER, CATEGORY, emptyList()))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches(Uri.parse("http://tracker.com/script.js"), DOCUMENT_URL, mapOf())
        assertFalse(result.matches)
    }

    @Test
    fun whenUrlContainsButIsNotSubdomainOfATrackerEntryThenMatchesIsFalse() {
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, emptyList()))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://notsubdomainoftracker.com", DOCUMENT_URL, mapOf())
        assertFalse(result.matches)
    }

    @Test
    fun whenUrlContainsButIsNotSubdomainOfATrackerEntryThenMatchesIsFalse2() {
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, emptyList()))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches(Uri.parse("http://notsubdomainoftracker.com"), DOCUMENT_URL, mapOf())
        assertFalse(result.matches)
    }

    @Test
    fun whenUrlMatchesRuleWithNoExceptionsAndRuleActionBlockThenMatchesIsTrue() {
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, null, null, null)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf())
        assertTrue(result.matches)
    }

    @Test
    fun whenUrlMatchesRuleWithNoExceptionsAndRuleActionBlockThenMatchesIsTrue2() {
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, null, null, null)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches(Uri.parse("http://api.tracker.com/auth/script.js"), DOCUMENT_URL, mapOf())
        assertTrue(result.matches)
    }

    @Test
    fun whenUrlMatchesRuleWithNoExceptionsAndRuleActionIgnoreThenMatchesIsFalse() {
        val rule = Rule("api\\.tracker\\.com\\/auth", IGNORE, null, null, null)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf())
        assertFalse(result.matches)
    }

    @Test
    fun whenUrlMatchesRuleWithNoExceptionsAndRuleActionIgnoreThenMatchesIsFalse2() {
        val rule = Rule("api\\.tracker\\.com\\/auth", IGNORE, null, null, null)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches(Uri.parse("http://api.tracker.com/auth/script.js"), DOCUMENT_URL, mapOf())
        assertFalse(result.matches)
    }

    @Test
    fun whenUrlMatchesDomainWithDefaultBlockAndRuleWithNoExceptionsAndNoActionThenMatchesIsTrue() {
        val rule = Rule("api\\.tracker\\.com\\/auth", null, null, null, null)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf())
        assertTrue(result.matches)
    }

    @Test
    fun whenUrlMatchesDomainWithDefaultBlockAndRuleWithNoExceptionsAndNoActionThenMatchesIsTrue2() {
        val rule = Rule("api\\.tracker\\.com\\/auth", null, null, null, null)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches(Uri.parse("http://api.tracker.com/auth/script.js"), DOCUMENT_URL, mapOf())
        assertTrue(result.matches)
    }

    @Test
    fun whenUrlMatchesDomainWithDefaultIgnoreAndRuleWithNoExceptionsAndNoActionThenMatchesIsTrue() {
        val rule = Rule("api\\.tracker\\.com\\/auth", null, null, null, null)
        val data = listOf(TdsTracker("tracker.com", IGNORE, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf())
        assertTrue(result.matches)
    }

    @Test
    fun whenUrlMatchesDomainWithDefaultIgnoreAndRuleWithNoExceptionsAndNoActionThenMatchesIsTrue2() {
        val rule = Rule("api\\.tracker\\.com\\/auth", null, null, null, null)
        val data = listOf(TdsTracker("tracker.com", IGNORE, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches(Uri.parse("http://api.tracker.com/auth/script.js"), DOCUMENT_URL, mapOf())
        assertTrue(result.matches)
    }

    @Test
    fun whenUrlMatchesRuleWithExceptionsAndExceptionDomainMatchesDocumentThenMatchesIsFalseIrrespectiveOfAction() {
        val exceptions = RuleExceptions(listOf("example.com"), null)

        val ruleBlock = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null, null)
        val ruleIgnore = Rule("api\\.tracker\\.com\\/auth", IGNORE, exceptions, null, null)
        val ruleNone = Rule("api\\.tracker\\.com\\/auth", null, exceptions, null, null)

        val testeeBlockRuleBlock = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(ruleBlock))),
            mockUrlToTypeMapper,
        )
        val testeeIgnoreRuleBlock = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", IGNORE, OWNER, CATEGORY, listOf(ruleBlock))),
            mockUrlToTypeMapper,
        )
        val testeeBlockRuleIgnore = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(ruleIgnore))),
            mockUrlToTypeMapper,
        )
        val testeeIgnoreRuleIgnore = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", IGNORE, OWNER, CATEGORY, listOf(ruleIgnore))),
            mockUrlToTypeMapper,
        )
        val testeeBlockRuleNone = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(ruleNone))),
            mockUrlToTypeMapper,
        )
        val testeeIgnoreRuleNone = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", IGNORE, OWNER, CATEGORY, listOf(ruleNone))),
            mockUrlToTypeMapper,
        )

        assertFalse(testeeBlockRuleBlock.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf()).matches)
        assertFalse(testeeIgnoreRuleBlock.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf()).matches)
        assertFalse(testeeBlockRuleIgnore.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf()).matches)
        assertFalse(testeeIgnoreRuleIgnore.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf()).matches)
        assertFalse(testeeBlockRuleNone.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf()).matches)
        assertFalse(testeeIgnoreRuleNone.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf()).matches)
    }

    @Test
    fun whenUrlMatchesRuleWithExceptionsAndExceptionDomainMatchesDocumentThenMatchesIsFalseIrrespectiveOfAction2() {
        val exceptions = RuleExceptions(listOf("example.com"), null)

        val ruleBlock = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null, null)
        val ruleIgnore = Rule("api\\.tracker\\.com\\/auth", IGNORE, exceptions, null, null)
        val ruleNone = Rule("api\\.tracker\\.com\\/auth", null, exceptions, null, null)

        val testeeBlockRuleBlock = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(ruleBlock))),
            mockUrlToTypeMapper,
        )
        val testeeIgnoreRuleBlock = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", IGNORE, OWNER, CATEGORY, listOf(ruleBlock))),
            mockUrlToTypeMapper,
        )
        val testeeBlockRuleIgnore = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(ruleIgnore))),
            mockUrlToTypeMapper,
        )
        val testeeIgnoreRuleIgnore = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", IGNORE, OWNER, CATEGORY, listOf(ruleIgnore))),
            mockUrlToTypeMapper,
        )
        val testeeBlockRuleNone = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(ruleNone))),
            mockUrlToTypeMapper,
        )
        val testeeIgnoreRuleNone = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", IGNORE, OWNER, CATEGORY, listOf(ruleNone))),
            mockUrlToTypeMapper,
        )

        assertFalse(testeeBlockRuleBlock.matches("http://api.tracker.com/auth/script.js".toUri(), DOCUMENT_URL, mapOf()).matches)
        assertFalse(testeeIgnoreRuleBlock.matches("http://api.tracker.com/auth/script.js".toUri(), DOCUMENT_URL, mapOf()).matches)
        assertFalse(testeeBlockRuleIgnore.matches("http://api.tracker.com/auth/script.js".toUri(), DOCUMENT_URL, mapOf()).matches)
        assertFalse(testeeIgnoreRuleIgnore.matches("http://api.tracker.com/auth/script.js".toUri(), DOCUMENT_URL, mapOf()).matches)
        assertFalse(testeeBlockRuleNone.matches("http://api.tracker.com/auth/script.js".toUri(), DOCUMENT_URL, mapOf()).matches)
        assertFalse(testeeIgnoreRuleNone.matches("http://api.tracker.com/auth/script.js".toUri(), DOCUMENT_URL, mapOf()).matches)
    }

    @Test
    fun whenUrlMatchesRuleWithExceptionsAndExceptionDomainDoesNotMatchDocumentThenMatchesBehaviorIsStandard() {
        val exceptions = RuleExceptions(listOf("nonmatching.com"), null)

        val ruleBlock = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null, null)
        val ruleIgnore = Rule("api\\.tracker\\.com\\/auth", IGNORE, exceptions, null, null)
        val ruleNone = Rule("api\\.tracker\\.com\\/auth", null, exceptions, null, null)

        val testeeBlockRuleBlock = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(ruleBlock))),
            mockUrlToTypeMapper,
        )
        val testeeIgnoreRuleBlock = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", IGNORE, OWNER, CATEGORY, listOf(ruleBlock))),
            mockUrlToTypeMapper,
        )
        val testeeBlockRuleIgnore = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(ruleIgnore))),
            mockUrlToTypeMapper,
        )
        val testeeIgnoreRuleIgnore = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", IGNORE, OWNER, CATEGORY, listOf(ruleIgnore))),
            mockUrlToTypeMapper,
        )
        val testeeBlockRuleNone = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(ruleNone))),
            mockUrlToTypeMapper,
        )
        val testeeIgnoreRuleNone = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", IGNORE, OWNER, CATEGORY, listOf(ruleNone))),
            mockUrlToTypeMapper,
        )

        assertTrue(testeeBlockRuleBlock.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf()).matches)
        assertTrue(testeeIgnoreRuleBlock.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf()).matches)
        assertFalse(testeeBlockRuleIgnore.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf()).matches)
        assertFalse(testeeIgnoreRuleIgnore.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf()).matches)
        assertTrue(testeeBlockRuleNone.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf()).matches)
        assertTrue(testeeIgnoreRuleNone.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf()).matches)
    }

    @Test
    fun whenUrlMatchesRuleWithExceptionsAndExceptionDomainDoesNotMatchDocumentThenMatchesBehaviorIsStandard2() {
        val exceptions = RuleExceptions(listOf("nonmatching.com"), null)

        val ruleBlock = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null, null)
        val ruleIgnore = Rule("api\\.tracker\\.com\\/auth", IGNORE, exceptions, null, null)
        val ruleNone = Rule("api\\.tracker\\.com\\/auth", null, exceptions, null, null)

        val testeeBlockRuleBlock = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(ruleBlock))),
            mockUrlToTypeMapper,
        )
        val testeeIgnoreRuleBlock = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", IGNORE, OWNER, CATEGORY, listOf(ruleBlock))),
            mockUrlToTypeMapper,
        )
        val testeeBlockRuleIgnore = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(ruleIgnore))),
            mockUrlToTypeMapper,
        )
        val testeeIgnoreRuleIgnore = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", IGNORE, OWNER, CATEGORY, listOf(ruleIgnore))),
            mockUrlToTypeMapper,
        )
        val testeeBlockRuleNone = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(ruleNone))),
            mockUrlToTypeMapper,
        )
        val testeeIgnoreRuleNone = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", IGNORE, OWNER, CATEGORY, listOf(ruleNone))),
            mockUrlToTypeMapper,
        )

        assertTrue(testeeBlockRuleBlock.matches("http://api.tracker.com/auth/script.js".toUri(), DOCUMENT_URL, mapOf()).matches)
        assertTrue(testeeIgnoreRuleBlock.matches("http://api.tracker.com/auth/script.js".toUri(), DOCUMENT_URL, mapOf()).matches)
        assertFalse(testeeBlockRuleIgnore.matches("http://api.tracker.com/auth/script.js".toUri(), DOCUMENT_URL, mapOf()).matches)
        assertFalse(testeeIgnoreRuleIgnore.matches("http://api.tracker.com/auth/script.js".toUri(), DOCUMENT_URL, mapOf()).matches)
        assertTrue(testeeBlockRuleNone.matches("http://api.tracker.com/auth/script.js".toUri(), DOCUMENT_URL, mapOf()).matches)
        assertTrue(testeeIgnoreRuleNone.matches("http://api.tracker.com/auth/script.js".toUri(), DOCUMENT_URL, mapOf()).matches)
    }

    @Test
    fun whenUrlMatchesRuleWithExceptionsWithNoDomainsAndTypeMatchesExceptionThenMatchesIsFalseIrrespectiveOfAction() {
        whenever(mockUrlToTypeMapper.map(anyString(), anyMap())).thenReturn("something")

        val exceptions = RuleExceptions(null, listOf("something"))

        val ruleBlock = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null, null)
        val ruleIgnore = Rule("api\\.tracker\\.com\\/auth", IGNORE, exceptions, null, null)
        val ruleNone = Rule("api\\.tracker\\.com\\/auth", null, exceptions, null, null)

        val testeeBlockRuleBlock = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(ruleBlock))),
            mockUrlToTypeMapper,
        )
        val testeeIgnoreRuleBlock = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", IGNORE, OWNER, CATEGORY, listOf(ruleBlock))),
            mockUrlToTypeMapper,
        )
        val testeeBlockRuleIgnore = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(ruleIgnore))),
            mockUrlToTypeMapper,
        )
        val testeeIgnoreRuleIgnore = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", IGNORE, OWNER, CATEGORY, listOf(ruleIgnore))),
            mockUrlToTypeMapper,
        )
        val testeeBlockRuleNone = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(ruleNone))),
            mockUrlToTypeMapper,
        )
        val testeeIgnoreRuleNone = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", IGNORE, OWNER, CATEGORY, listOf(ruleNone))),
            mockUrlToTypeMapper,
        )

        assertFalse(testeeBlockRuleBlock.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf()).matches)
        assertFalse(testeeIgnoreRuleBlock.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf()).matches)
        assertFalse(testeeBlockRuleIgnore.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf()).matches)
        assertFalse(testeeIgnoreRuleIgnore.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf()).matches)
        assertFalse(testeeBlockRuleNone.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf()).matches)
        assertFalse(testeeIgnoreRuleNone.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf()).matches)
    }

    @Test
    fun whenUrlMatchesRuleWithExceptionsWithNoDomainsAndTypeMatchesExceptionThenMatchesIsFalseIrrespectiveOfAction2() {
        whenever(mockUrlToTypeMapper.map(anyString(), anyMap())).thenReturn("something")

        val exceptions = RuleExceptions(null, listOf("something"))

        val ruleBlock = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null, null)
        val ruleIgnore = Rule("api\\.tracker\\.com\\/auth", IGNORE, exceptions, null, null)
        val ruleNone = Rule("api\\.tracker\\.com\\/auth", null, exceptions, null, null)

        val testeeBlockRuleBlock = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(ruleBlock))),
            mockUrlToTypeMapper,
        )
        val testeeIgnoreRuleBlock = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", IGNORE, OWNER, CATEGORY, listOf(ruleBlock))),
            mockUrlToTypeMapper,
        )
        val testeeBlockRuleIgnore = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(ruleIgnore))),
            mockUrlToTypeMapper,
        )
        val testeeIgnoreRuleIgnore = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", IGNORE, OWNER, CATEGORY, listOf(ruleIgnore))),
            mockUrlToTypeMapper,
        )
        val testeeBlockRuleNone = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(ruleNone))),
            mockUrlToTypeMapper,
        )
        val testeeIgnoreRuleNone = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", IGNORE, OWNER, CATEGORY, listOf(ruleNone))),
            mockUrlToTypeMapper,
        )

        assertFalse(testeeBlockRuleBlock.matches("http://api.tracker.com/auth/script.js".toUri(), DOCUMENT_URL, mapOf()).matches)
        assertFalse(testeeIgnoreRuleBlock.matches("http://api.tracker.com/auth/script.js".toUri(), DOCUMENT_URL, mapOf()).matches)
        assertFalse(testeeBlockRuleIgnore.matches("http://api.tracker.com/auth/script.js".toUri(), DOCUMENT_URL, mapOf()).matches)
        assertFalse(testeeIgnoreRuleIgnore.matches("http://api.tracker.com/auth/script.js".toUri(), DOCUMENT_URL, mapOf()).matches)
        assertFalse(testeeBlockRuleNone.matches("http://api.tracker.com/auth/script.js".toUri(), DOCUMENT_URL, mapOf()).matches)
        assertFalse(testeeIgnoreRuleNone.matches("http://api.tracker.com/auth/script.js".toUri(), DOCUMENT_URL, mapOf()).matches)
    }

    @Test
    fun whenUrlMatchesRuleWithSurrogateThenMatchesIsTrueIrrespectiveOfActionExceptIgnore() {
        val exceptions = RuleExceptions(null, null)

        val ruleBlock = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, "testId", null)
        val ruleIgnore = Rule("api\\.tracker\\.com\\/auth", IGNORE, exceptions, "testId", null)
        val ruleNone = Rule("api\\.tracker\\.com\\/auth", null, exceptions, "testId", null)

        val testeeBlockRuleBlock = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(ruleBlock))),
            mockUrlToTypeMapper,
        )
        val testeeIgnoreRuleBlock = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", IGNORE, OWNER, CATEGORY, listOf(ruleBlock))),
            mockUrlToTypeMapper,
        )
        val testeeBlockRuleIgnore = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(ruleIgnore))),
            mockUrlToTypeMapper,
        )
        val testeeIgnoreRuleIgnore = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", IGNORE, OWNER, CATEGORY, listOf(ruleIgnore))),
            mockUrlToTypeMapper,
        )
        val testeeBlockRuleNone = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(ruleNone))),
            mockUrlToTypeMapper,
        )
        val testeeIgnoreRuleNone = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", IGNORE, OWNER, CATEGORY, listOf(ruleNone))),
            mockUrlToTypeMapper,
        )

        assertTrue(testeeBlockRuleBlock.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf()).matches)
        assertTrue(testeeIgnoreRuleBlock.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf()).matches)
        assertFalse(testeeBlockRuleIgnore.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf()).matches)
        assertFalse(testeeIgnoreRuleIgnore.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf()).matches)
        assertTrue(testeeBlockRuleNone.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf()).matches)
        assertTrue(testeeIgnoreRuleNone.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf()).matches)
    }

    @Test
    fun whenUrlMatchesRuleWithSurrogateThenMatchesIsTrueIrrespectiveOfActionExceptIgnore2() {
        val exceptions = RuleExceptions(null, null)

        val ruleBlock = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, "testId", null)
        val ruleIgnore = Rule("api\\.tracker\\.com\\/auth", IGNORE, exceptions, "testId", null)
        val ruleNone = Rule("api\\.tracker\\.com\\/auth", null, exceptions, "testId", null)

        val testeeBlockRuleBlock = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(ruleBlock))),
            mockUrlToTypeMapper,
        )
        val testeeIgnoreRuleBlock = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", IGNORE, OWNER, CATEGORY, listOf(ruleBlock))),
            mockUrlToTypeMapper,
        )
        val testeeBlockRuleIgnore = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(ruleIgnore))),
            mockUrlToTypeMapper,
        )
        val testeeIgnoreRuleIgnore = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", IGNORE, OWNER, CATEGORY, listOf(ruleIgnore))),
            mockUrlToTypeMapper,
        )
        val testeeBlockRuleNone = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(ruleNone))),
            mockUrlToTypeMapper,
        )
        val testeeIgnoreRuleNone = TdsClient(
            TDS,
            listOf(TdsTracker("tracker.com", IGNORE, OWNER, CATEGORY, listOf(ruleNone))),
            mockUrlToTypeMapper,
        )

        assertTrue(testeeBlockRuleBlock.matches("http://api.tracker.com/auth/script.js".toUri(), DOCUMENT_URL, mapOf()).matches)
        assertTrue(testeeIgnoreRuleBlock.matches("http://api.tracker.com/auth/script.js".toUri(), DOCUMENT_URL, mapOf()).matches)
        assertFalse(testeeBlockRuleIgnore.matches("http://api.tracker.com/auth/script.js".toUri(), DOCUMENT_URL, mapOf()).matches)
        assertFalse(testeeIgnoreRuleIgnore.matches("http://api.tracker.com/auth/script.js".toUri(), DOCUMENT_URL, mapOf()).matches)
        assertTrue(testeeBlockRuleNone.matches("http://api.tracker.com/auth/script.js".toUri(), DOCUMENT_URL, mapOf()).matches)
        assertTrue(testeeIgnoreRuleNone.matches("http://api.tracker.com/auth/script.js".toUri(), DOCUMENT_URL, mapOf()).matches)
    }

    @Test
    fun whenUrlMatchesRuleWithSurrogateThenSurrogateScriptIdReturned() {
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, null, "script.js", null)

        val testee = TdsClient(TDS, listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule))), mockUrlToTypeMapper)

        assertEquals("script.js", testee.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf()).surrogate)
    }

    @Test
    fun whenUrlMatchesRuleWithSurrogateThenSurrogateScriptIdReturned2() {
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, null, "script.js", null)

        val testee = TdsClient(TDS, listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule))), mockUrlToTypeMapper)

        assertEquals("script.js", testee.matches("http://api.tracker.com/auth/script.js".toUri(), DOCUMENT_URL, mapOf()).surrogate)
    }

    @Test
    fun whenUrlMatchesRuleWithTypeExceptionAndDomainsIsNullThenMatchesIsFalse() {
        whenever(mockUrlToTypeMapper.map(anyString(), anyMap())).thenReturn("image")
        val exceptions = RuleExceptions(null, listOf("image"))
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null, null)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/image.png", DOCUMENT_URL, mapOf())
        assertFalse(result.matches)
    }

    @Test
    fun whenUrlMatchesRuleWithTypeExceptionAndDomainsIsNullThenMatchesIsFalse2() {
        whenever(mockUrlToTypeMapper.map(anyString(), anyMap())).thenReturn("image")
        val exceptions = RuleExceptions(null, listOf("image"))
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null, null)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/image.png".toUri(), DOCUMENT_URL, mapOf())
        assertFalse(result.matches)
    }

    @Test
    fun whenUrlMatchesRuleWithTypeExceptionAndDomainsIsEmptyThenMatchesIsFalse() {
        whenever(mockUrlToTypeMapper.map(anyString(), anyMap())).thenReturn("image")
        val exceptions = RuleExceptions(emptyList(), listOf("image"))
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null, null)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/image.png", DOCUMENT_URL, mapOf())
        assertFalse(result.matches)
    }

    @Test
    fun whenUrlMatchesRuleWithTypeExceptionAndDomainsIsEmptyThenMatchesIsFalse2() {
        whenever(mockUrlToTypeMapper.map(anyString(), anyMap())).thenReturn("image")
        val exceptions = RuleExceptions(emptyList(), listOf("image"))
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null, null)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/image.png".toUri(), DOCUMENT_URL, mapOf())
        assertFalse(result.matches)
    }

    @Test
    fun whenUrlMatchesRuleWithDomainExceptionAndTypesIsNullThenMatchesIsFalse() {
        whenever(mockUrlToTypeMapper.map(anyString(), anyMap())).thenReturn("image")
        val exceptions = RuleExceptions(listOf("example.com"), null)
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null, null)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/image.png", DOCUMENT_URL, mapOf())
        assertFalse(result.matches)
    }

    @Test
    fun whenUrlMatchesRuleWithDomainExceptionAndTypesIsNullThenMatchesIsFalse2() {
        whenever(mockUrlToTypeMapper.map(anyString(), anyMap())).thenReturn("image")
        val exceptions = RuleExceptions(listOf("example.com"), null)
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null, null)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/image.png".toUri(), DOCUMENT_URL, mapOf())
        assertFalse(result.matches)
    }

    @Test
    fun whenUrlMatchesRuleWithDomainExceptionAndTypesIsEmptyThenMatchesIsFalse() {
        whenever(mockUrlToTypeMapper.map(anyString(), anyMap())).thenReturn("image")
        val exceptions = RuleExceptions(listOf("example.com"), emptyList())
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null, null)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/image.png", DOCUMENT_URL, mapOf())
        assertFalse(result.matches)
    }

    @Test
    fun whenUrlMatchesRuleWithDomainExceptionAndTypesIsEmptyThenMatchesIsFalse2() {
        whenever(mockUrlToTypeMapper.map(anyString(), anyMap())).thenReturn("image")
        val exceptions = RuleExceptions(listOf("example.com"), emptyList())
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null, null)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/image.png".toUri(), DOCUMENT_URL, mapOf())
        assertFalse(result.matches)
    }

    @Test
    fun whenUrlMatchesRuleWithDomainAndTypeExceptionThenMatchesIsFalse() {
        whenever(mockUrlToTypeMapper.map(anyString(), anyMap())).thenReturn("image")
        val exceptions = RuleExceptions(listOf("example.com"), listOf("image"))
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null, null)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/image.png", DOCUMENT_URL, mapOf())
        assertFalse(result.matches)
    }

    @Test
    fun whenUrlMatchesRuleWithDomainAndTypeExceptionThenMatchesIsFalse2() {
        whenever(mockUrlToTypeMapper.map(anyString(), anyMap())).thenReturn("image")
        val exceptions = RuleExceptions(listOf("example.com"), listOf("image"))
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null, null)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/image.png".toUri(), DOCUMENT_URL, mapOf())
        assertFalse(result.matches)
    }

    @Test
    fun whenUrlMatchesRuleWithDomainExceptionButNotTypeThenMatchesIsTrue() {
        whenever(mockUrlToTypeMapper.map(anyString(), anyMap())).thenReturn("image")
        val exceptions = RuleExceptions(listOf("example.com"), listOf("script"))
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null, null)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/image.png", DOCUMENT_URL, mapOf())
        assertTrue(result.matches)
    }

    @Test
    fun whenUrlMatchesRuleWithDomainExceptionButNotTypeThenMatchesIsTrue2() {
        whenever(mockUrlToTypeMapper.map(anyString(), anyMap())).thenReturn("image")
        val exceptions = RuleExceptions(listOf("example.com"), listOf("script"))
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null, null)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/image.png".toUri(), DOCUMENT_URL, mapOf())
        assertTrue(result.matches)
    }

    @Test
    fun whenUrlMatchesRuleWithTypeExceptionButNotDomainThenMatchesIsTrue() {
        whenever(mockUrlToTypeMapper.map(anyString(), anyMap())).thenReturn("image")
        val exceptions = RuleExceptions(listOf("foo.com"), listOf("image"))
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null, null)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/image.png", DOCUMENT_URL, mapOf())
        assertTrue(result.matches)
    }

    @Test
    fun whenUrlMatchesRuleWithTypeExceptionButNotDomainThenMatchesIsTrue2() {
        whenever(mockUrlToTypeMapper.map(anyString(), anyMap())).thenReturn("image")
        val exceptions = RuleExceptions(listOf("foo.com"), listOf("image"))
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null, null)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/image.png".toUri(), DOCUMENT_URL, mapOf())
        assertTrue(result.matches)
    }

    @Test
    fun whenUrlMatchesRuleForBlockedTrackerWithMatchingExceptionAndOptionTypeAndEmptyOptionDomainThenMatchesFalse() {
        // If option domain is empty and type is matching, should block would be false since exception is matching.
        val matchingType = "image"
        whenever(mockUrlToTypeMapper.map(anyString(), anyMap())).thenReturn(matchingType)
        val exceptions = RuleExceptions(listOf("example.com"), listOf(matchingType))
        val options = Options(domains = null, types = listOf(matchingType))
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null, options)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/image.png", DOCUMENT_URL, mapOf())
        assertFalse(result.matches)
    }

    @Test
    fun whenUrlMatchesRuleForBlockedTrackerWithMatchingExceptionAndOptionTypeAndEmptyOptionDomainThenMatchesFalse2() {
        // If option domain is empty and type is matching, should block would be false since exception is matching.
        val matchingType = "image"
        whenever(mockUrlToTypeMapper.map(anyString(), anyMap())).thenReturn(matchingType)
        val exceptions = RuleExceptions(listOf("example.com"), listOf(matchingType))
        val options = Options(domains = null, types = listOf(matchingType))
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null, options)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/image.png".toUri(), DOCUMENT_URL, mapOf())
        assertFalse(result.matches)
    }

    @Test
    fun whenUrlMatchesRuleForBlockedTrackerWithMatchingExceptionAndOptionDomainAndEmptyOptionTypeThenMatchesFalse() {
        // If option type is empty and domain is matching, should block would be false since exception is matching.
        val matchingDomain = "example.com"
        whenever(mockUrlToTypeMapper.map(anyString(), anyMap())).thenReturn("image")
        val exceptions = RuleExceptions(listOf(matchingDomain), listOf("image"))
        val options = Options(domains = listOf(matchingDomain), types = null)
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null, options)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/image.png", DOCUMENT_URL, mapOf())
        assertFalse(result.matches)
    }

    @Test
    fun whenUrlMatchesRuleForBlockedTrackerWithMatchingExceptionAndOptionDomainAndEmptyOptionTypeThenMatchesFalse2() {
        // If option type is empty and domain is matching, should block would be false since exception is matching.
        val matchingDomain = "example.com"
        whenever(mockUrlToTypeMapper.map(anyString(), anyMap())).thenReturn("image")
        val exceptions = RuleExceptions(listOf(matchingDomain), listOf("image"))
        val options = Options(domains = listOf(matchingDomain), types = null)
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null, options)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/image.png".toUri(), DOCUMENT_URL, mapOf())
        assertFalse(result.matches)
    }

    @Test
    fun whenUrlMatchesRuleForBlockedTrackerWithMatchingExceptionAndOptionDomainButNotOptionTypeThenMatchesTrue() {
        // If option type is not null and not matching, should block would be true since we will use the tracker's default action.
        val matchingDomain = "example.com"
        whenever(mockUrlToTypeMapper.map(anyString(), anyMap())).thenReturn("image")
        val exceptions = RuleExceptions(listOf(matchingDomain), listOf("image"))
        val options = Options(domains = listOf(matchingDomain), types = listOf("not-matching-type"))
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null, options)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/image.png", DOCUMENT_URL, mapOf())
        assertTrue(result.matches)
    }

    @Test
    fun whenUrlMatchesRuleForBlockedTrackerWithMatchingExceptionAndOptionDomainButNotOptionTypeThenMatchesTrue2() {
        // If option type is not null and not matching, should block would be true since we will use the tracker's default action.
        val matchingDomain = "example.com"
        whenever(mockUrlToTypeMapper.map(anyString(), anyMap())).thenReturn("image")
        val exceptions = RuleExceptions(listOf(matchingDomain), listOf("image"))
        val options = Options(domains = listOf(matchingDomain), types = listOf("not-matching-type"))
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null, options)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/image.png".toUri(), DOCUMENT_URL, mapOf())
        assertTrue(result.matches)
    }

    @Test
    fun whenUrlMatchesRuleForBlockedTrackerWithMatchingExceptionButNotOptionDomainAndOptionTypeThenMatchesTrue() {
        // If option domain is not null and not matching, should block would be true since we will use the tracker's default action.
        val matchingType = "image"
        whenever(mockUrlToTypeMapper.map(anyString(), anyMap())).thenReturn(matchingType)
        val exceptions = RuleExceptions(listOf("example.com"), listOf(matchingType))
        val options = Options(domains = listOf("not-matching-domain.com"), types = listOf(matchingType))
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null, options)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/image.png", DOCUMENT_URL, mapOf())
        assertTrue(result.matches)
    }

    @Test
    fun whenUrlMatchesRuleForBlockedTrackerWithMatchingExceptionButNotOptionDomainAndOptionTypeThenMatchesTrue2() {
        // If option domain is not null and not matching, should block would be true since we will use the tracker's default action.
        val matchingType = "image"
        whenever(mockUrlToTypeMapper.map(anyString(), anyMap())).thenReturn(matchingType)
        val exceptions = RuleExceptions(listOf("example.com"), listOf(matchingType))
        val options = Options(domains = listOf("not-matching-domain.com"), types = listOf(matchingType))
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null, options)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/image.png".toUri(), DOCUMENT_URL, mapOf())
        assertTrue(result.matches)
    }

    @Test
    fun whenHasOptionsButDoesntMatchDomainNorTypeThenMatchesTrue() {
        // If option type and domain are both not null and not matching, should block would be true since we will use the tracker's default action.
        whenever(mockUrlToTypeMapper.map(anyString(), anyMap())).thenReturn("image")
        val exceptions = RuleExceptions(listOf("example.com"), listOf("image"))
        val options = Options(domains = listOf("not-matching-domain.com"), types = listOf("not-matching-type"))
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null, options)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/image.png", DOCUMENT_URL, mapOf())
        assertTrue(result.matches)
    }

    @Test
    fun whenHasOptionsButDoesntMatchDomainNorTypeThenMatchesTrue2() {
        // If option type and domain are both not null and not matching, should block would be true since we will use the tracker's default action.
        whenever(mockUrlToTypeMapper.map(anyString(), anyMap())).thenReturn("image")
        val exceptions = RuleExceptions(listOf("example.com"), listOf("image"))
        val options = Options(domains = listOf("not-matching-domain.com"), types = listOf("not-matching-type"))
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null, options)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/image.png".toUri(), DOCUMENT_URL, mapOf())
        assertTrue(result.matches)
    }

    companion object {
        private const val OWNER = "A Network Owner"
        private const val DOCUMENT_URL = "http://example.com/index.htm"
        private val CATEGORY: List<String> = emptyList()
    }
}
