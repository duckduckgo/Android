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

package com.duckduckgo.pir.impl.dashboard

import com.duckduckgo.navigation.api.GlobalActivityStarter.DeeplinkActivityParams
import com.duckduckgo.pir.api.PirScreens.PirDashboardWebViewScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * RMF `navigation` actions with value `pir.main` open the PIR dashboard. The mapper under test is generated from the
 * `@ContributeToActivityStarter(..., screenName = "pir.main")` annotation on [PirDashboardWebViewActivity].
 */
class PirDashboardDeeplinkMapperTest {

    private val mapper = PirDashboardWebViewActivity_PirDashboardWebViewScreen_Mapper()

    @Test
    fun whenRmfNavigatesToPirMainThenMapsToDashboardScreen() {
        assertEquals(PirDashboardWebViewScreen, mapper.map(DeeplinkActivityParams(screenName = "pir.main", jsonArguments = "")))
    }

    @Test
    fun whenDeeplinkScreenNameDoesNotMatchThenReturnsNull() {
        assertNull(mapper.map(DeeplinkActivityParams(screenName = "pir.settings")))
        assertNull(mapper.map(DeeplinkActivityParams(screenName = "PIR.MAIN")))
    }

    @Test
    fun whenStartedWithActivityParamsThenStillResolvesDashboardActivity() {
        assertEquals(PirDashboardWebViewActivity::class.java, mapper.map(PirDashboardWebViewScreen))
    }
}
