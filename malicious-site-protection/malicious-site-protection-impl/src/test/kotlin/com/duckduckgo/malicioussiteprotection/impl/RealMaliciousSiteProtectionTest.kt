import android.net.Uri
import android.webkit.WebResourceRequest
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.malicioussiteprotection.impl.MaliciousSiteProtectionFeature
import com.duckduckgo.malicioussiteprotection.impl.RealMaliciousSiteProtection
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealMaliciousSiteProtectionTest {

    private val maliciousSiteProtectionFeature = FakeFeatureToggleFactory.create(MaliciousSiteProtectionFeature::class.java)

    private lateinit var testee: RealMaliciousSiteProtection

    @get:Rule
    var coroutineTestRule = CoroutineTestRule()

    @Before
    fun setup() {
        testee = RealMaliciousSiteProtection(
            coroutineTestRule.testDispatcherProvider,
            maliciousSiteProtectionFeature,
            true,
            coroutineTestRule.testScope,
        )
    }

    @Test
    fun `shouldOverrideUrlLoading returns false when feature is disabled`() = runTest {
        val url = mock(Uri::class.java)
        whenever(url.toString()).thenReturn("http://example.com")
        maliciousSiteProtectionFeature.self().setRawStoredState(Toggle.State(false))
        testee.onPrivacyConfigDownloaded()

        val result = testee.shouldOverrideUrlLoading(url, null, true, false) {}
        assertFalse(result)
    }

    @Test
    fun `shouldOverrideUrlLoading returns false when for main frame`() = runTest {
        val url = mock(Uri::class.java)
        whenever(url.toString()).thenReturn("http://malicious.com")
        maliciousSiteProtectionFeature.self().setRawStoredState(Toggle.State(true))
        testee.onPrivacyConfigDownloaded()

        val result = testee.shouldOverrideUrlLoading(url, "http://malicious.com".toUri(), true, false) {}
        assertFalse(result)
    }

    @Test
    fun `shouldOverrideUrlLoading returns false when url is already processed`() = runTest {
        val url = mock(Uri::class.java)
        whenever(url.toString()).thenReturn("http://example.com")
        maliciousSiteProtectionFeature.self().setRawStoredState(Toggle.State(true))
        testee.processedUrls.add("http://example.com")
        testee.onPrivacyConfigDownloaded()

        val result = testee.shouldOverrideUrlLoading(url, "http://example.com".toUri(), true, false) {}
        assertFalse(result)
    }

    @Test
    fun `shouldOverrideUrlLoading returns false when for iframe`() = runTest {
        val url = mock(Uri::class.java)
        whenever(url.toString()).thenReturn("http://malicious.com")
        maliciousSiteProtectionFeature.self().setRawStoredState(Toggle.State(true))
        testee.onPrivacyConfigDownloaded()

        val result = testee.shouldOverrideUrlLoading(url, "http://example.com".toUri(), false, false) {}
        assertFalse(result)
    }

    @Test
    fun `shouldInterceptRequest returns null when feature is disabled`() = runTest {
        val request = mock(WebResourceRequest::class.java)
        whenever(request.url).thenReturn("http://example.com".toUri())
        maliciousSiteProtectionFeature.self().setRawStoredState(Toggle.State(false))
        testee.onPrivacyConfigDownloaded()

        val result = testee.shouldIntercept(request, null) {}
        assertNull(result)
    }

    @Test
    fun `shouldInterceptRequest returns null when feature is enabled`() = runTest {
        val request = mock(WebResourceRequest::class.java)
        whenever(request.url).thenReturn("http://malicious.com".toUri())
        maliciousSiteProtectionFeature.self().setRawStoredState(Toggle.State(true))
        testee.onPrivacyConfigDownloaded()

        val result = testee.shouldIntercept(request, "http://malicious.com".toUri()) {}
        assertNull(result)
    }

    @Test
    fun `onPageLoadStarted clears processedUrls`() = runTest {
        testee.processedUrls.add("http://example.com")
        testee.onPageLoadStarted()
        assertTrue(testee.processedUrls.isEmpty())
    }
}
