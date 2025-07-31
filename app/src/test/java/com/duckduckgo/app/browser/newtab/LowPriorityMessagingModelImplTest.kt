import android.content.Context
import com.duckduckgo.app.browser.defaultbrowsing.prompts.DefaultBrowserPromptsFeatureToggles
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore
import com.duckduckgo.app.browser.newtab.LowPriorityMessage
import com.duckduckgo.app.browser.newtab.LowPriorityMessagingModelImpl
import com.duckduckgo.app.browser.newtab.NewTabLegacyPageViewModel.Command.LaunchDefaultBrowser
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LowPriorityMessagingModelImplTest {

    private val mockFeatureToggles: DefaultBrowserPromptsFeatureToggles = mock()

    private val mockAdditionalPromptsToggle: Toggle = mock()

    private val mockSelfToggle: Toggle = mock()

    private val mockDefaultBrowserPromptsDataStore: DefaultBrowserPromptsDataStore = mock()

    private val mockPixel: Pixel = mock()

    private val mockContext: Context = mock()

    private lateinit var testee: LowPriorityMessagingModelImpl

    @Before
    fun setup() {
        whenever(mockFeatureToggles.self()).thenReturn(mockSelfToggle)
        whenever(mockFeatureToggles.self().isEnabled()).thenReturn(true)
        whenever(mockFeatureToggles.defaultBrowserPrompts25()).thenReturn(mockAdditionalPromptsToggle)
        whenever(mockFeatureToggles.defaultBrowserPrompts25().isEnabled()).thenReturn(true)

        testee = LowPriorityMessagingModelImpl(
            defaultBrowserPromptsFeatureToggles = mockFeatureToggles,
            defaultBrowserPromptsDataStore = mockDefaultBrowserPromptsDataStore,
            pixel = mockPixel,
            context = mockContext,
        )
    }

    @Test
    fun `getMessage returns null when flag disabled`() = runTest {
        whenever(mockFeatureToggles.self()).thenReturn(mockSelfToggle)
        whenever(mockFeatureToggles.self().isEnabled()).thenReturn(false)
        whenever(mockFeatureToggles.defaultBrowserPrompts25()).thenReturn(mockAdditionalPromptsToggle)
        whenever(mockFeatureToggles.defaultBrowserPrompts25().isEnabled()).thenReturn(false)
        whenever(mockDefaultBrowserPromptsDataStore.showSetAsDefaultMessage).thenReturn(flowOf(true))

        val message = testee.getMessage()

        assertEquals(null, message)
    }

    @Test
    fun `getMessage returns DefaultBrowserMessage when showSetAsDefaultMessage is true`() = runTest {
        whenever(mockDefaultBrowserPromptsDataStore.showSetAsDefaultMessage).thenReturn(flowOf(true))
        whenever(mockContext.getString(any())) doReturn "Test String"

        val message = testee.getMessage()

        assertEquals(true, message is LowPriorityMessage.DefaultBrowserMessage)
    }

    @Test
    fun `getMessage returns null when showSetAsDefaultMessage is false`() = runTest {
        whenever(mockDefaultBrowserPromptsDataStore.showSetAsDefaultMessage).thenReturn(flowOf(false))

        val message = testee.getMessage()

        assertEquals(null, message)
    }

    @Test
    fun `onMessageShown fires impression pixel`() = runTest {
        whenever(mockDefaultBrowserPromptsDataStore.showSetAsDefaultMessage).thenReturn(flowOf(true))
        whenever(mockContext.getString(any())).thenReturn("Test String")
        testee.getMessage()

        testee.onMessageShown()

        verify(mockPixel).fire(AppPixelName.SET_AS_DEFAULT_MESSAGE_IMPRESSION)
    }

    @Test
    fun `getPrimaryButtonCommand returns the correct command`() = runTest {
        whenever(mockDefaultBrowserPromptsDataStore.showSetAsDefaultMessage).thenReturn(flowOf(true))
        whenever(mockContext.getString(any())).thenReturn("Test String")
        testee.getMessage()

        val result = testee.getPrimaryButtonCommand()

        assertEquals(LaunchDefaultBrowser, result)
    }
}
