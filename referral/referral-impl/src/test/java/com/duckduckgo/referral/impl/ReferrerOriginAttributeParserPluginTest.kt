/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.referral.impl

import com.duckduckgo.referral.impl.ReferrerOriginAttributeParserPlugin.Companion.DEFAULT_ATTRIBUTION_FOR_PLAY_STORE_INSTALLS
import com.duckduckgo.referral.impl.ReferrerOriginAttributeParserPlugin.Companion.ORIGIN_ATTRIBUTE_KEY
import com.duckduckgo.verifiedinstallation.installsource.VerificationCheckPlayStoreInstall
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ReferrerOriginAttributeParserPluginTest {

    private val appReferrerDataStore: AppReferrerDataStore = mock()
    private val playStoreInstallChecker: VerificationCheckPlayStoreInstall = mock()

    @Before
    fun setup() {
        whenever(playStoreInstallChecker.installedFromPlayStore()).thenReturn(true)
    }

    private val testee = ReferrerOriginAttributeParserPlugin(
        appReferrerDataStore = appReferrerDataStore,
        playStoreInstallChecker = playStoreInstallChecker,
    )

    @Test
    fun `when no params and installed from Play Store then origin set to default`() {
        whenever(playStoreInstallChecker.installedFromPlayStore()).thenReturn(true)
        testee.process(emptyMap())
        verifyOriginAttributeProcessed(DEFAULT_ATTRIBUTION_FOR_PLAY_STORE_INSTALLS)
    }

    @Test
    fun `when no params and not installed from Play Store then origin unset`() {
        whenever(playStoreInstallChecker.installedFromPlayStore()).thenReturn(false)
        testee.process(emptyMap())
        verifyOriginAttributeProcessed(null)
    }

    @Test
    fun `when params do not contain origin key and installed from Play Store then origin set to default`() {
        whenever(playStoreInstallChecker.installedFromPlayStore()).thenReturn(true)
        testee.process(mapOf("unknown_key" to "unknown_value"))
        verifyOriginAttributeProcessed(DEFAULT_ATTRIBUTION_FOR_PLAY_STORE_INSTALLS)
    }

    @Test
    fun `when params do not contain origin key and not installed from Play Store then origin unset`() {
        whenever(playStoreInstallChecker.installedFromPlayStore()).thenReturn(false)
        testee.process(mapOf("unknown_key" to "unknown_value"))
        verifyOriginAttributeProcessed(null)
    }

    @Test
    fun `when params contain origin key then origin attribute processed`() {
        val campaignName = "campaign_foo_bar"
        testee.process(mapOf(ORIGIN_ATTRIBUTE_KEY to campaignName))
        verifyOriginAttributeProcessed(campaignName)
    }

    @Test
    fun `when params contain origin key with empty value then empty value persisted`() {
        // an explicit `origin=` (present but empty) is persisted as-is, not replaced by the default
        testee.process(mapOf(ORIGIN_ATTRIBUTE_KEY to ""))
        verifyOriginAttributeProcessed("")
    }

    private fun verifyOriginAttributeProcessed(campaign: String?) {
        verify(appReferrerDataStore).utmOriginAttributeCampaign = campaign
    }
}
