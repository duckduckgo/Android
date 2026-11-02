@file:SuppressLint("NoImplImportsInAppModule")

package com.duckduckgo.app.browser.nativeinput

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LiveData
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.Omnibar
import com.duckduckgo.app.browser.omnibar.QueryUrlPredictor
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.DuckChatInputModeState
import com.duckduckgo.duckchat.api.NativeInputEventListener
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.NativeInputWidget
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.voice.api.VoiceSearchAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class RealNativeInputManagerHideDuringLayoutTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val duckChat: DuckChat = mock()
    private val duckAiFeatureState: DuckAiFeatureState = mock()
    private val duckChatInputModeState: DuckChatInputModeState = mock()
    private val omnibar: Omnibar = mock()
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val content: FrameLayout = activity.findViewById(android.R.id.content)
    private val lifecycleOwner = FakeLifecycleOwner()
    private var hideCalledInLayout = false

    private val testee = RealNativeInputManager(
        duckChat,
        mock<NativeInputAnimator>(),
        mock<VoiceSearchAvailability>().also { whenever(it.observeVoiceSearchAvailability()).thenReturn(MutableStateFlow(false)) },
        mock<GlobalActivityStarter>(),
        mock<QueryUrlPredictor>(),
        duckAiFeatureState,
        duckChatInputModeState,
        mock<Pixel>(),
        FakeFeatureToggleFactory.create(NativeInputStateBugKillSwitch::class.java),
        FakeFeatureToggleFactory.create(NativeInputUrlClearingFeature::class.java).also { it.self().setRawStoredState(State(enable = true)) },
        mock<NativeInputEventListener>(),
        EdgeToEdgeHandler(),
        mock<DuckAiChatStore>(),
    )

    private val rootView = object : FrameLayout(activity) {
        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            super.onLayout(changed, l, t, r, b)
            if (!hideCalledInLayout && content.childCount > 1) {
                hideCalledInLayout = isInLayout
                testee.hideNativeInput(animate = false, isNavigation = true)
            }
        }
    }

    @Test
    fun whenInputIsHiddenDuringLayoutThenContentFrameDoesNotCrash() {
        whenever(duckChatInputModeState.inputModeCapability).thenReturn(MutableStateFlow(NativeInputState.InputMode.SEARCH_AND_DUCK_AI))
        whenever(duckChat.observeNativeInputNavBarEnabled()).thenReturn(MutableStateFlow(false))
        whenever(duckChat.observeNativeChatInputEnabled()).thenReturn(MutableStateFlow(true))
        whenever(duckAiFeatureState.showVoiceSearchToggle).thenReturn(MutableStateFlow(false))
        whenever(duckAiFeatureState.showVoiceChatEntry).thenReturn(MutableStateFlow(false))
        whenever(omnibar.viewMode).thenReturn(Omnibar.ViewMode.NewTab)
        whenever(omnibar.getText()).thenReturn("")
        activity.setContentView(rootView)
        rootView.addView(FrameLayout(activity).apply { id = R.id.includeNewBrowserTab })
        testee.init(omnibar, rootView, lifecycleOwner)

        val widgetView = FrameLayout(activity).apply {
            id = R.id.inputModeRoot
            addView(TestWidget(activity).apply { id = com.duckduckgo.duckchat.impl.R.id.inputModeWidget })
        }
        val layoutInflater: LayoutInflater = mock()
        whenever(layoutInflater.inflate(any<Int>(), any<ViewGroup>(), any<Boolean>())).thenReturn(widgetView)
        testee.showNativeInput(
            tabId = "tab",
            layoutInflater = layoutInflater,
            lifecycleOwner = lifecycleOwner,
            tabs = mock<LiveData<List<TabEntity>>>(),
            currentTabUrl = emptyFlow(),
            callbacks = NativeInputCallbacks(
                onSearchTextChanged = {},
                onSearchSubmitted = {},
                onDuckAiChatSubmitted = { _, _, _, _, _, _, _ -> },
                onChatSuggestionSelected = {},
                onDuckAiQuerySubmitted = { _, _, _ -> },
                onClearAutocomplete = {},
                onStopTapped = {},
            ),
            forceImageGeneration = false,
        )
        assertEquals("floating submit container attached to content frame", 2, content.childCount)

        content.requestLayout()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(hideCalledInLayout)
        assertEquals(1, content.childCount)
    }

    private class FakeLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    private class TestWidget(context: Context) : View(context), NativeInputWidget by mock()
}
