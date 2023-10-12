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

package com.duckduckgo.privacy.dashboard.impl.ui

import android.os.Build.VERSION_CODES
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.domain
import com.duckduckgo.app.privacy.db.UserAllowListDao
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.privacy.config.api.ContentBlocking
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.duckduckgo.privacy.dashboard.impl.pixels.FakePrivacyDashboardRemoteFeatureFactory
import com.duckduckgo.privacy.dashboard.impl.pixels.PrivacyDashboardPixels.PRIVACY_DASHBOARD_ALLOWLIST_ADD
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command.LaunchReportBrokenSite
import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class PrivacyDashboardHybridViewModelTest {

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val androidQAppBuildConfig = mock<AppBuildConfig>().apply {
        whenever(this.sdkInt).thenReturn(VERSION_CODES.Q)
    }

    private val userAllowListDao = mock<UserAllowListDao>()

    private val contentBlocking = mock<ContentBlocking>()
    private val unprotectedTemporary = mock<UnprotectedTemporary>()

    private val pixel = mock<Pixel>()
    private val mockRemoteFeatures = FakePrivacyDashboardRemoteFeatureFactory.create()

    private val testee: PrivacyDashboardHybridViewModel by lazy {
        PrivacyDashboardHybridViewModel(
            userAllowListDao = userAllowListDao,
            pixel = pixel,
            dispatcher = coroutineRule.testDispatcherProvider,
            siteViewStateMapper = AppSiteViewStateMapper(PublicKeyInfoMapper(androidQAppBuildConfig)),
            requestDataViewStateMapper = AppSiteRequestDataViewStateMapper(),
            protectionStatusViewStateMapper = AppProtectionStatusViewStateMapper(contentBlocking, unprotectedTemporary),
            privacyDashboardPayloadAdapter = mock(),
            autoconsentStatusViewStateMapper = CookiePromptManagementStatusViewStateMapper(),
            privacyDashboardRemoteFeature = mockRemoteFeatures,
        )
    }

    @Test
    fun whenUserClicksOnReportBrokenSiteThenCommandEmitted() = runTest {
        testee.onReportBrokenSiteSelected()

        testee.commands().test {
            val command = awaitItem()
            Assert.assertTrue(command is LaunchReportBrokenSite)
        }
    }

    @Test
    fun whenSiteChangesThenViewStateUpdates() = runTest {
        testee.onSiteChanged(site())

        testee.viewState.test {
            val viewState = awaitItem()
            assertNotNull(viewState)
            assertEquals("https://example.com", viewState!!.siteViewState.url)
        }
    }

    @Test
    fun whenOnPrivacyProtectionClickedThenUpdateViewState() = runTest {
        testee.onSiteChanged(site(siteAllowed = false))
        testee.onPrivacyProtectionsClicked(enabled = false)

        testee.viewState.test {
            awaitItem()
            val viewState = awaitItem()
            assertTrue(viewState!!.userChangedValues)
            assertFalse(viewState.protectionStatus.allowlisted)
            verify(pixel).fire(PRIVACY_DASHBOARD_ALLOWLIST_ADD)
        }
    }

    @Test
    fun whenOnPrivacyProtectionClickedThenUpdateViewStateWithParam() = runTest {
        mockRemoteFeatures.highlightedProtectionsToggle().setEnabled(Toggle.State(enable = true))

        testee.onSiteChanged(site(siteAllowed = false))
        testee.onPrivacyProtectionsClicked(enabled = false)

        testee.viewState.test {
            awaitItem()
            val viewState = awaitItem()
            assertTrue(viewState!!.userChangedValues)
            assertFalse(viewState.protectionStatus.allowlisted)
            verify(pixel).fire(PRIVACY_DASHBOARD_ALLOWLIST_ADD, mapOf("dashboard_highlighted_toggle" to true.toString()))
        }
    }

    @Test
    fun whenOnPrivacyProtectionClickedThenValueStoredInStore() = runTest {
        val site = site(siteAllowed = false)
        testee.onSiteChanged(site)
        testee.onPrivacyProtectionsClicked(enabled = false)

        verify(userAllowListDao).insert(site.domain!!)
    }

    private fun site(
        url: String = "https://example.com",
        siteAllowed: Boolean = false,
    ): Site {
        val site: Site = mock()
        whenever(site.uri).thenReturn(url.toUri())
        whenever(site.url).thenReturn(url)
        whenever(site.userAllowList).thenReturn(siteAllowed)
        return site
    }
}
