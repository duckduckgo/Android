/*
 * Copyright (c) 2019 DuckDuckGo
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

import com.duckduckgo.app.trackerdetection.Client.ClientName
import com.duckduckgo.app.trackerdetection.model.Action.BLOCK
import com.duckduckgo.app.trackerdetection.model.Action.IGNORE
import com.duckduckgo.app.trackerdetection.model.Rule
import com.duckduckgo.app.trackerdetection.model.RuleExceptions
import com.duckduckgo.app.trackerdetection.model.TdsTracker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TdsClientTest {

    @Test
    fun whenUrlHasSameDomainAsTrackerEntryAndDefaultActionBlockThenMatchesIsTrue() {
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, emptyList()))
        val testee = TdsClient(ClientName.TDS, data)
        assertTrue(testee.matches("http://tracker.com/script.js", DOCUMENT_URL))
    }

    @Test
    fun whenUrlHasSameDomainAsTrackerEntryAndDefaultActionIgnoreThenMatchesIsFalse() {
        val data = listOf(TdsTracker("tracker.com", IGNORE, OWNER, emptyList()))
        val testee = TdsClient(ClientName.TDS, data)
        assertFalse(testee.matches("http://tracker.com/script.js", DOCUMENT_URL))
    }

    @Test
    fun whenUrlIsSubdomainOfTrackerEntryAndDefaultActionBlockThenMatchesIsTrue() {
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, emptyList()))
        val testee = TdsClient(ClientName.TDS, data)
        assertTrue(testee.matches("http://subdomian.tracker.com/script.js", DOCUMENT_URL))
    }

    @Test
    fun whenUrlIsNotDomainOrSubDomainOfTrackerEntryThenMatchesIsFalse() {
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, emptyList()))
        val testee = TdsClient(ClientName.TDS, data)
        assertFalse(testee.matches("http://nontracker.com/script.js", DOCUMENT_URL))
    }

    @Test
    fun whenUrlIsAParentDomainOfATrackerEntryThenMatchesIsFalse() {
        val data = listOf(TdsTracker("subdomain.tracker.com", BLOCK, OWNER, emptyList()))
        val testee = TdsClient(ClientName.TDS, data)
        assertFalse(testee.matches("http://tracker.com/script.js", DOCUMENT_URL))
    }

    @Test
    fun whenUrlContainsButIsNotSubdomainOfATrackerEntryThenMatchesIsFalse() {
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, emptyList()))
        val testee = TdsClient(ClientName.TDS, data)
        assertFalse(testee.matches("http://notsubdomainoftracker.com", DOCUMENT_URL))
    }

    @Test
    fun whenUrlMatchesRuleWithNoExceptionsAndRuleActionBlockThenMatchesIsTrue() {
        val rule = Rule("api\\.tracker\\.com\\/auth", BLOCK, null)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, listOf(rule)))
        val testee = TdsClient(ClientName.TDS, data)
        assertTrue(testee.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL))
    }

    @Test
    fun whenUrlMatchesRuleWithNoExceptionsAndRuleActionIgnoreThenMatchesIsFalse() {
        val rule = Rule("api\\.tracker\\.com\\/auth", IGNORE, null)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, listOf(rule)))
        val testee = TdsClient(ClientName.TDS, data)
        assertFalse(testee.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL))
    }

    @Test
    fun whenUrlMatchesDomainWithDefaultBlockAndRuleWithNoExceptionsAndNoActionThenMatchesIsTrue() {
        val rule = Rule("api\\.tracker\\.com\\/auth", null, null)
        val data = listOf(TdsTracker("tracker.com", BLOCK, OWNER, listOf(rule)))
        val testee = TdsClient(ClientName.TDS, data)
        assertTrue(testee.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL))
    }

    @Test
    fun whenUrlMatchesDomainWithDefaultIgnoreAndRuleWithNoExceptionsAndNoActionThenMatchesIsTrue() {
        val rule = Rule("api\\.tracker\\.com\\/auth", null, null)
        val data = listOf(TdsTracker("tracker.com", IGNORE, OWNER, listOf(rule)))
        val testee = TdsClient(ClientName.TDS, data)
        assertTrue(testee.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL))
    }

    @Test
    fun whenUrlMatchesRuleWithExceptionsAndExceptionDomainMatchesDocumentThenMatchesIsFalseIrrespectiveOfAction() {
        val exceptions = RuleExceptions(listOf("example.com"), null)

        val ruleActionBlock = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions)
        val ruleActionIgnore = Rule("api\\.tracker\\.com\\/auth", IGNORE, exceptions)
        val ruleActionNone = Rule("api\\.tracker\\.com\\/auth", null, exceptions)

        val testeeBlockWithRuleBlock = TdsClient(ClientName.TDS, listOf(TdsTracker("tracker.com", BLOCK, OWNER, listOf(ruleActionBlock))))
        val testeeIgnoreWithRuleBlock = TdsClient(ClientName.TDS, listOf(TdsTracker("tracker.com", IGNORE, OWNER, listOf(ruleActionBlock))))
        val testeeBlockWithRuleIgnore = TdsClient(ClientName.TDS, listOf(TdsTracker("tracker.com", BLOCK, OWNER, listOf(ruleActionIgnore))))
        val testeeIgnoreWithRuleIgnore = TdsClient(ClientName.TDS, listOf(TdsTracker("tracker.com", IGNORE, OWNER, listOf(ruleActionIgnore))))
        val testeeBlockWithRuleNone = TdsClient(ClientName.TDS, listOf(TdsTracker("tracker.com", BLOCK, OWNER, listOf(ruleActionNone))))
        val testeeIgnoreWithRuleNone = TdsClient(ClientName.TDS, listOf(TdsTracker("tracker.com", IGNORE, OWNER, listOf(ruleActionNone))))

        assertFalse(testeeBlockWithRuleBlock.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL))
        assertFalse(testeeIgnoreWithRuleBlock.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL))
        assertFalse(testeeBlockWithRuleIgnore.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL))
        assertFalse(testeeIgnoreWithRuleIgnore.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL))
        assertFalse(testeeBlockWithRuleNone.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL))
        assertFalse(testeeIgnoreWithRuleNone.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL))
    }

    @Test
    fun whenUrlMatchesRuleWithExceptionsAndExceptionDomainDoesNotMatchDocumentThenMatchesBehaviorIsStandard() {
        val exceptions = RuleExceptions(listOf("nonmatching.com"), null)

        val ruleActionBlock = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions)
        val ruleActionIgnore = Rule("api\\.tracker\\.com\\/auth", IGNORE, exceptions)
        val ruleActionNone = Rule("api\\.tracker\\.com\\/auth", null, exceptions)

        val testeeBlockWithRuleBlock = TdsClient(ClientName.TDS, listOf(TdsTracker("tracker.com", BLOCK, OWNER, listOf(ruleActionBlock))))
        val testeeIgnoreWithRuleBlock = TdsClient(ClientName.TDS, listOf(TdsTracker("tracker.com", IGNORE, OWNER, listOf(ruleActionBlock))))
        val testeeBlockWithRuleIgnore = TdsClient(ClientName.TDS, listOf(TdsTracker("tracker.com", BLOCK, OWNER, listOf(ruleActionIgnore))))
        val testeeIgnoreWithRuleIgnore = TdsClient(ClientName.TDS, listOf(TdsTracker("tracker.com", IGNORE, OWNER, listOf(ruleActionIgnore))))
        val testeeBlockWithRuleNone = TdsClient(ClientName.TDS, listOf(TdsTracker("tracker.com", BLOCK, OWNER, listOf(ruleActionNone))))
        val testeeIgnoreWithRuleNone = TdsClient(ClientName.TDS, listOf(TdsTracker("tracker.com", IGNORE, OWNER, listOf(ruleActionNone))))

        assertTrue(testeeBlockWithRuleBlock.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL))
        assertTrue(testeeIgnoreWithRuleBlock.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL))
        assertFalse(testeeBlockWithRuleIgnore.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL))
        assertFalse(testeeIgnoreWithRuleIgnore.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL))
        assertTrue(testeeBlockWithRuleNone.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL))
        assertTrue(testeeIgnoreWithRuleNone.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL))
    }

    @Test
    fun whenUrlMatchesRuleWithExceptionsWithNoDomainsAndTypesIsNotNullThenMatchesIsFalseIrrespectiveOfAction() {
        val exceptions = RuleExceptions(null, listOf("something"))

        val ruleActionBlock = Rule("api\\.tracker\\.com\\/auth", BLOCK, exceptions)
        val ruleActionIgnore = Rule("api\\.tracker\\.com\\/auth", IGNORE, exceptions)
        val ruleActionNone = Rule("api\\.tracker\\.com\\/auth", null, exceptions)

        val testeeBlockWithRuleBlock = TdsClient(ClientName.TDS, listOf(TdsTracker("tracker.com", BLOCK, OWNER, listOf(ruleActionBlock))))
        val testeeIgnoreWithRuleBlock = TdsClient(ClientName.TDS, listOf(TdsTracker("tracker.com", IGNORE, OWNER, listOf(ruleActionBlock))))
        val testeeBlockWithRuleIgnore = TdsClient(ClientName.TDS, listOf(TdsTracker("tracker.com", BLOCK, OWNER, listOf(ruleActionIgnore))))
        val testeeIgnoreWithRuleIgnore = TdsClient(ClientName.TDS, listOf(TdsTracker("tracker.com", IGNORE, OWNER, listOf(ruleActionIgnore))))
        val testeeBlockWithRuleNone = TdsClient(ClientName.TDS, listOf(TdsTracker("tracker.com", BLOCK, OWNER, listOf(ruleActionNone))))
        val testeeIgnoreWithRuleNone = TdsClient(ClientName.TDS, listOf(TdsTracker("tracker.com", IGNORE, OWNER, listOf(ruleActionNone))))

        assertFalse(testeeBlockWithRuleBlock.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL))
        assertFalse(testeeIgnoreWithRuleBlock.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL))
        assertFalse(testeeBlockWithRuleIgnore.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL))
        assertFalse(testeeIgnoreWithRuleIgnore.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL))
        assertFalse(testeeBlockWithRuleNone.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL))
        assertFalse(testeeIgnoreWithRuleNone.matches("http://api.tracker.com/auth/script.js", DOCUMENT_URL))
    }

    companion object {
        private const val OWNER = "A Network Owner"
        private const val DOCUMENT_URL = "http://example.com/index.htm"
    }
}