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

package com.duckduckgo.agesignals.impl

import android.content.Context
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.google.android.play.agesignals.AgeSignalsManager
import com.google.android.play.agesignals.testing.FakeAgeSignalsManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class AgeSignalsManagerProviderTest {

    private val context: Context = mock()
    private val playManager: AgeSignalsManager = mock()
    private val scenarioStore = FakeScenarioStore()

    @Test
    fun whenPlayBuildHasAStoredFakeScenarioThenItGetsTheRealManager() = runTest {
        scenarioStore.setScenario(AgeSignalsScenario.SHARED)

        val manager = provider(BuildFlavor.PLAY).get()

        assertSame(playManager, manager)
    }

    @Test
    fun whenInternalBuildHasAStoredFakeScenarioThenItGetsAFakeManager() = runTest {
        scenarioStore.setScenario(AgeSignalsScenario.SHARED)

        val manager = provider(BuildFlavor.INTERNAL).get()

        assertTrue(manager is FakeAgeSignalsManager)
    }

    @Test
    fun whenInternalBuildSelectsRealPlayThenItGetsTheRealManager() = runTest {
        scenarioStore.setScenario(AgeSignalsScenario.REAL_PLAY)

        val manager = provider(BuildFlavor.INTERNAL).get()

        assertSame(playManager, manager)
    }

    private fun provider(flavor: BuildFlavor): RealAgeSignalsManagerProvider {
        val appBuildConfig: AppBuildConfig = mock { on { this.flavor } doReturn flavor }
        return RealAgeSignalsManagerProvider(context, appBuildConfig, scenarioStore) { playManager }
    }

    private class FakeScenarioStore : AgeSignalsScenarioStore {
        private var scenario = AgeSignalsScenario.REAL_PLAY
        override suspend fun scenario(): AgeSignalsScenario = scenario
        override suspend fun setScenario(scenario: AgeSignalsScenario) {
            this.scenario = scenario
        }
    }
}
