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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.trackerdetection.Client.ClientName.TDS
import com.duckduckgo.app.trackerdetection.model.Action.BLOCK
import com.duckduckgo.app.trackerdetection.model.Action.IGNORE
import com.duckduckgo.app.trackerdetection.model.Rule
import com.duckduckgo.app.trackerdetection.model.RuleExceptions
import com.duckduckgo.app.trackerdetection.model.TdsTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
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
    fun whenUrlHasSameDomainAsTrackerEntryAndDefaultActionIgnoreThenMatchesIsFalse() {
        val data = listOf(TdsTracker("tracker.com", IGNORE, OWNER, CATEGORY, emptyList()))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://tracker.com/script.js", DOCUMENT_URL, mapOf())
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
    fun whenUrlIsAParentDomainOfATrackerEntryThenMatchesIsFalse() {
        val data = listOf(TdsTracker("subdomain.tracker.com", BLOCK, OWNER, CATEGORY, emptyList()))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://tracker.com/script.js", DOCUMENT_URL, mapOf())
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
    fun whenUrlMatchesRuleWithNoExceptionsAndRuleActionBlockThenMatchesIsTrue() {
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, null, null)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf())
        assertTrue(result.matches)
    }

    @Test
    fun whenUrlMatchesRuleWithNoExceptionsAndRuleActionIgnoreThenMatchesIsFalse() {
        val rule = Rule("api\\.tracker\\.com\\/auth", IGNORE, null, null)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf())
        assertFalse(result.matches)
    }

    @Test
    fun whenUrlMatchesDomainWithDefaultBlockAndRuleWithNoExceptionsAndNoActionThenMatchesIsTrue() {
        val rule = Rule("api\\.tracker\\.com\\/auth", null, null, null)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf())
        assertTrue(result.matches)
    }

    @Test
    fun whenUrlMatchesDomainWithDefaultIgnoreAndRuleWithNoExceptionsAndNoActionThenMatchesIsTrue() {
        val rule = Rule("api\\.tracker\\.com\\/auth", null, null, null)
        val data = listOf(TdsTracker("tracker.com", IGNORE, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf())
        assertTrue(result.matches)
    }

    @Test
    fun whenUrlMatchesRuleWithExceptionsAndExceptionDomainMatchesDocumentThenMatchesIsFalseIrrespectiveOfAction() {
        val exceptions = RuleExceptions(listOf("example.com"), null)

        val ruleBlock = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null)
        val ruleIgnore = Rule("api\\.tracker\\.com\\/auth", IGNORE, exceptions, null)
        val ruleNone = Rule("api\\.tracker\\.com\\/auth", null, exceptions, null)

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
    fun whenUrlMatchesRuleWithExceptionsAndExceptionDomainDoesNotMatchDocumentThenMatchesBehaviorIsStandard() {
        val exceptions = RuleExceptions(listOf("nonmatching.com"), null)

        val ruleBlock = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null)
        val ruleIgnore = Rule("api\\.tracker\\.com\\/auth", IGNORE, exceptions, null)
        val ruleNone = Rule("api\\.tracker\\.com\\/auth", null, exceptions, null)

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
    fun whenUrlMatchesRuleWithExceptionsWithNoDomainsAndTypesIsNotNullThenMatchesIsFalseIrrespectiveOfAction() {
        val exceptions = RuleExceptions(null, listOf("something"))

        val ruleBlock = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null)
        val ruleIgnore = Rule("api\\.tracker\\.com\\/auth", IGNORE, exceptions, null)
        val ruleNone = Rule("api\\.tracker\\.com\\/auth", null, exceptions, null)

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
    fun whenUrlMatchesRuleWithSurrogateThenMatchesIsTrueIrrespectiveOfAction() {
        val exceptions = RuleExceptions(null, listOf("something"))

        val ruleBlock = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, "testId")
        val ruleIgnore = Rule("api\\.tracker\\.com\\/auth", IGNORE, exceptions, "testId")
        val ruleNone = Rule("api\\.tracker\\.com\\/auth", null, exceptions, "testId")

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
    fun whenUrlMatchesRuleWithSurrogateThenSurrogateScriptIdReturned() {
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, null, "script.js")

        val testee = TdsClient(TDS, listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule))), mockUrlToTypeMapper)

        assertEquals("script.js", testee.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL, mapOf()).surrogate)
    }

    @Test
    fun whenUrlMatchesRuleWithTypeExceptionThenMatchesIsTrue() {
        whenever(mockUrlToTypeMapper.map(anyString(), mapOf())).thenReturn("image")
        val exceptions = RuleExceptions(null, listOf("image"))
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions, null)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, CATEGORY, listOf(rule)))
        val testee = TdsClient(TDS, data, mockUrlToTypeMapper)
        val result = testee.matches("http://api.tracker.com/auth/image.png", DOCUMENT_URL, mapOf())
        assertTrue(result.matches)
    }

    companion object {
        private const val OWNER = "A Network Owner"
        private const val DOCUMENT_URL = "http://example.com/index.htm"
        private val CATEGORY: List<String> = emptyList()
    }
}
