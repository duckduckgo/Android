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

package com.duckduckgo.app.onboarding.ui.page.configdriven.engine

import android.animation.Animator
import android.view.View
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.app.onboarding.orchestrator.StepProgress
import com.duckduckgo.app.onboarding.ui.page.ComparisonChartConfig
import com.duckduckgo.app.onboarding.ui.page.OnboardingBackgroundStep
import com.duckduckgo.app.onboarding.ui.page.configdriven.BindScope
import com.duckduckgo.app.onboarding.ui.page.configdriven.CardArrowConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentHandle
import com.duckduckgo.app.onboarding.ui.page.configdriven.CtaAction
import com.duckduckgo.app.onboarding.ui.page.configdriven.CtaConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.DialogConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.Embellishment
import com.duckduckgo.app.onboarding.ui.page.configdriven.TextConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.onboarding.api.LinearOnboardingStepId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class DialogRenderEngineTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val content = FakeContentController()
    private val cardStage = FakeCardStage()
    private val background = FakeBackgroundController()
    private val embellishments = FakeEmbellishmentController()
    private val cardAnchor = FakeCardAnchorController()
    private val cardArrow = FakeCardArrowController()
    private val stepIndicator = FakeStepIndicatorController()

    private val emitted = mutableListOf<NewUserOnboardingEvent>()
    private val animatingChanges = mutableListOf<Boolean>()

    private val testee = DialogRenderEngine(
        content = content,
        cardStage = cardStage,
        background = background,
        embellishments = embellishments,
        cardAnchor = cardAnchor,
        cardArrow = cardArrow,
        stepIndicator = stepIndicator,
        emit = { emitted += it },
        execute = {},
        onAnimatingChanged = { animatingChanges += it },
    )

    @Test
    fun `first render resets the stage and applies every axis`() = runTest {
        testee.render(COMPARISON_STEP, comparisonConfig(), animate = true)

        assertTrue(content.stageReset)
        assertEquals(null to OnboardingBackgroundStep.ComparisonChart, background.applied)
        assertEquals(null to Embellishment.BottomWing, embellishments.applied)
        assertEquals(null to CardArrowConfig.AtEnd, cardArrow.applied)
        assertEquals(null to StepProgress(current = 1, total = 2), stepIndicator.applied)
    }

    @Test
    fun `second render diffs each axis against the previous config`() = runTest {
        testee.render(COMPARISON_STEP, comparisonConfig(), animate = true)
        content.stageReset = false

        testee.render(ADDRESS_BAR_STEP, addressBarConfig(), animate = true)

        assertFalse(content.stageReset)
        assertEquals(OnboardingBackgroundStep.ComparisonChart to OnboardingBackgroundStep.AddressBar, background.applied)
        assertEquals(Embellishment.BottomWing to Embellishment.BobbingDax, embellishments.applied)
        assertEquals(
            StepProgress(current = 1, total = 2) to StepProgress(current = 2, total = 2),
            stepIndicator.applied,
        )
    }

    @Test
    fun `re-emitting the same step and config does not re-render`() = runTest {
        testee.render(COMPARISON_STEP, comparisonConfig(), animate = true)
        val bindCount = content.bindCount

        testee.render(COMPARISON_STEP, comparisonConfig(), animate = true)

        assertEquals(bindCount, content.bindCount)
    }

    @Test
    fun `the same config on a different step re-renders`() = runTest {
        testee.render(COMPARISON_STEP, comparisonConfig(), animate = true)

        testee.render("another_step", comparisonConfig(), animate = true)

        assertEquals(2, content.bindCount)
    }

    @Test
    fun `a snapped render runs the whole pipeline without animating`() = runTest {
        testee.render(COMPARISON_STEP, comparisonConfig(), animate = false)

        assertEquals(listOf(false, false, false), cardStage.animateFlags)
        assertFalse(background.animated)
        assertFalse(embellishments.animated)
        assertEquals(1, cardStage.fadeCount)
    }

    @Test
    fun `an emit cta forwards its event as-is`() = runTest {
        testee.render(COMPARISON_STEP, comparisonConfig(), animate = false)

        cardStage.clickPrimary()

        assertEquals(listOf(NewUserOnboardingEvent.ContinueClicked), emitted)
    }

    @Test
    fun `a submit cta emits the event the bound screen builds`() = runTest {
        content.handleResult = { NewUserOnboardingEvent.AddressBarConfirmed(OmnibarType.SPLIT) }

        testee.render(ADDRESS_BAR_STEP, addressBarConfig(), animate = false)
        cardStage.clickPrimary()

        assertEquals(listOf(NewUserOnboardingEvent.AddressBarConfirmed(OmnibarType.SPLIT)), emitted)
    }

    @Test
    fun `an animated render reports animating until the entrance settles`() = runTest {
        cardStage.autoComplete = false

        testee.render(COMPARISON_STEP, comparisonConfig(), animate = true)
        assertEquals(listOf(true), animatingChanges)

        cardStage.completePendingStages()

        assertEquals(listOf(true, false), animatingChanges)
    }

    @Test
    fun `skip settles every axis`() = runTest {
        cardStage.autoComplete = false
        testee.render(COMPARISON_STEP, comparisonConfig(), animate = true)

        testee.skipRunningAnimations()

        assertTrue(cardStage.settled)
        assertTrue(background.skipped)
        assertTrue(embellishments.skipped)
        assertTrue(stepIndicator.skipped)
        assertTrue(cardArrow.skipped)
        assertEquals(listOf(true, false), animatingChanges)
    }

    @Test
    fun `an after-fade animator is started and ended on a snapped render`() = runTest {
        val animator: Animator = mock()
        content.afterFade = { animator }

        testee.render(COMPARISON_STEP, comparisonConfig(), animate = false)

        verify(animator).start()
        verify(animator).end()
    }

    @Test
    fun `release unbinds the content and cancels the after-fade animator`() = runTest {
        val animator: Animator = mock()
        content.afterFade = { animator }
        testee.render(COMPARISON_STEP, comparisonConfig(), animate = true)

        testee.release()

        verify(animator).cancel()
        assertTrue(content.hidden)
        assertTrue(cardStage.released)
        assertTrue(embellishments.released)
    }

    @Test
    fun `a superseded render does not continue its pipeline`() = runTest {
        cardStage.autoComplete = false
        testee.render(COMPARISON_STEP, comparisonConfig(), animate = true)
        val supersededStages = cardStage.takePendingStages()

        testee.render(ADDRESS_BAR_STEP, addressBarConfig(), animate = true)
        val fadeCountAfterSupersede = cardStage.fadeCount
        supersededStages.forEach { it() }

        assertEquals(fadeCountAfterSupersede, cardStage.fadeCount)
    }

    private companion object {
        const val COMPARISON_STEP: LinearOnboardingStepId = "comparison_chart"
        const val ADDRESS_BAR_STEP: LinearOnboardingStepId = "address_bar_position"

        fun comparisonConfig() = DialogConfig(
            background = OnboardingBackgroundStep.ComparisonChart,
            embellishment = Embellishment.BottomWing,
            cardArrow = CardArrowConfig.AtEnd,
            content = ContentConfig.ComparisonChart(
                title = TextConfig.Literal("comparison"),
                config = ComparisonChartConfig.Browser(isCustomAiCopy = false),
            ),
            primaryCta = CtaConfig(
                text = TextConfig.Literal("next"),
                action = CtaAction.Emit(NewUserOnboardingEvent.ContinueClicked),
            ),
            stepIndicator = StepProgress(current = 1, total = 2),
        )

        fun addressBarConfig() = DialogConfig(
            background = OnboardingBackgroundStep.AddressBar,
            embellishment = Embellishment.BobbingDax,
            cardArrow = CardArrowConfig.AtEnd,
            content = ContentConfig.AddressBar(
                title = TextConfig.Literal("address bar"),
                initialPosition = OmnibarType.SINGLE_TOP,
                showSplitOption = false,
            ),
            primaryCta = CtaConfig(text = TextConfig.Literal("next"), action = CtaAction.Submit),
            stepIndicator = StepProgress(current = 2, total = 2),
        )
    }
}

private class FakeContentController : ContentController {

    var stageReset = false
    var bindCount = 0
    var hidden = false
    var afterFade: (() -> Animator)? = null
    var handleResult: (() -> NewUserOnboardingEvent)? = null
    var unbindCount = 0

    override fun resetStage() {
        stageReset = true
    }

    override fun bind(stepId: LinearOnboardingStepId, content: ContentConfig, scope: BindScope): ContentHandle {
        bindCount++
        return ContentHandle(
            title = null,
            fadeTargets = emptyList(),
            afterFade = afterFade,
            result = handleResult,
            unbind = { unbindCount++ },
        )
    }

    override fun hideBound() {
        hidden = true
    }
}

private class FakeCardStage : CardStage {

    /** When false, stage continuations queue up in [pending] so a test can settle them explicitly. */
    var autoComplete = true
    var settled = false
    var released = false
    var fadeCount = 0
    val animateFlags = mutableListOf<Boolean>()

    private val pending = mutableListOf<() -> Unit>()
    private var primary: CtaConfig? = null
    private var onCtaClick: ((CtaConfig) -> Unit)? = null

    override fun reveal(animate: Boolean, onEnd: () -> Unit) = stage(animate, onEnd)

    override fun morph(animate: Boolean, onEnd: () -> Unit) = stage(animate, onEnd)

    override fun fadeInContent(contentTargets: List<View>, animate: Boolean, onEnd: () -> Unit) {
        fadeCount++
        stage(animate, onEnd)
    }

    override fun showCtaButtons(primary: CtaConfig?, secondary: CtaConfig?, onClick: (CtaConfig) -> Unit) {
        this.primary = primary
        onCtaClick = onClick
    }

    override fun prepareEntrance(contentTargets: List<View>) = Unit

    override fun settle() {
        settled = true
        completePendingStages()
    }

    override fun release() {
        released = true
        pending.clear()
    }

    fun clickPrimary() {
        primary?.let { cta -> onCtaClick?.invoke(cta) }
    }

    fun completePendingStages() {
        while (pending.isNotEmpty()) {
            pending.removeAt(0).invoke()
        }
    }

    fun takePendingStages(): List<() -> Unit> = pending.toList().also { pending.clear() }

    private fun stage(animate: Boolean, onEnd: () -> Unit) {
        animateFlags += animate
        if (autoComplete || !animate) onEnd() else pending += onEnd
    }
}

private class FakeBackgroundController : BackgroundController {

    var applied: Pair<OnboardingBackgroundStep?, OnboardingBackgroundStep>? = null
    var animated = false
    var skipped = false

    override fun apply(previous: OnboardingBackgroundStep?, next: OnboardingBackgroundStep, animate: Boolean) {
        applied = previous to next
        animated = animate
    }

    override fun skipRunning() {
        skipped = true
    }
}

private class FakeStepIndicatorController : StepIndicatorController {

    var applied: Pair<StepProgress?, StepProgress?>? = null
    var skipped = false
    var released = false

    override fun apply(previous: StepProgress?, next: StepProgress?, animate: Boolean) {
        applied = previous to next
    }

    override fun skipRunning() {
        skipped = true
    }

    override fun release() {
        released = true
    }
}

private class FakeCardArrowController : CardArrowController {

    var applied: Pair<CardArrowConfig?, CardArrowConfig>? = null
    var skipped = false

    override fun apply(previous: CardArrowConfig?, next: CardArrowConfig, animate: Boolean) {
        applied = previous to next
    }

    override fun skipRunning() {
        skipped = true
    }
}

private class FakeCardAnchorController : CardAnchorController {

    var applied = false

    override fun apply(settled: SettledDecoration?) {
        applied = true
    }
}

private class FakeEmbellishmentController : EmbellishmentController {

    var applied: Pair<Embellishment?, Embellishment>? = null
    var animated = false
    var skipped = false
    var released = false

    override fun transition(
        previous: Embellishment?,
        next: Embellishment,
        animate: Boolean,
        onSettled: (SettledDecoration?) -> Unit,
    ) {
        applied = previous to next
        animated = animate
        onSettled(null)
    }

    override fun skipRunning() {
        skipped = true
    }

    override fun release() {
        released = true
    }
}
