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

package com.duckduckgo.pir.impl.common

import android.webkit.WebView
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.pir.impl.PirRemoteFeatures
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.ScanStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStepActions.ScanStepActions
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.scripts.models.BrokerAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class PirWorkDistributorTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val mockPirRemoteFeatures: PirRemoteFeatures = mock()
    private val mockWorkQueueToggle: Toggle = mock()

    private lateinit var testee: RealPirWorkDistributor

    @Before
    fun setUp() {
        whenever(mockPirRemoteFeatures.workQueueScheduling()).thenReturn(mockWorkQueueToggle)
        whenever(mockWorkQueueToggle.isEnabled()).thenReturn(true)
        testee = RealPirWorkDistributor(mockPirRemoteFeatures)
    }

    private val testProfileQuery = ProfileQuery(
        id = 123L,
        firstName = "John",
        lastName = "Doe",
        city = "New York",
        state = "NY",
        addresses = emptyList(),
        birthYear = 1990,
        fullName = "John Doe",
        age = 33,
        deprecated = false,
    )

    /**
     * A mock cannot express "this suspend call takes N ms" - delay() is not callable from a Mockito
     * answer - so the distribution tests use a fake driven by runTest's virtual clock.
     */
    private class FakeRunner(private val stepDurationMs: Long = 0L) : PirActionsRunner {
        val executed = mutableListOf<BrokerStep>()
        var stopCount = 0

        override fun stop() {
            stopCount++
        }

        override suspend fun execute(
            profileQuery: ProfileQuery,
            brokerStep: BrokerStep,
        ): Result<Unit> {
            delay(stepDurationMs)
            executed += brokerStep
            return Result.success(Unit)
        }

        override suspend fun executeOn(
            webView: WebView,
            profileQuery: ProfileQuery,
            brokerStep: BrokerStep,
        ): Result<Unit> = execute(profileQuery, brokerStep)
    }

    private fun scanStep(
        brokerName: String,
        actions: List<BrokerAction> = emptyList(),
        parent: String? = null,
    ): ScanStep = ScanStep(
        broker = Broker(
            name = brokerName,
            fileName = "$brokerName.json",
            url = "https://$brokerName.com",
            version = "1.0",
            parent = parent,
            addedDatetime = 1000L,
            removedAt = 0L,
        ),
        step = ScanStepActions(
            stepType = "scan",
            actions = actions,
            scanType = "data",
        ),
    )

    private fun navigate(id: String) = BrokerAction.Navigate(id = id, url = "https://example.com")
    private fun click(id: String) = BrokerAction.Click(id = id, elements = emptyList(), selector = null)

    private fun fillForm(id: String) = BrokerAction.FillForm(id = id, elements = emptyList(), selector = "form")

    private fun solveCaptcha(id: String) = BrokerAction.SolveCaptcha(id = id, selector = "captcha")

    private fun work(count: Int) = (1..count).map { testProfileQuery to scanStep("broker-$it") }

    @Test
    fun whenWorkIsDistributedThenEveryStepRunsExactlyOnce() = runTest {
        val runners = listOf(FakeRunner(), FakeRunner())
        val work = work(6)

        testee.executeAll(runners, work)

        val executed = runners.flatMap { it.executed }
        assertEquals(6, executed.size)
        assertEquals(work.map { it.second }.toSet(), executed.toSet())
    }

    @Test
    fun whenWorkIsDistributedThenEachRunnerIsStoppedOnce() = runTest {
        val runners = listOf(FakeRunner(), FakeRunner())

        testee.executeAll(runners, work(6))

        runners.forEach {
            assertEquals(1, it.stopCount)
        }
    }

    @Test
    fun whenOneRunnerIsSlowThenTheOtherTakesMoreSteps() = runTest {
        val slow = FakeRunner(stepDurationMs = 1_000)
        val fast = FakeRunner(stepDurationMs = 10)

        testee.executeAll(listOf(slow, fast), work(10))

        assertEquals(10, slow.executed.size + fast.executed.size)
        assertTrue(
            "fast runner should take more work than the slow one",
            fast.executed.size > slow.executed.size,
        )
    }

    @Test
    fun whenWorkIsEnqueuedThenMostExpensiveStepsGoFirst() = runTest {
        val runner = FakeRunner()
        val cheap = scanStep("cheap", actions = listOf(navigate("a")))
        val expensive = scanStep("expensive", actions = listOf(navigate("b"), click("c"), click("d")))

        testee.executeAll(
            listOf(runner),
            listOf(
                testProfileQuery to cheap,
                testProfileQuery to expensive,
            ),
        )

        assertEquals(listOf<BrokerStep>(expensive, cheap), runner.executed)
    }

    @Test
    fun whenStepCompletesThenCallbackIsInvokedOncePerStep() = runTest {
        var completed = 0

        testee.executeAll(listOf(FakeRunner()), work(4), onStepCompleted = { completed++ })

        assertEquals(4, completed)
    }

    @Test
    fun whenWorkIsEmptyThenNoRunnerIsTouched() = runTest {
        val runner = FakeRunner()

        testee.executeAll(listOf(runner), emptyList())

        assertEquals(0, runner.executed.size)
        assertEquals(0, runner.stopCount)
    }

    @Test
    fun whenThereAreMoreRunnersThanStepsThenSurplusRunnersAreNotTouched() = runTest {
        val used = FakeRunner()
        val surplus = FakeRunner()

        testee.executeAll(listOf(used, surplus), listOf(testProfileQuery to scanStep("only")))

        assertEquals(1, used.executed.size)
        assertEquals(1, used.stopCount)
        assertEquals(0, surplus.executed.size)
        assertEquals(0, surplus.stopCount)
    }

    @Test
    fun whenKillSwitchDisabledThenWorkIsStaticallyPartitionedInOriginalOrder() = runTest {
        whenever(mockWorkQueueToggle.isEnabled()).thenReturn(false)
        val runners = listOf(FakeRunner(), FakeRunner())
        val cheap = scanStep("cheap", actions = listOf(navigate("a")))
        val expensive = scanStep("expensive", actions = listOf(navigate("b"), click("c"), click("d")))
        val work = listOf(
            testProfileQuery to cheap,
            testProfileQuery to expensive,
            testProfileQuery to cheap,
            testProfileQuery to expensive,
        )

        testee.executeAll(runners, work)

        // Contiguous equal-count chunks, original order, fixed runner affinity - no cost ordering.
        assertEquals(listOf<BrokerStep>(cheap, expensive), runners[0].executed)
        assertEquals(listOf<BrokerStep>(cheap, expensive), runners[1].executed)
    }

    @Test
    fun whenKillSwitchDisabledThenEveryStepRunsOnceAndRunnersAreStopped() = runTest {
        whenever(mockWorkQueueToggle.isEnabled()).thenReturn(false)
        val runners = listOf(FakeRunner(), FakeRunner())
        var completed = 0

        testee.executeAll(runners, work(5), onStepCompleted = { completed++ })

        assertEquals(5, runners.flatMap { it.executed }.size)
        assertEquals(5, completed)
        runners.forEach { assertEquals(1, it.stopCount) }
    }

    @Test
    fun whenStepHasMoreGatedActionsThenItIsDequeuedFirst() = runTest {
        val runner = FakeRunner()
        val cheap = scanStep("cheap", actions = listOf(navigate("a"), navigate("b")))
        val expensive = scanStep("expensive", actions = listOf(navigate("a"), click("b")))

        testee.executeAll(listOf(runner), listOf(testProfileQuery to cheap, testProfileQuery to expensive))

        assertEquals(listOf<BrokerStep>(expensive, cheap), runner.executed)
    }

    @Test
    fun whenStepHasFillFormThenItIsDequeuedBeforeAPlainActionStep() = runTest {
        val runner = FakeRunner()
        val plain = scanStep("plain", actions = listOf(navigate("a")))
        val optOutForm = scanStep("form", actions = listOf(fillForm("b")))

        testee.executeAll(listOf(runner), listOf(testProfileQuery to plain, testProfileQuery to optOutForm))

        assertEquals(listOf<BrokerStep>(optOutForm, plain), runner.executed)
    }

    @Test
    fun whenStepHasSolveCaptchaThenItIsDequeuedBeforeAGatedActionStep() = runTest {
        val runner = FakeRunner()
        val gated = scanStep("gated", actions = listOf(click("a")))
        val captcha = scanStep("captcha", actions = listOf(solveCaptcha("b")))

        testee.executeAll(listOf(runner), listOf(testProfileQuery to gated, testProfileQuery to captcha))

        assertEquals(listOf<BrokerStep>(captcha, gated), runner.executed)
    }

    @Test
    fun whenOneOperatorOwnsSeveralStepsThenTheyAreSpacedApartInTheQueue() = runTest {
        val runner = FakeRunner()
        val mirrorActions = listOf(navigate("a"), click("b"), click("c"))
        val mirror1 = scanStep("mirror-1", actions = mirrorActions, parent = "operator.com")
        val mirror2 = scanStep("mirror-2", actions = mirrorActions, parent = "operator.com")
        val mirror3 = scanStep("mirror-3", actions = mirrorActions, parent = "operator.com")
        val other = scanStep("other", actions = listOf(navigate("d"), click("e")))
        val cheapest = scanStep("cheapest", actions = listOf(navigate("f")))

        testee.executeAll(
            listOf(runner),
            listOf(
                testProfileQuery to mirror1,
                testProfileQuery to mirror2,
                testProfileQuery to mirror3,
                testProfileQuery to other,
                testProfileQuery to cheapest,
            ),
        )

        // The operator's steps still lead (they are the most expensive) but are separated by the
        // other operators' steps rather than being handed out back to back.
        assertEquals(
            listOf<BrokerStep>(mirror1, other, cheapest, mirror2, mirror3),
            runner.executed,
        )
    }

    @Test
    fun whenOneOperatorDominatesTheWorkThenItDoesNotFillTheFirstWave() = runTest {
        // Mirrors of one operator share an action template, so they share a cost estimate. Cost
        // ordering alone would hand the whole first wave to that one operator, which is what
        // collapsed scan results: its sites then run their identical anti-bot challenge in lockstep.
        val runnerCount = 20
        val mirrorActions = listOf(navigate("a"), click("b"), click("c"), click("d"))
        val mirrors = (1..15).map {
            testProfileQuery to scanStep("mirror-$it", actions = mirrorActions, parent = "operator.com")
        }
        val others = (1..30).map {
            testProfileQuery to scanStep("other-$it", actions = listOf(navigate("a"), click("b")))
        }
        val runners = List(runnerCount) { FakeRunner(stepDurationMs = 1_000) }

        testee.executeAll(runners, mirrors + others)

        val firstWave = runners.mapNotNull { it.executed.firstOrNull() }
        assertEquals(runnerCount, firstWave.size)
        val fromOperator = firstWave.count { it.broker.parent == "operator.com" }
        assertTrue(
            "one operator took $fromOperator of the $runnerCount steps in the first wave",
            fromOperator <= 2,
        )
        assertEquals(45, runners.flatMap { it.executed }.size)
    }

    @Test
    fun whenOperatorsAreFewerThanRunnersThenEachOperatorHoldsRoughlyItsEvenShareOfTheFirstWave() = runTest {
        // The scan's real shape: 45 steps over 7 operators, run on 20 runners. A 20 wide wave cannot
        // hold one step per operator when there are only 7 of them, so the bound interleaving gives
        // is the even share - about runners / operators - rather than one.
        val runnerCount = 20
        val operatorSizes = mapOf(
            "a.com" to 15,
            "b.com" to 13,
            "c.com" to 7,
            "d.com" to 5,
            "e.com" to 3,
            "f.com" to 1,
            "g.com" to 1,
        )
        val work = operatorSizes.flatMap { (operator, size) ->
            (1..size).map {
                testProfileQuery to scanStep(
                    brokerName = "$operator-mirror-$it",
                    actions = listOf(navigate("a"), click("b")),
                    parent = operator,
                )
            }
        }
        val runners = List(runnerCount) { FakeRunner(stepDurationMs = 1_000) }

        testee.executeAll(runners, work)

        val firstWave = runners.mapNotNull { it.executed.firstOrNull() }
        assertEquals(runnerCount, firstWave.size)
        val worstOperatorShare = firstWave.groupingBy { it.broker.parent }.eachCount().values.max()
        assertTrue(
            "one operator took $worstOperatorShare of the $runnerCount steps in the first wave",
            worstOperatorShare <= 4,
        )
        assertEquals(operatorSizes.values.sum(), runners.flatMap { it.executed }.size)
    }

    @Test
    fun whenAParentAndItsMirrorsAreQueuedThenTheyCountAsOneOperator() = runTest {
        val runner = FakeRunner()
        val sharedActions = listOf(navigate("a"), click("b"))
        // A mirror's parent is the parent broker's url, so the two belong to the same operator.
        val parentBroker = scanStep("parent", actions = sharedActions).let {
            it.copy(broker = it.broker.copy(url = "operator.com"))
        }
        val mirror = scanStep("mirror", actions = sharedActions, parent = "operator.com")
        val unrelated = scanStep("unrelated", actions = sharedActions)

        testee.executeAll(
            listOf(runner),
            listOf(
                testProfileQuery to parentBroker,
                testProfileQuery to mirror,
                testProfileQuery to unrelated,
            ),
        )

        assertEquals(
            listOf<BrokerStep>(parentBroker, unrelated, mirror),
            runner.executed,
        )
    }
}
