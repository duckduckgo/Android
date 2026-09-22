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

package com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits

import com.duckduckgo.common.utils.CurrentTimeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UsageLimitsSnapshotParserTest {

    private val currentTimeProvider: CurrentTimeProvider = mock {
        whenever(it.currentTimeMillis()).thenReturn(NOW_MILLIS)
    }
    private val testee = UsageLimitsSnapshotParser(currentTimeProvider)

    @Test
    fun whenPayloadMatchesTheStorageContractThenEverythingIsParsed() {
        val snapshot = testee.parse(CONTRACT_SAMPLE)

        assertEquals(
            UsageLimitsSnapshot(
                notice = UsageNotice(
                    id = UsageNoticeId.APPROACHING,
                    window = UsageWindow.WEEKLY,
                    percentUsed = 75,
                    resetsAtMillis = FUTURE_MILLIS,
                    reached = false,
                    dismissible = true,
                ),
                cta = UsageCta(
                    id = UsageCtaId.SWITCH_TO_CHEAPER,
                    modelId = "claude-haiku-4-5",
                    modelIds = listOf("claude-haiku-4-5", "mistral-small-2603"),
                    byModelId = mapOf(
                        "claude-sonnet-4-6" to UsageCtaModelTargets(
                            modelId = "claude-haiku-4-5",
                            modelIds = listOf("claude-haiku-4-5", "mistral-small-2603"),
                        ),
                    ),
                    putEntries = emptyList(),
                ),
            ),
            snapshot,
        )
    }

    @Test
    fun whenPayloadHasOnlyLegacyWindowsThenNothingIsParsed() {
        assertNull(testee.parse("""{"daily":{"percentUsed":100,"resetsAt":"$FUTURE"},"weekly":{"percentUsed":75,"resetsAt":"$FUTURE"}}"""))
    }

    @Test
    fun whenPayloadIsEmptyMalformedOrBlankThenNothingIsParsed() {
        assertNull(testee.parse(null))
        assertNull(testee.parse(""))
        assertNull(testee.parse("   "))
        assertNull(testee.parse("{}"))
        assertNull(testee.parse("not json"))
        assertNull(testee.parse("""{"notice":"approaching"}"""))
    }

    @Test
    fun whenNoticeIsMissingARequiredFieldThenNothingIsParsed() {
        assertNull(testee.parse(notice("""{"window":"weekly","percentUsed":50,"resetsAt":"$FUTURE"}""")))
        assertNull(testee.parse(notice("""{"id":"approaching","percentUsed":50,"resetsAt":"$FUTURE"}""")))
        assertNull(testee.parse(notice("""{"id":"approaching","window":"weekly","resetsAt":"$FUTURE"}""")))
        assertNull(testee.parse(notice("""{"id":"approaching","window":"weekly","percentUsed":50}""")))
    }

    @Test
    fun whenNoticeIdOrWindowIsUnknownThenNothingIsParsed() {
        assertNull(testee.parse(notice("""{"id":"somethingNew","window":"weekly","percentUsed":50,"resetsAt":"$FUTURE"}""")))
        assertNull(testee.parse(notice("""{"id":"approaching","window":"month","percentUsed":50,"resetsAt":"$FUTURE"}""")))
    }

    @Test
    fun whenResetsAtIsNowOrPastOrInvalidThenNothingIsParsed() {
        assertNull(testee.parse(notice("""{"id":"approaching","window":"weekly","percentUsed":50,"resetsAt":"$NOW"}""")))
        assertNull(testee.parse(notice("""{"id":"approaching","window":"weekly","percentUsed":50,"resetsAt":"$PAST"}""")))
        assertNull(testee.parse(notice("""{"id":"approaching","window":"weekly","percentUsed":50,"resetsAt":"tomorrow"}""")))
    }

    @Test
    fun whenPercentIsOutOfRangeThenItIsClamped() {
        assertEquals(100, parsedNotice("""{"id":"dailyReached","window":"daily","percentUsed":140,"resetsAt":"$FUTURE"}""").percentUsed)
        assertEquals(0, parsedNotice("""{"id":"approaching","window":"daily","percentUsed":-5,"resetsAt":"$FUTURE"}""").percentUsed)
    }

    @Test
    fun whenReachedAndDismissibleAreOmittedThenDefaultsApply() {
        val notice = parsedNotice("""{"id":"approaching","window":"weekly","percentUsed":50,"resetsAt":"$FUTURE"}""")

        assertFalse(notice.reached)
        assertTrue(notice.dismissible)
    }

    @Test
    fun whenReachedIsTrueAndDismissibleOmittedThenNoticeIsNotDismissible() {
        val notice = parsedNotice("""{"id":"weeklyReached","window":"weekly","percentUsed":100,"resetsAt":"$FUTURE","reached":true}""")

        assertTrue(notice.reached)
        assertFalse(notice.dismissible)
    }

    @Test
    fun whenDismissibleIsExplicitThenItWins() {
        val notice = parsedNotice(
            """{"id":"weeklyReached","window":"weekly","percentUsed":100,"resetsAt":"$FUTURE","reached":true,"dismissible":true}""",
        )

        assertTrue(notice.dismissible)
    }

    @Test
    fun whenUnknownFieldsArePresentThenTheyAreIgnored() {
        val snapshot = testee.parse(
            """{"notice":{"id":"approaching","window":"weekly","percentUsed":50,"resetsAt":"$FUTURE","tier":"plus"},"extra":1}""",
        )

        assertNotNull(snapshot)
        assertNull(snapshot!!.cta)
    }

    @Test
    fun whenCtaIdIsUnknownOrMissingThenNoticeIsKeptWithoutCta() {
        val unknown = testee.parse(withCta("""{"id":"teleport","modelId":"x"}"""))
        val missing = testee.parse(withCta("""{"modelId":"x"}"""))

        assertNotNull(unknown!!.notice)
        assertNull(unknown.cta)
        assertNull(missing!!.cta)
    }

    @Test
    fun whenCtaHasOnlyByModelIdThenTopLevelTargetsAreEmpty() {
        val cta = testee.parse(
            withCta("""{"id":"switchToCheaper","byModelId":{"claude-sonnet-4-6":{"modelId":"claude-haiku-4-5","modelIds":["claude-haiku-4-5"]}}}"""),
        )!!.cta!!

        assertNull(cta.modelId)
        assertTrue(cta.modelIds.isEmpty())
        assertEquals(listOf("claude-haiku-4-5"), cta.byModelId.getValue("claude-sonnet-4-6").modelIds)
    }

    @Test
    fun whenCtaHasPutEntriesThenValuesAreKeptAsStrings() {
        val cta = testee.parse(
            withCta(
                """{"id":"bypassWeekly","putEntries":[
                    {"key":"duckai.fixedCostWindowBypassResetAtById","value":"{\"day\":\"$FUTURE\"}"},
                    {"key":"objectValue","value":{"a":1}},
                    {"key":"","value":"dropped"},
                    "not an object"
                ]}""",
            ),
        )!!.cta!!

        assertEquals(UsageCtaId.BYPASS_WEEKLY, cta.id)
        assertEquals(2, cta.putEntries.size)
        assertEquals(UsageCtaPutEntry("duckai.fixedCostWindowBypassResetAtById", """{"day":"$FUTURE"}"""), cta.putEntries[0])
        assertEquals("objectValue", cta.putEntries[1].key)
        assertEquals("""{"a":1}""", cta.putEntries[1].value)
    }

    @Test
    fun whenCtaIsSubscribeThenModelTargetsAreEmpty() {
        val cta = testee.parse(withCta("""{"id":"subscribe"}"""))!!.cta!!

        assertEquals(UsageCtaId.SUBSCRIBE, cta.id)
        assertNull(cta.modelId)
        assertTrue(cta.modelIds.isEmpty())
        assertTrue(cta.byModelId.isEmpty())
    }

    private fun notice(noticeJson: String) = """{"notice":$noticeJson}"""

    private fun withCta(ctaJson: String) =
        """{"notice":{"id":"approaching","window":"weekly","percentUsed":75,"resetsAt":"$FUTURE"},"cta":$ctaJson}"""

    private fun parsedNotice(noticeJson: String): UsageNotice = testee.parse(notice(noticeJson))!!.notice

    private companion object {
        const val NOW = "2026-08-25T12:00:00.000Z"
        const val NOW_MILLIS = 1787659200000L
        const val PAST = "2026-08-25T00:00:00.000Z"
        const val FUTURE = "2026-08-31T00:00:00.000Z"
        const val FUTURE_MILLIS = 1788134400000L

        val CONTRACT_SAMPLE = """
            {
              "daily":  { "percentUsed": 100, "resetsAt": "2026-08-25T00:00:00.000Z" },
              "weekly": { "percentUsed": 75, "resetsAt": "$FUTURE" },
              "notice": {
                "id": "approaching",
                "window": "weekly",
                "percentUsed": 75,
                "resetsAt": "$FUTURE",
                "reached": false,
                "dismissible": true
              },
              "cta": {
                "id": "switchToCheaper",
                "modelId": "claude-haiku-4-5",
                "modelIds": ["claude-haiku-4-5", "mistral-small-2603"],
                "byModelId": {
                  "claude-sonnet-4-6": {
                    "modelId": "claude-haiku-4-5",
                    "modelIds": ["claude-haiku-4-5", "mistral-small-2603"]
                  }
                }
              }
            }
        """.trimIndent()
    }
}
