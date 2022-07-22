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
import com.duckduckgo.app.privacy.db.UserWhitelistDao
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.privacy.config.api.ContentBlocking
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command.LaunchReportBrokenSite
import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
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

    private val userWhitelistDao = mock<UserWhitelistDao>()

    private val contentBlocking = mock<ContentBlocking>()

    private val pixel = mock<Pixel>()

    val testee = PrivacyDashboardHybridViewModel(
        userWhitelistDao = userWhitelistDao,
        contentBlocking = contentBlocking,
        pixel = pixel,
        dispatcher = coroutineRule.testDispatcherProvider,
        appCoroutineScope = TestScope(),
        siteProtectionsViewStateMapper = AppSiteProtectionsViewStateMapper(PublicKeyInfoMapper(androidQAppBuildConfig))
    )

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
            assertEquals("https://example.com", viewState!!.siteProtectionsViewState.site.url)
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
            assertFalse(viewState.userSettingsViewState.privacyProtectionEnabled)
        }
    }

    @Test
    fun whenOnPrivacyProtectionClickedThenValueStoredInStore() = runTest {
        val site = site(siteAllowed = false)
        testee.onSiteChanged(site)
        testee.onPrivacyProtectionsClicked(enabled = false)

        verify(userWhitelistDao).insert(site.domain!!)
    }

    private fun site(
        url: String = "https://example.com",
        siteAllowed: Boolean = false
    ): Site {
        val site: Site = mock()
        whenever(site.uri).thenReturn(url.toUri())
        whenever(site.url).thenReturn(url)
        whenever(site.userAllowList).thenReturn(siteAllowed)
        return site
    }

}
