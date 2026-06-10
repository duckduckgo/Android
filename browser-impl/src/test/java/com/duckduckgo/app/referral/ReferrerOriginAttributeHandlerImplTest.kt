package com.duckduckgo.app.referral

import com.duckduckgo.app.referral.ReferrerOriginAttributeHandlerImpl.Companion.DEFAULT_ATTRIBUTION_FOR_PLAY_STORE_INSTALLS
import com.duckduckgo.app.referral.ReferrerOriginAttributeHandlerImpl.Companion.ORIGIN_ATTRIBUTE_KEY
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.verifiedinstallation.installsource.VerificationCheckPlayStoreInstall
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.*

class ReferrerOriginAttributeHandlerImplTest {

    private val pixel: Pixel = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private val appReferrerDataStore: AppReferrerDataStore = mock()
    private val playStoreInstallChecker: VerificationCheckPlayStoreInstall = mock()
    private val captor = argumentCaptor<Map<String, String>>()

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Before
    fun setup() {
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale.US)
        whenever(playStoreInstallChecker.installedFromPlayStore()).thenReturn(true)
    }

    private val testee = ReferrerOriginAttributeHandlerImpl(
        appReferrerDataStore = appReferrerDataStore,
        playStoreInstallChecker = playStoreInstallChecker,
    )

    @Test
    fun whenEmptyReferrerListAndInstalledFromPlayStoreThenOriginSetToDefault() {
        whenever(playStoreInstallChecker.installedFromPlayStore()).thenReturn(true)
        testee.process(emptyList())
        verifyOriginAttributeProcessed(DEFAULT_ATTRIBUTION_FOR_PLAY_STORE_INSTALLS)
    }

    @Test
    fun whenEmptyReferrerListAndNotInstalledFromPlayStoreThenOriginUnset() {
        whenever(playStoreInstallChecker.installedFromPlayStore()).thenReturn(false)
        testee.process(emptyList())
        verifyOriginAttributeProcessed(null)
    }

    @Test
    fun whenReferrerListNotEmptyButDoesNotContainKeyAndInstalledFromPlayStoreThenOriginSetToDefault() {
        whenever(playStoreInstallChecker.installedFromPlayStore()).thenReturn(true)
        testee.process(listOf("unknown_key=unknown_value"))
        verifyOriginAttributeProcessed(DEFAULT_ATTRIBUTION_FOR_PLAY_STORE_INSTALLS)
    }

    @Test
    fun whenReferrerListNotEmptyButDoesNotContainKeyAndNotInstalledFromPlayStoreThenOriginUnset() {
        whenever(playStoreInstallChecker.installedFromPlayStore()).thenReturn(false)
        testee.process(listOf("unknown_key=unknown_value"))
        verifyOriginAttributeProcessed(null)
    }

    @Test
    fun whenReferrerListContainsExpectedKeyThenOriginAttributeProcessed() {
        val campaignName = "campaign_foo_bar"
        testee.process(listOf("$ORIGIN_ATTRIBUTE_KEY=$campaignName"))
        verifyOriginAttributeProcessed(campaignName)
    }

    @Test
    fun whenReferrerListContainsExpectedKeyButNotAValueAndInstalledFromPlayStoreThenOriginSetToDefault() {
        whenever(playStoreInstallChecker.installedFromPlayStore()).thenReturn(true)
        testee.process(listOf(ORIGIN_ATTRIBUTE_KEY))
        verifyOriginAttributeProcessed(DEFAULT_ATTRIBUTION_FOR_PLAY_STORE_INSTALLS)
    }

    @Test
    fun whenReferrerListContainsExpectedKeyButNotAValueAndNotInstalledFromPlayStoreThenOriginUnset() {
        whenever(playStoreInstallChecker.installedFromPlayStore()).thenReturn(false)
        testee.process(listOf(ORIGIN_ATTRIBUTE_KEY))
        verifyOriginAttributeProcessed(null)
    }

    private fun verifyOriginAttributeProcessed(campaign: String?) {
        verifyOriginAttributePersisted(campaign)
    }

    private fun verifyOriginAttributePersisted(campaign: String?) {
        verify(appReferrerDataStore).utmOriginAttributeCampaign = campaign
    }
}
