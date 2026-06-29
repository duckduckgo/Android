package com.duckduckgo.app.browser.newtab

import android.content.Context
import com.duckduckgo.app.browser.defaultbrowsing.prompts.AdditionalDefaultBrowserPrompts
import com.duckduckgo.app.browser.newtab.NewTabPageViewModel.Command.LaunchDefaultBrowser
import com.duckduckgo.app.browser.newtab.NewTabPageViewModel.Command.LaunchTabSwitcherForFirePromo
import com.duckduckgo.app.fire.promo.FireTabsPromos
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LowPriorityMessagingModelImplTest {

    private val mockAdditionalDefaultBrowserPrompts: AdditionalDefaultBrowserPrompts = mock()

    private val mockPixel: Pixel = mock()

    private val mockContext: Context = mock()

    private val mockFireTabsPromos: FireTabsPromos = mock()

    private lateinit var testee: LowPriorityMessagingModelImpl

    @Before
    fun setup() {
        testee = LowPriorityMessagingModelImpl(
            additionalDefaultBrowserPrompts = mockAdditionalDefaultBrowserPrompts,
            pixel = mockPixel,
            context = mockContext,
            fireTabsPromos = mockFireTabsPromos,
        )
    }

    @Test
    fun `getMessage returns DefaultBrowserMessage when showSetAsDefaultMessage is true`() = runTest {
        whenever(mockAdditionalDefaultBrowserPrompts.showSetAsDefaultMessage).thenReturn(MutableStateFlow(true))
        whenever(mockContext.getString(any())) doReturn "Test String"

        val message = testee.getMessage()

        assertEquals(true, message is LowPriorityMessage.DefaultBrowserMessage)
    }

    @Test
    fun `getMessage returns null when showSetAsDefaultMessage is false and no fire promo`() = runTest {
        whenever(mockAdditionalDefaultBrowserPrompts.showSetAsDefaultMessage).thenReturn(MutableStateFlow(false))
        whenever(mockFireTabsPromos.canShowNtpPromo()).thenReturn(false)

        val message = testee.getMessage()

        assertEquals(null, message)
    }

    @Test
    fun `onMessageShown fires impression pixel`() = runTest {
        whenever(mockAdditionalDefaultBrowserPrompts.showSetAsDefaultMessage).thenReturn(MutableStateFlow(true))
        whenever(mockContext.getString(any())).thenReturn("Test String")
        testee.getMessage()

        testee.onMessageShown()

        verify(mockPixel).fire(AppPixelName.SET_AS_DEFAULT_MESSAGE_IMPRESSION)
    }

    @Test
    fun `getPrimaryButtonCommand returns the correct command`() = runTest {
        whenever(mockAdditionalDefaultBrowserPrompts.showSetAsDefaultMessage).thenReturn(MutableStateFlow(true))
        whenever(mockContext.getString(any())).thenReturn("Test String")
        testee.getMessage()

        val result = testee.getPrimaryButtonCommand()

        assertEquals(LaunchDefaultBrowser, result)
    }

    @Test
    fun whenFireTabsPromoEligibleThenReturnsFireTabsPromoMessage() = runTest {
        whenever(mockAdditionalDefaultBrowserPrompts.showSetAsDefaultMessage).thenReturn(MutableStateFlow(false))
        whenever(mockFireTabsPromos.canShowNtpPromo()).thenReturn(true)
        whenever(mockContext.getString(any())).thenReturn("Test String")

        val message = testee.getMessage()

        assertTrue(message is LowPriorityMessage.FireTabsPromoMessage)
    }

    @Test
    fun whenCannotShowNtpPromoThenNoFireTabsPromo() = runTest {
        whenever(mockAdditionalDefaultBrowserPrompts.showSetAsDefaultMessage).thenReturn(MutableStateFlow(false))
        whenever(mockFireTabsPromos.canShowNtpPromo()).thenReturn(false)

        assertNull(testee.getMessage())
    }

    @Test
    fun `whenFireTabsPromoPrimaryClickedThenInteractedAndCtaPixel`() = runTest {
        whenever(mockAdditionalDefaultBrowserPrompts.showSetAsDefaultMessage).thenReturn(MutableStateFlow(false))
        whenever(mockFireTabsPromos.canShowNtpPromo()).thenReturn(true)
        whenever(mockContext.getString(any())).thenReturn("Test String")

        val message = testee.getMessage() as LowPriorityMessage.FireTabsPromoMessage
        message.onPrimaryButtonClicked()

        verify(mockFireTabsPromos).onNtpPromoInteracted()
        verify(mockPixel).fire(AppPixelName.FIRE_TABS_PROMO_NTP_CTA)
        assertEquals(LaunchTabSwitcherForFirePromo, message.getPrimaryAction())
    }

    @Test
    fun `whenFireTabsPromoSecondaryClickedThenInteractedAndDismissedPixel`() = runTest {
        whenever(mockAdditionalDefaultBrowserPrompts.showSetAsDefaultMessage).thenReturn(MutableStateFlow(false))
        whenever(mockFireTabsPromos.canShowNtpPromo()).thenReturn(true)
        whenever(mockContext.getString(any())).thenReturn("Test String")

        val message = testee.getMessage() as LowPriorityMessage.FireTabsPromoMessage
        message.onSecondaryButtonClicked()

        verify(mockFireTabsPromos).onNtpPromoInteracted()
        verify(mockPixel).fire(AppPixelName.FIRE_TABS_PROMO_NTP_DISMISSED)
    }

    @Test
    fun `whenFireTabsPromoClosedThenInteractedAndDismissedPixel`() = runTest {
        whenever(mockAdditionalDefaultBrowserPrompts.showSetAsDefaultMessage).thenReturn(MutableStateFlow(false))
        whenever(mockFireTabsPromos.canShowNtpPromo()).thenReturn(true)
        whenever(mockContext.getString(any())).thenReturn("Test String")

        val message = testee.getMessage() as LowPriorityMessage.FireTabsPromoMessage
        message.onCloseButtonClicked()

        verify(mockFireTabsPromos).onNtpPromoInteracted()
        verify(mockPixel).fire(AppPixelName.FIRE_TABS_PROMO_NTP_DISMISSED)
    }

    @Test
    fun `whenFireTabsPromoShownThenShownPixelAndNoInteracted`() = runTest {
        whenever(mockAdditionalDefaultBrowserPrompts.showSetAsDefaultMessage).thenReturn(MutableStateFlow(false))
        whenever(mockFireTabsPromos.canShowNtpPromo()).thenReturn(true)
        whenever(mockContext.getString(any())).thenReturn("Test String")

        val message = testee.getMessage() as LowPriorityMessage.FireTabsPromoMessage
        message.onMessageShown()

        verify(mockPixel).fire(AppPixelName.FIRE_TABS_PROMO_NTP_SHOWN)
        verify(mockFireTabsPromos, never()).onNtpPromoInteracted()
    }
}
