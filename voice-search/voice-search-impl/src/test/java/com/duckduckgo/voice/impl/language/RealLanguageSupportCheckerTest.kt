import android.content.Context
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.SpeechRecognizer
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.voice.impl.VoiceSearchAvailabilityConfig
import com.duckduckgo.voice.impl.VoiceSearchAvailabilityConfigProvider
import com.duckduckgo.voice.impl.language.LanguageSupportCheckerDelegate
import com.duckduckgo.voice.impl.language.RealLanguageSupportChecker
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class RealLanguageSupportCheckerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var configProvider: VoiceSearchAvailabilityConfigProvider

    @Mock
    private lateinit var languageSupportCheckerDelegate: LanguageSupportCheckerDelegate

    private lateinit var languageSupportChecker: RealLanguageSupportChecker

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        val config = VoiceSearchAvailabilityConfig("Samsung", 33, "en-US", true)
        whenever(configProvider.get()).thenReturn(config)
    }

    private fun instantiate() {
        languageSupportChecker = RealLanguageSupportChecker(
            context,
            configProvider,
            languageSupportCheckerDelegate,
            TestScope(),
            coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenLanguageInstalledThenLanguageIsSupported() {
        instantiate()

        val callbackCaptor = argumentCaptor<RecognitionSupportCallback>()
        verify(languageSupportCheckerDelegate).checkRecognitionSupport(eq(context), eq("en-US"), callbackCaptor.capture())

        callbackCaptor.firstValue.onSupportResult(
            mock(RecognitionSupport::class.java).apply {
                whenever(this.installedOnDeviceLanguages).thenReturn(listOf("en-US"))
            },
        )
        assertTrue(languageSupportChecker.isLanguageSupported())
    }

    @Test
    fun whenLanguageNotInstalledThenLanguageIsNotSupported() {
        instantiate()

        val callbackCaptor = argumentCaptor<RecognitionSupportCallback>()
        verify(languageSupportCheckerDelegate).checkRecognitionSupport(eq(context), eq("en-US"), callbackCaptor.capture())

        callbackCaptor.firstValue.onSupportResult(
            mock(RecognitionSupport::class.java).apply {
                whenever(this.installedOnDeviceLanguages).thenReturn(listOf("de-ZA"))
            },
        )
        assertFalse(languageSupportChecker.isLanguageSupported())
    }

    @Test
    fun whenSpeechRecognizerErrorThenLanguageIsNotSupported() {
        instantiate()

        val callbackCaptor = argumentCaptor<RecognitionSupportCallback>()
        verify(languageSupportCheckerDelegate).checkRecognitionSupport(eq(context), eq("en-US"), callbackCaptor.capture())

        callbackCaptor.firstValue.onError(SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT)

        assertFalse(languageSupportChecker.isLanguageSupported())
    }

    @Test
    fun whenSdkLowerThanTiramisuThenLanguageIsNotSupported() {
        val config = VoiceSearchAvailabilityConfig("Samsung", 32, "en-US", true)
        whenever(configProvider.get()).thenReturn(config)

        instantiate()

        verifyNoInteractions(languageSupportCheckerDelegate)

        assertFalse(languageSupportChecker.isLanguageSupported())
    }
}
