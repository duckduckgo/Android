package com.duckduckgo.app.pixels.campaign

import com.duckduckgo.app.pixels.campaign.params.AdditionalPixelParamsGenerator
import com.duckduckgo.common.test.api.FakeChain
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class CampaignPixelParamsAdditionInterceptorTest {
    @Mock
    private lateinit var additionalPixelParamsDataStore: AdditionalPixelParamsDataStore

    private lateinit var additionalPixelParamsGenerator: AdditionalPixelParamsGenerator
    private lateinit var interceptor: CampaignPixelParamsAdditionInterceptor
    private lateinit var additionalPixelParamsFeature: AdditionalPixelParamsFeature
    private lateinit var fakePluginPoint: FakePluginPoint

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        fakePluginPoint = FakePluginPoint(
            listOf(
                RmfCampaignPixelParamsAdditionPlugin(),
                PproCampaignPixelParamsAdditionPlugin(),
            ),
        )
        additionalPixelParamsFeature = FakeFeatureToggleFactory.create(AdditionalPixelParamsFeature::class.java)
        additionalPixelParamsGenerator = FakeParamsGenerator()
        whenever(additionalPixelParamsDataStore.includedOrigins).thenReturn(
            listOf(
                "valid_test_origin1",
                "valid_test_origin2",
            ),
        )
        interceptor = CampaignPixelParamsAdditionInterceptor(
            fakePluginPoint,
            additionalPixelParamsGenerator,
            additionalPixelParamsFeature,
            additionalPixelParamsDataStore,
        )
    }

    @Test
    fun whenFeatureIsDisabledThenNoChangesInEligiblePixelUrls() {
        additionalPixelParamsFeature.self().setRawStoredState(Toggle.State(enable = false))
        val startUrl = URL_PIXEL_BASE + "m_subscribe_android_phone?origin=valid_test_origin1"
        val resultUrl = interceptor.intercept(FakeChain(startUrl)).request.url

        assertEquals(startUrl, resultUrl.toString())
    }

    @Test
    fun whenFeatureIsEnabledAndPixelIsOriginSubscribeWithValidCampaignThenAppendAdditionalParams() = runTest {
        additionalPixelParamsFeature.self().setRawStoredState(Toggle.State(enable = true))
        val startUrl = URL_PIXEL_BASE + "m_subscribe_android_phone?origin=valid_test_origin1"
        val resultUrl = interceptor.intercept(FakeChain(startUrl)).request.url

        assertEquals("$startUrl&test1=value1&test2=value2&test3=value3&test4=value4", resultUrl.toString())
    }

    @Test
    fun whenFeatureIsEnabledAndPixelIsOriginSubscribeWithInValidCampaignThenNoChangesInPixel() = runTest {
        additionalPixelParamsFeature.self().setRawStoredState(Toggle.State(enable = true))
        val startUrl = URL_PIXEL_BASE + "m_subscribe_android_phone?origin=invalid_origin"
        val resultUrl = interceptor.intercept(FakeChain(startUrl)).request.url

        assertEquals(startUrl, resultUrl.toString())
    }

    @Test
    fun whenFeatureIsEnabledAndPixelIsInvalidThenNoChangesInPixel() = runTest {
        additionalPixelParamsFeature.self().setRawStoredState(Toggle.State(enable = true))
        val startUrl = URL_PIXEL_BASE + "m_subscribe_something_else_android_phone?origin=valid_test_origin1"
        val resultUrl = interceptor.intercept(FakeChain(startUrl)).request.url

        assertEquals(startUrl, resultUrl.toString())
    }

    @Test
    fun whenFeatureIsEnabledAndPixelIsRMFShownWithValidMessageThenAppendAdditionalParamsToPixel() = runTest {
        additionalPixelParamsFeature.self().setRawStoredState(Toggle.State(enable = true))
        val startUrl = URL_PIXEL_BASE + "m_remote_message_shown_android_phone?message=valid_test_origin1"
        val resultUrl = interceptor.intercept(FakeChain(startUrl)).request.url

        assertEquals("$startUrl&test1=value1&test2=value2&test3=value3&test4=value4", resultUrl.toString())
    }

    @Test
    fun whenFeatureIsEnabledAndPixelIsRMFShownWithInvalidMessageThenNoChangesInPixel() = runTest {
        additionalPixelParamsFeature.self().setRawStoredState(Toggle.State(enable = true))
        val startUrl = URL_PIXEL_BASE + "m_remote_message_shown_android_phone?message=invalid"
        val resultUrl = interceptor.intercept(FakeChain(startUrl)).request.url

        assertEquals(startUrl, resultUrl.toString())
    }

    @Test
    fun whenFeatureIsEnabledAndPixelIsRMFDismissedWithValidMessageThenAppendAdditionalParamsToPixel() = runTest {
        additionalPixelParamsFeature.self().setRawStoredState(Toggle.State(enable = true))
        val startUrl = URL_PIXEL_BASE + "m_remote_message_dismissed_android_phone?message=valid_test_origin1"
        val resultUrl = interceptor.intercept(FakeChain(startUrl)).request.url

        assertEquals("$startUrl&test1=value1&test2=value2&test3=value3&test4=value4", resultUrl.toString())
    }

    @Test
    fun whenFeatureIsEnabledAndPixelIsRMFDismissedWithInvalidMessageThenNoChangesInPixel() = runTest {
        additionalPixelParamsFeature.self().setRawStoredState(Toggle.State(enable = true))
        val startUrl = URL_PIXEL_BASE + "m_remote_message_dismissed_android_phone?message=invalid"
        val resultUrl = interceptor.intercept(FakeChain(startUrl)).request.url

        assertEquals(startUrl, resultUrl.toString())
    }

    @Test
    fun whenFeatureIsEnabledAndPixelIsRMFPrimaryClickedWithValidMessageThenAppendAdditionalParamsToPixel() = runTest {
        additionalPixelParamsFeature.self().setRawStoredState(Toggle.State(enable = true))
        val startUrl = URL_PIXEL_BASE + "m_remote_message_primary_action_clicked_android_phone?message=valid_test_origin1"
        val resultUrl = interceptor.intercept(FakeChain(startUrl)).request.url

        assertEquals("$startUrl&test1=value1&test2=value2&test3=value3&test4=value4", resultUrl.toString())
    }

    @Test
    fun whenFeatureIsEnabledAndPixelIsRMFPrimaryClickedWithInvalidMessageThenNoChangesInPixel() = runTest {
        additionalPixelParamsFeature.self().setRawStoredState(Toggle.State(enable = true))
        val startUrl = URL_PIXEL_BASE + "m_remote_message_primary_action_clicked_android_phone?message=invalid"
        val resultUrl = interceptor.intercept(FakeChain(startUrl)).request.url

        assertEquals(startUrl, resultUrl.toString())
    }

    @Test
    fun whenFeatureIsEnabledAndPixelIsRMFPrimaryClickedWithNoParamshenNoChangesInPixel() = runTest {
        additionalPixelParamsFeature.self().setRawStoredState(Toggle.State(enable = true))
        val startUrl = URL_PIXEL_BASE + "m_remote_message_primary_action_clicked_android_phone"
        val resultUrl = interceptor.intercept(FakeChain(startUrl)).request.url

        assertEquals(startUrl, resultUrl.toString())
    }

    private class FakePluginPoint(val plugins: List<CampaignPixelParamsAdditionPlugin>) : PluginPoint<CampaignPixelParamsAdditionPlugin> {
        override fun getPlugins(): Collection<CampaignPixelParamsAdditionPlugin> = plugins
    }

    private class FakeParamsGenerator : AdditionalPixelParamsGenerator {
        override suspend fun generateAdditionalParams(): Map<String, String> {
            return mapOf(
                "test1" to "value1",
                "test2" to "value2",
                "test3" to "value3",
                "test4" to "value4",
            )
        }
    }

    companion object {
        private const val URL_PIXEL_BASE = "https://improving.duckduckgo.com/t/"
    }
}
