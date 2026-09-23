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

package com.duckduckgo.pir.impl.freemium

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.pir.impl.common.BrokerStepsParser
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.ScanStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStepActions.ScanStepActions
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.scripts.models.BrokerAction
import com.duckduckgo.pir.impl.store.PirRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealPirFreeScanBrokerFilterTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val mockPirRepository: PirRepository = mock()
    private val mockBrokerStepsParser: BrokerStepsParser = mock()

    private lateinit var testee: RealPirFreeScanBrokerFilter

    @Before
    fun setUp() {
        testee = RealPirFreeScanBrokerFilter(
            pirRepository = mockPirRepository,
            brokerStepsParser = mockBrokerStepsParser,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenScanStepHasNoTokenActionsThenBrokerIsFreeScannable() = runTest {
        val broker = broker("Ungated")
        stubScanStep(broker, listOf(navigate(), extract()))

        assertEquals(listOf(broker), testee.excludingGatedBrokers(listOf(broker)))
    }

    @Test
    fun whenScanStepHasTopLevelCaptchaActionThenBrokerIsExcluded() = runTest {
        val broker = broker("Captcha")
        stubScanStep(broker, listOf(navigate(), BrokerAction.GetCaptchaInfo(id = "c", selector = ".captcha")))

        assertTrue(testee.excludingGatedBrokers(listOf(broker)).isEmpty())
    }

    @Test
    fun whenTokenActionIsNestedInsideConditionThenBrokerIsExcluded() = runTest {
        val broker = broker("NestedCaptcha")
        val condition = BrokerAction.Condition(
            id = "cond",
            comment = "",
            expectations = emptyList(),
            actions = listOf(BrokerAction.SolveCaptcha(id = "s", selector = ".captcha")),
        )
        stubScanStep(broker, listOf(navigate(), condition))

        assertTrue(testee.excludingGatedBrokers(listOf(broker)).isEmpty())
    }

    @Test
    fun whenScanStepCannotBeParsedThenBrokerIsNotFreeScannable() = runTest {
        val broker = broker("Unparseable")
        whenever(mockPirRepository.getBrokerScanSteps(broker.name)).thenReturn("{}")
        whenever(mockBrokerStepsParser.parseStep(broker, "{}")).thenReturn(emptyList())
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(broker))

        assertTrue(testee.freeScannableBrokerNames().isEmpty())
    }

    @Test
    fun whenBrokerHasNoScanStepsThenItIsNotFreeScannableButIsNotTreatedAsGated() = runTest {
        val broker = broker("NoSteps")
        whenever(mockPirRepository.getBrokerScanSteps(broker.name)).thenReturn(null)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(broker))

        assertFalse(testee.excludingGatedBrokers(listOf(broker)).isEmpty())
        assertTrue(testee.freeScannableBrokerNames().isEmpty())
    }

    @Test
    fun whenFreeScannableBrokerNamesThenGatedAndUnparseableBrokersAreBothDropped() = runTest {
        val ungated = broker("Ungated")
        val gated = broker("Gated")
        val unparseable = broker("Unparseable")
        stubScanStep(ungated, listOf(navigate()))
        stubScanStep(gated, listOf(BrokerAction.GenerateEmail(id = "g")))
        whenever(mockPirRepository.getBrokerScanSteps(unparseable.name)).thenReturn("{}")
        whenever(mockBrokerStepsParser.parseStep(unparseable, "{}")).thenReturn(emptyList())
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(ungated, gated, unparseable))

        assertEquals(setOf("Ungated"), testee.freeScannableBrokerNames())
    }

    @Test
    fun whenSameBrokerVersionQueriedTwiceThenScanStepsAreParsedOnce() = runTest {
        val broker = broker("Cached")
        stubScanStep(broker, listOf(navigate()))

        testee.excludingGatedBrokers(listOf(broker))
        testee.excludingGatedBrokers(listOf(broker))

        verify(mockBrokerStepsParser, times(1)).parseStep(broker, SCAN_STEPS_JSON)
    }

    @Test
    fun whenBrokerAlreadyClassifiedByExcludingGatedBrokersThenFreeScannableBrokerNamesReusesTheClassification() = runTest {
        val broker = broker("Cached")
        stubScanStep(broker, listOf(navigate()))
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(broker))

        testee.excludingGatedBrokers(listOf(broker))
        testee.freeScannableBrokerNames()

        verify(mockBrokerStepsParser, times(1)).parseStep(broker, SCAN_STEPS_JSON)
    }

    private fun broker(name: String) = Broker(
        name = name,
        fileName = "$name.json",
        url = "$name.com",
        version = "1.0.0",
        parent = null,
        addedDatetime = 0L,
        removedAt = 0L,
    )

    private suspend fun stubScanStep(
        broker: Broker,
        actions: List<BrokerAction>,
    ) {
        whenever(mockPirRepository.getBrokerScanSteps(broker.name)).thenReturn(SCAN_STEPS_JSON)
        whenever(mockBrokerStepsParser.parseStep(broker, SCAN_STEPS_JSON)).thenReturn(
            listOf(ScanStep(broker = broker, step = ScanStepActions(stepType = "scan", actions = actions, scanType = "templatedUrl"))),
        )
    }

    private fun navigate() = BrokerAction.Navigate(id = "n", url = "https://example.com")

    private fun extract() = BrokerAction.Extract(id = "e", selector = ".result", noResultsSelector = null, profile = emptyMap())

    private companion object {
        const val SCAN_STEPS_JSON = """{"stepType":"scan"}"""
    }
}
