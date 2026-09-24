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

import com.duckduckgo.duckchat.impl.models.AIChatModel
import com.duckduckgo.duckchat.impl.models.ModelState
import com.duckduckgo.duckchat.impl.models.Tool
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageLimitCtaResolverTest {

    private val testee = UsageLimitCtaResolver()

    private val haiku = model("claude-haiku-4-5")
    private val mistral =
        model("mistral-small-2603", supportsImageUpload = true, supportedFileTypes = listOf("application/pdf"), tools = listOf(Tool.WEB_SEARCH))
    private val sonnet = model("claude-sonnet-4-6")
    private val locked = model("gpt-locked", isAccessible = false)
    private val state = ModelState(models = listOf(haiku, mistral, sonnet, locked), selectedModelId = sonnet.id, isSubscriptionEligible = true)

    @Test
    fun whenCtaIsMissingThenNothingIsResolved() {
        assertNull(testee.resolve(null, state, emptyDraft(), freeTrialEligible = false))
    }

    @Test
    fun whenPickerModelHasATargetTableThenItWinsOverTopLevelTargets() {
        val cta =
            switchCta(modelId = haiku.id, byModelId = mapOf(sonnet.id to UsageCtaModelTargets(modelId = mistral.id, modelIds = listOf(haiku.id))))

        val resolved = testee.resolve(cta, state, emptyDraft(), freeTrialEligible = false) as ResolvedUsageCta.SwitchModel

        assertEquals(mistral, resolved.model)
        assertEquals(listOf(mistral.id, haiku.id), resolved.candidateIds)
    }

    @Test
    fun whenNoTableEntryForPickerModelThenTopLevelTargetsAreUsedInOrder() {
        val cta =
            switchCta(
                modelId = haiku.id,
                modelIds = listOf(haiku.id, mistral.id),
                byModelId = mapOf("other" to UsageCtaModelTargets(mistral.id, emptyList())),
            )

        val resolved = testee.resolve(cta, state, emptyDraft(), freeTrialEligible = false) as ResolvedUsageCta.SwitchModel

        assertEquals(haiku, resolved.model)
        assertEquals(listOf(haiku.id, mistral.id), resolved.candidateIds)
    }

    @Test
    fun whenTheOnlyTargetIsTheCurrentModelThenNothingIsResolved() {
        assertNull(testee.resolve(switchCta(modelId = sonnet.id), state, emptyDraft(), freeTrialEligible = false))
    }

    @Test
    fun whenTargetsAreUnknownOrInaccessibleThenTheyAreSkipped() {
        val cta = switchCta(modelIds = listOf("not-in-list", locked.id, haiku.id))

        val resolved = testee.resolve(cta, state, emptyDraft(), freeTrialEligible = false) as ResolvedUsageCta.SwitchModel

        assertEquals(haiku, resolved.model)
    }

    @Test
    fun whenDraftHasImagesThenOnlyImageCapableTargetsQualify() {
        val cta = switchCta(modelIds = listOf(haiku.id, mistral.id))

        val resolved = testee.resolve(cta, state, emptyDraft().copy(hasImages = true), freeTrialEligible = false) as ResolvedUsageCta.SwitchModel

        assertEquals(mistral, resolved.model)
    }

    @Test
    fun whenDraftHasFilesThenTargetsMustSupportEveryMimeType() {
        val cta = switchCta(modelIds = listOf(haiku.id, mistral.id))

        val pdfOnly = testee.resolve(cta, state, emptyDraft().copy(fileMimeTypes = listOf("application/pdf")), freeTrialEligible = false)
        val pdfAndDoc = testee.resolve(
            cta,
            state,
            emptyDraft().copy(fileMimeTypes = listOf("application/pdf", "text/plain")),
            freeTrialEligible = false,
        )

        assertEquals(mistral, (pdfOnly as ResolvedUsageCta.SwitchModel).model)
        assertNull(pdfAndDoc)
    }

    @Test
    fun whenDraftHasAToolThenTargetsMustSupportIt() {
        val cta = switchCta(modelIds = listOf(haiku.id, mistral.id))

        val resolved = testee.resolve(cta, state, emptyDraft().copy(selectedTool = Tool.WEB_SEARCH.rawValue), freeTrialEligible = false)

        assertEquals(mistral, (resolved as ResolvedUsageCta.SwitchModel).model)
    }

    @Test
    fun whenCtaIsBypassWeeklyThenPutEntriesAreCarried() {
        val entries = listOf(UsageCtaPutEntry("k", "v"))
        val cta = UsageCta(UsageCtaId.BYPASS_WEEKLY, modelId = null, modelIds = emptyList(), byModelId = emptyMap(), putEntries = entries)

        assertEquals(ResolvedUsageCta.StartUsingWeeklyLimit(entries), testee.resolve(cta, state, emptyDraft(), freeTrialEligible = false))
    }

    @Test
    fun whenCtaIsSubscribeThenEligibilityAndTrialAreHonoured() {
        val cta = UsageCta(UsageCtaId.SUBSCRIBE, modelId = null, modelIds = emptyList(), byModelId = emptyMap(), putEntries = emptyList())

        assertEquals(ResolvedUsageCta.Subscribe(freeTrialEligible = true), testee.resolve(cta, state, emptyDraft(), freeTrialEligible = true))
        assertEquals(ResolvedUsageCta.Subscribe(freeTrialEligible = false), testee.resolve(cta, state, emptyDraft(), freeTrialEligible = false))
        assertNull(testee.resolve(cta, state.copy(isSubscriptionEligible = false), emptyDraft(), freeTrialEligible = true))
    }

    @Test
    fun whenCandidateIdsAreComputedThenDuplicatesAndCurrentModelAreDropped() {
        val cta = switchCta(modelId = haiku.id, modelIds = listOf(haiku.id, sonnet.id, mistral.id))

        assertEquals(listOf(haiku.id, mistral.id), testee.candidateIds(cta, sonnet.id))
        assertTrue(testee.candidateIds(switchCta(), null).isEmpty())
    }

    private fun switchCta(
        modelId: String? = null,
        modelIds: List<String> = emptyList(),
        byModelId: Map<String, UsageCtaModelTargets> = emptyMap(),
    ) = UsageCta(UsageCtaId.SWITCH_TO_CHEAPER, modelId = modelId, modelIds = modelIds, byModelId = byModelId, putEntries = emptyList())

    private fun emptyDraft() = NativeInputFooterDraft(hasImages = false, fileMimeTypes = emptyList(), selectedTool = null)

    private fun model(
        id: String,
        isAccessible: Boolean = true,
        supportsImageUpload: Boolean = false,
        supportedFileTypes: List<String> = emptyList(),
        tools: List<Tool> = emptyList(),
    ) = AIChatModel(
        id = id,
        name = id,
        displayName = id,
        shortName = id,
        accessTier = listOf("free"),
        isAccessible = isAccessible,
        supportsImageUpload = supportsImageUpload,
        supportedFileTypes = supportedFileTypes,
        supportedTools = tools,
    )
}
