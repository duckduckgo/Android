package com.duckduckgo.app.referrer

import com.duckduckgo.app.pixels.AppPixelName.REFERRAL_INSTALL_UTM_CAMPAIGN
import com.duckduckgo.app.referral.AppReferrerDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.referral.AppReferrerInstallPixelSender
import com.duckduckgo.referral.AppReferrerInstallPixelSender.Companion.PIXEL_PARAM_LOCALE
import com.duckduckgo.referral.AppReferrerInstallPixelSender.Companion.PIXEL_PARAM_ORIGIN
import com.duckduckgo.referral.AppReferrerInstallPixelSender.Companion.PIXEL_PARAM_RETURNING_USER
import com.duckduckgo.verifiedinstallation.installsource.VerificationCheckPlayStoreInstall
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.util.*

class AppReferrerInstallPixelSenderTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val pixel: Pixel = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private val appReferrerDataStore: AppReferrerDataStore = mock()
    private val playStoreInstallChecker: VerificationCheckPlayStoreInstall = mock()
    private val captor = argumentCaptor<Map<String, String>>()

    @Before
    fun setup() = runTest {
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale.US)
        whenever(playStoreInstallChecker.installedFromPlayStore()).thenReturn(true)
        configureAsNewUser()
    }

    private val testee = AppReferrerInstallPixelSender(
        appReferrerDataStore = appReferrerDataStore,
        pixel = pixel,
        appCoroutineScope = coroutineTestRule.testScope,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        appBuildConfig = appBuildConfig,
    )

    @Test
    fun whenAtbNotInitializedYetThenPixelNotSent() = runTest {
        verifyPixelNotFired()
    }

    @Test
    fun whenBothUserCheckAndReferrerExtractionFinishedForReturningUserThenPixelSent() = runTest {
        configureAsReturningUser()
        configureReferrerCampaign("foo")
        testee.onAppAtbInitialized()
        verifyCorrectPixelSent("foo", returningUser = true)
    }

    @Test
    fun whenBothUserCheckAndReferrerExtractionFinishedForNewUserThenPixelSent() = runTest {
        configureAsNewUser()
        configureReferrerCampaign("foo")
        testee.onAppAtbInitialized()
        verifyCorrectPixelSent("foo", returningUser = false)
    }

    @Test
    fun whenPixelAlreadySentThenPixelNotSentAgain() = runTest {
        configureReferrerCampaign("foo")
        testee.onAppAtbInitialized()
        verifyCorrectPixelSent("foo")

        testee.onAppAtbInitialized()
        verifyNoMoreInteractions(pixel)
    }

    private suspend fun configureAsReturningUser() {
        whenever(appBuildConfig.isAppReinstall()).thenReturn(true)
    }

    private suspend fun configureAsNewUser() {
        whenever(appBuildConfig.isAppReinstall()).thenReturn(false)
    }

    private fun configureReferrerCampaign(campaign: String?) {
        whenever(appReferrerDataStore.utmOriginAttributeCampaign).thenReturn(campaign)
    }

    private fun verifyCorrectPixelSent(
        campaign: String?,
        returningUser: Boolean? = null,
    ) {
        verifyPixelFired()
        verifyPixelParamsAttached(campaign, returningUser)
    }

    private fun verifyPixelNotFired() {
        verifyNoInteractions(pixel)
    }

    private fun verifyPixelFired() {
        verify(pixel).fire(
            eq(REFERRAL_INSTALL_UTM_CAMPAIGN),
            parameters = captor.capture(),
            encodedParameters = any(),
            type = eq(Unique()),
        )
    }

    private fun verifyPixelParamsAttached(
        campaign: String?,
        returningUser: Boolean?,
    ) {
        val params = captor.firstValue

        val expectedOriginParamAdded = campaign != null
        assertEquals(expectedOriginParamAdded, params.containsKey(PIXEL_PARAM_ORIGIN))

        assertEquals("en-US", params[PIXEL_PARAM_LOCALE])
        assertEquals(campaign, params[PIXEL_PARAM_ORIGIN])

        if (returningUser != null) {
            assertEquals(returningUser.toString(), params[PIXEL_PARAM_RETURNING_USER])
        }
    }
}
