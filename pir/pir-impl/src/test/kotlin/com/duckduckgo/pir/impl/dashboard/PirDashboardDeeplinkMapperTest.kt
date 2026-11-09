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
import com.duckduckgo.pir.impl.dashboard.entrypoint.PirFreeScanEntryPoint
import com.duckduckgo.pir.impl.dashboard.entrypoint.PirFreemiumDashboardScreens.FromEntryPoint
import com.duckduckgo.pir.impl.dashboard.entrypoint.PirFreemiumDashboardScreens.FromRemoteMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * RMF `navigation` actions with value `pir.main` open the PIR dashboard as a remote message launch, so the dashboard can
 * attribute the visit to the banner. The mappers under test are generated from the `@ContributeToActivityStarter`
 * annotations on [PirDashboardWebViewActivity].
 */
class PirDashboardDeeplinkMapperTest {

    private val dashboardScreenMapper = PirDashboardWebViewActivity_PirDashboardWebViewScreen_Mapper()
    private val fromEntryPointMapper = PirDashboardWebViewActivity_FromEntryPoint_Mapper()
    private val fromRemoteMessageMapper = PirDashboardWebViewActivity_FromRemoteMessage_Mapper()

    @Test
    fun whenRmfNavigatesToPirMainThenMapsToRemoteMessageLaunch() {
        assertEquals(FromRemoteMessage, fromRemoteMessageMapper.map(DeeplinkActivityParams(screenName = "pir.main", jsonArguments = "")))
    }

    @Test
    fun whenRmfNavigatesToPirMainThenOnlyTheRemoteMessageLaunchClaimsIt() {
        val pirMain = DeeplinkActivityParams(screenName = "pir.main", jsonArguments = "")

        assertNull(dashboardScreenMapper.map(pirMain))
        assertNull(fromEntryPointMapper.map(pirMain))
    }

    @Test
    fun whenDeeplinkScreenNameDoesNotMatchThenReturnsNull() {
        assertNull(fromRemoteMessageMapper.map(DeeplinkActivityParams(screenName = "pir.settings")))
        assertNull(fromRemoteMessageMapper.map(DeeplinkActivityParams(screenName = "PIR.MAIN")))
    }

    @Test
    fun whenStartedWithActivityParamsThenEachResolvesDashboardActivity() {
        assertEquals(PirDashboardWebViewActivity::class.java, dashboardScreenMapper.map(PirDashboardWebViewScreen))
        assertEquals(
            PirDashboardWebViewActivity::class.java,
            fromEntryPointMapper.map(FromEntryPoint(PirFreeScanEntryPoint.APP_SETTINGS_PRE_SCAN)),
        )
        assertEquals(PirDashboardWebViewActivity::class.java, fromRemoteMessageMapper.map(FromRemoteMessage))
    }
}
