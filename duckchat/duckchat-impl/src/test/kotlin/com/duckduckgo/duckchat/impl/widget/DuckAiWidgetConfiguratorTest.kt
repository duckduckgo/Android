/*
 * Copyright (c) 2026 DuckDuckGo
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
package com.duckduckgo.duckchat.impl.widget

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.tabs.DuckChatLaunch
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.store.impl.DuckAiChat
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.mockingDetails
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class DuckAiWidgetConfiguratorTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val duckChat: DuckChatInternal = mock()
    private val duckChatFeature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java)
    private val chatStore: DuckAiChatStore = mock()
    private val widgetPreferences: DuckAiWidgetPreferences = mock<DuckAiWidgetPreferences> {
        onBlocking { showRecentChats(any()) } doReturn true
    }
    private val remoteViews: RemoteViews = mock()
    private val stringContext: Context = mock<Context>().also { whenever(it.getString(any())).thenReturn("Untitled chat") }
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private val testee = DuckAiWidgetConfigurator(
        duckChat = duckChat,
        duckChatFeature = duckChatFeature,
        chats = DuckAiChatsWidgetChats(stringContext, chatStore),
        widgetPreferences = widgetPreferences,
        intents = fakeDuckAiWidgetIntents(),
        dispatcherProvider = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenPromptBarTappedThenLaunchesNewChatFromWidget() = runTest {
        stubDuckAi(enabled = true, historyAvailable = false)

        val launched = populateAndCaptureClickIntent(R.id.promptBarContainer)

        assertLaunch(launched, url = null)
    }

    @Test
    fun whenVoiceActionTappedThenLaunchesVoiceMode() = runTest {
        stubDuckAi(enabled = true, historyAvailable = false)

        val launched = populateAndCaptureClickIntent(R.id.actionVoice)

        assertLaunch(launched, url = null, launch = DuckChatLaunch.Voice::class)
    }

    @Test
    fun whenCreateImageTappedThenLaunchesImageGeneration() = runTest {
        stubDuckAi(enabled = true, historyAvailable = false)

        val launched = populateAndCaptureClickIntent(R.id.actionCreateImage)

        assertLaunch(launched, url = null, launch = DuckChatLaunch.ImageGeneration::class)
    }

    @Test
    fun whenTakePhotoTappedThenLaunchesCameraCapture() = runTest {
        stubDuckAi(enabled = true, historyAvailable = false)

        val launched = populateAndCaptureClickIntent(R.id.actionTakePhoto)

        assertLaunch(launched, url = null, launch = DuckChatLaunch.CameraCapture::class)
    }

    @Test
    fun whenChatHistoryAvailableThenViewAllShownAndOpensChatHistory() = runTest {
        stubDuckAi(enabled = true, historyAvailable = true)

        val launched = populateAndCaptureClickIntent(R.id.chatsViewAll)

        assertLaunch(launched, launch = DuckChatLaunch.ChatHistory::class)
        verify(remoteViews).setViewVisibility(R.id.chatsViewAllContainer, View.VISIBLE)
    }

    @Test
    fun whenShowRecentChatsOnThenChatsSectionVisible() = runTest {
        stubDuckAi(enabled = true, historyAvailable = true)

        testee.populateRemoteViews(activity, APP_WIDGET_ID, remoteViews)

        verify(remoteViews).setViewVisibility(R.id.chatsSection, View.VISIBLE)
    }

    @Test
    fun whenShowRecentChatsOffThenChatsSectionHiddenAndChatsNotLoadedOrWired() = runTest {
        stubDuckAi(enabled = true, historyAvailable = true)
        whenever(widgetPreferences.showRecentChats(APP_WIDGET_ID)).thenReturn(false)

        val launched = populateAndCaptureClickIntent(R.id.promptBarContainer)

        assertLaunch(launched, url = null)
        verify(remoteViews).setViewVisibility(R.id.chatsSection, View.GONE)
        verify(remoteViews, never()).setOnClickPendingIntent(eq(R.id.chatsViewAll), any())
        verify(remoteViews, never()).setRemoteAdapter(eq(R.id.chatsList), any<RemoteViews.RemoteCollectionItems>())
        verify(chatStore, never()).getChats()
    }

    @Test
    fun whenChatHistoryUnavailableThenViewAllHiddenNotWiredAndUnavailableMessageShown() = runTest {
        stubDuckAi(enabled = true, historyAvailable = false)

        testee.populateRemoteViews(activity, APP_WIDGET_ID, remoteViews)

        verify(remoteViews).setViewVisibility(R.id.chatsViewAllContainer, View.GONE)
        verify(remoteViews, never()).setOnClickPendingIntent(eq(R.id.chatsViewAll), any())
        verify(remoteViews).setViewVisibility(R.id.chatsList, View.GONE)
        verify(remoteViews).setViewVisibility(R.id.chatsMessageUnavailable, View.VISIBLE)
        verify(remoteViews).setViewVisibility(R.id.chatsMessageEmpty, View.GONE)
        verify(chatStore, never()).getChats()
    }

    @Test
    @Config(sdk = [34])
    fun whenChatsAvailableThenListShownAndTemplateLaunchesDuckAi() = runTest {
        stubDuckAi(enabled = true, historyAvailable = true)
        whenever(chatStore.getChats()).thenReturn(listOf(chat("a"), chat("b", pinned = true)))
        whenever(duckChat.buildChatUrl(any())).thenAnswer { "https://duck.ai/?chatID=${it.arguments[0]}" }

        testee.populateRemoteViews(activity, APP_WIDGET_ID, remoteViews)

        verify(remoteViews).setViewVisibility(R.id.chatsList, View.VISIBLE)
        verify(remoteViews).setViewVisibility(R.id.chatsMessageEmpty, View.GONE)
        verify(remoteViews).setViewVisibility(R.id.chatsMessageUnavailable, View.GONE)
        val captor = argumentCaptor<PendingIntent>()
        verify(remoteViews).setPendingIntentTemplate(eq(R.id.chatsList), captor.capture())
        assertEquals(null, shadowOf(captor.firstValue).savedIntent.extras)
        assertEquals(DuckAiWidgetTarget.CHAT.requestCode, shadowOf(captor.firstValue).requestCode)
        verify(duckChat).buildChatUrl("a")
        verify(duckChat).buildChatUrl("b")
    }

    @Test
    @Config(sdk = [34])
    fun whenChatsAvailableThenRowsHaveNoSectionHeadersAndPinnedChatsComeFirstWithPinnedIcon() = runTest {
        stubDuckAi(enabled = true, historyAvailable = true)
        whenever(chatStore.getChats()).thenReturn(
            listOf(
                chat("recent-old", lastEdit = "2026-01-01T00:00:00.000Z"),
                chat("recent-new", lastEdit = "2026-03-01T00:00:00.000Z"),
                chat("pinned", pinned = true, lastEdit = "2026-02-01T00:00:00.000Z"),
            ),
        )
        whenever(duckChat.buildChatUrl(any())).thenAnswer { "https://duck.ai/?chatID=${it.arguments[0]}" }

        testee.populateRemoteViews(activity, APP_WIDGET_ID, remoteViews)

        val captor = argumentCaptor<RemoteViews.RemoteCollectionItems>()
        verify(remoteViews).setRemoteAdapter(eq(R.id.chatsList), captor.capture())
        val items = captor.firstValue
        val rows = (0 until items.itemCount).map { items.getItemView(it).apply(activity, FrameLayout(activity)) }
        assertEquals(3, rows.size)
        assertEquals(
            listOf("Title pinned", "Title recent-new", "Title recent-old"),
            rows.map {
                it.findViewById<TextView>(R.id.chatsItemTitle).text.toString()
            },
        )
        assertEquals(
            listOf(R.drawable.ic_pin_24_daynight, R.drawable.ic_chat_24_daynight, R.drawable.ic_chat_24_daynight),
            rows.map { shadowOf(it.findViewById<ImageView>(R.id.chatsItemIcon).drawable).createdFromResId },
        )
    }

    @Test
    @Config(sdk = [34])
    fun whenChatsOfEachTypeThenRowIconFollowsNativeTypeAndPinnedRules() = runTest {
        stubDuckAi(enabled = true, historyAvailable = true)
        val chats = listOf(
            chat("discussion", lastEdit = "2026-01-01T00:00:01.000Z"),
            chat("image", isImageGeneration = true, lastEdit = "2026-01-01T00:00:02.000Z"),
            chat("voice", isVoice = true, lastEdit = "2026-01-01T00:00:03.000Z"),
            chat("both", isImageGeneration = true, isVoice = true, lastEdit = "2026-01-01T00:00:04.000Z"),
            chat("pinned-discussion", pinned = true, lastEdit = "2026-01-01T00:00:05.000Z"),
            chat("pinned-image", pinned = true, isImageGeneration = true, lastEdit = "2026-01-01T00:00:06.000Z"),
            chat("pinned-voice", pinned = true, isVoice = true, lastEdit = "2026-01-01T00:00:07.000Z"),
        )
        whenever(chatStore.getChats()).thenReturn(chats)
        whenever(duckChat.buildChatUrl(any())).thenAnswer { "https://duck.ai/?chatID=${it.arguments[0]}" }

        testee.populateRemoteViews(activity, APP_WIDGET_ID, remoteViews)

        val captor = argumentCaptor<RemoteViews.RemoteCollectionItems>()
        verify(remoteViews).setRemoteAdapter(eq(R.id.chatsList), captor.capture())
        val items = captor.firstValue
        val iconsByTitle = (0 until items.itemCount).map { items.getItemView(it).apply(activity, FrameLayout(activity)) }.associate {
            it.findViewById<TextView>(R.id.chatsItemTitle).text.toString() to
                shadowOf(it.findViewById<ImageView>(R.id.chatsItemIcon).drawable).createdFromResId
        }
        assertEquals(R.drawable.ic_chat_24_daynight, iconsByTitle["Title discussion"])
        assertEquals(R.drawable.ic_images_24_daynight, iconsByTitle["Title image"])
        assertEquals(R.drawable.ic_voice_24_daynight, iconsByTitle["Title voice"])
        assertEquals(R.drawable.ic_images_24_daynight, iconsByTitle["Title both"])
        assertEquals(R.drawable.ic_pin_24_daynight, iconsByTitle["Title pinned-discussion"])
        assertEquals(R.drawable.ic_images_pin_24_daynight, iconsByTitle["Title pinned-image"])
        assertEquals(R.drawable.ic_voice_pin_24_daynight, iconsByTitle["Title pinned-voice"])
    }

    @Test
    @Config(sdk = [34])
    fun whenChatsAvailableThenEachRowFillInIntentOpensItsOwnChat() = runTest {
        stubDuckAi(enabled = true, historyAvailable = true)
        whenever(chatStore.getChats()).thenReturn(
            listOf(
                chat("a", lastEdit = "2026-01-01T00:00:00.000Z"),
                chat("b", lastEdit = "2026-02-01T00:00:00.000Z"),
            ),
        )
        whenever(duckChat.buildChatUrl(any())).thenAnswer { "https://duck.ai/?chatID=${it.arguments[0]}" }

        testee.populateRemoteViews(activity, APP_WIDGET_ID, remoteViews)

        val captor = argumentCaptor<RemoteViews.RemoteCollectionItems>()
        verify(remoteViews).setRemoteAdapter(eq(R.id.chatsList), captor.capture())
        val items = captor.firstValue
        val fillIns = (0 until items.itemCount).associate { index ->
            val row = items.getItemView(index)
            val title = row.apply(activity, FrameLayout(activity)).findViewById<TextView>(R.id.chatsItemTitle).text.toString()
            title to row.fillInIntent()
        }
        assertEquals(setOf("Title a", "Title b"), fillIns.keys)
        assertLaunch(fillIns.getValue("Title a"), url = "https://duck.ai/?chatID=a")
        assertLaunch(fillIns.getValue("Title b"), url = "https://duck.ai/?chatID=b")
    }

    private fun RemoteViews.fillInIntent(): Intent {
        val actions = reflectField(this, "mActions") as Iterable<*>
        val response = reflectField(actions.filterNotNull().single { it.javaClass.simpleName == "SetOnClickResponse" }, "mResponse")
        return reflectField(response!!, "mFillIntent") as Intent
    }

    private fun reflectField(target: Any, name: String): Any? =
        generateSequence<Class<*>>(target.javaClass) { it.superclass }
            .mapNotNull { runCatching { it.getDeclaredField(name) }.getOrNull() }
            .first()
            .apply { isAccessible = true }
            .get(target)

    @Test
    fun whenNoChatsThenEmptyMessageShownAndActionsStillWired() = runTest {
        stubDuckAi(enabled = true, historyAvailable = true)
        whenever(chatStore.getChats()).thenReturn(emptyList())

        testee.populateRemoteViews(activity, APP_WIDGET_ID, remoteViews)

        verify(remoteViews).setViewVisibility(R.id.chatsList, View.GONE)
        verify(remoteViews).setViewVisibility(R.id.chatsMessageEmpty, View.VISIBLE)
        verify(remoteViews).setViewVisibility(R.id.chatsMessageUnavailable, View.GONE)
        verify(remoteViews).setOnClickPendingIntent(eq(R.id.chatsViewAll), any())
        verify(remoteViews, never()).setPendingIntentTemplate(any(), any())
    }

    @Test
    fun whenDuckAiDisabledThenNoClickTargetsWiredAndUnavailableMessageShown() = runTest {
        whenever(duckChat.isEnabled()).thenReturn(false)

        testee.populateRemoteViews(activity, APP_WIDGET_ID, remoteViews)

        verify(remoteViews, never()).setOnClickPendingIntent(any(), any())
        verify(remoteViews, never()).setPendingIntentTemplate(any(), any())
        verify(remoteViews).setViewVisibility(R.id.chatsMessageUnavailable, View.VISIBLE)
        verify(remoteViews).setViewVisibility(R.id.chatsMessageEmpty, View.GONE)
        verify(chatStore, never()).getChats()
    }

    @Test
    @Config(sdk = [34])
    fun whenDynamicColorsFlagOffThenWidgetViewsTintedWithStaticColorResources() = runTest {
        stubDuckAi(enabled = true, historyAvailable = false)
        duckChatFeature.duckAiWidgetDynamicColors().setRawStoredState(State(enable = false))

        testee.populateRemoteViews(activity, APP_WIDGET_ID, remoteViews)

        verifyWidgetTints(
            background = R.color.duckAiWidgetBackgroundColor,
            searchBar = R.color.duckAiWidgetSearchBarColor,
            container = R.color.duckAiWidgetContainerColor,
            onContainer = R.color.duckAiWidgetOnContainerColor,
            hint = R.color.duckAiWidgetHintColor,
            text = R.color.duckAiWidgetTextColor,
            divider = R.color.duckAiWidgetDividerColor,
        )
    }

    @Test
    @Config(sdk = [34])
    fun whenDynamicColorsFlagOnThenWidgetViewsTintedWithDynamicColorResources() = runTest {
        stubDuckAi(enabled = true, historyAvailable = false)
        duckChatFeature.duckAiWidgetDynamicColors().setRawStoredState(State(enable = true))

        testee.populateRemoteViews(activity, APP_WIDGET_ID, remoteViews)

        verifyWidgetTints(
            background = R.color.duckAiWidgetDynamicBackgroundColor,
            searchBar = R.color.duckAiWidgetDynamicSearchBarColor,
            container = R.color.duckAiWidgetDynamicContainerColor,
            onContainer = R.color.duckAiWidgetDynamicOnContainerColor,
            hint = R.color.duckAiWidgetDynamicHintColor,
            text = R.color.duckAiWidgetDynamicTextColor,
            divider = R.color.duckAiWidgetDynamicDividerColor,
        )
    }

    private fun verifyWidgetTints(
        background: Int,
        searchBar: Int,
        container: Int,
        onContainer: Int,
        hint: Int,
        text: Int,
        divider: Int,
    ) {
        verify(remoteViews).setColorStateList(R.id.duckAiWidgetContainer, BACKGROUND_TINT, background)
        verify(remoteViews).setColorStateList(R.id.promptBarContainer, BACKGROUND_TINT, searchBar)
        listOf(R.id.actionVoice, R.id.actionCreateImage, R.id.actionTakePhoto).forEach {
            verify(remoteViews).setColorStateList(it, BACKGROUND_TINT, container)
        }
        listOf(R.id.iconVoice, R.id.iconImage, R.id.iconPhoto).forEach {
            verify(remoteViews).setColorStateList(it, IMAGE_TINT, onContainer)
        }
        listOf(R.id.labelVoice, R.id.labelCreateImage, R.id.labelTakePhoto).forEach {
            verify(remoteViews).setColorStateList(it, TEXT_COLOR, onContainer)
        }
        listOf(R.id.promptBarHint, R.id.chatsMessageEmpty, R.id.chatsMessageUnavailable).forEach {
            verify(remoteViews).setColorStateList(it, TEXT_COLOR, hint)
        }
        verify(remoteViews).setColorStateList(R.id.chatsViewAll, TEXT_COLOR, text)
        listOf(R.id.dividerTop, R.id.dividerBottom).forEach {
            verify(remoteViews).setColorStateList(it, BACKGROUND_TINT, divider)
        }
    }

    @Test
    @Config(sdk = [30])
    fun whenOnApi30ThenNoColorSettersApplied() = runTest {
        stubDuckAi(enabled = true, historyAvailable = false)
        duckChatFeature.duckAiWidgetDynamicColors().setRawStoredState(State(enable = true))

        testee.populateRemoteViews(activity, APP_WIDGET_ID, remoteViews)

        assertTrue(mockingDetails(remoteViews).invocations.none { it.method.name == "setColorStateList" })
    }

    @Test
    @Config(sdk = [34])
    fun whenDynamicColorsFlagOnThenChatRowsTintedWithDynamicColors() = runTest {
        val row = populateAndApplyFirstChatRow(dynamicColors = true)

        assertEquals(
            activity.getColor(R.color.duckAiWidgetDynamicContainerColor),
            row.findViewById<ImageView>(R.id.chatsItemIcon).backgroundTintList?.defaultColor,
        )
        assertEquals(
            activity.getColor(R.color.duckAiWidgetDynamicOnContainerColor),
            row.findViewById<ImageView>(R.id.chatsItemIcon).imageTintList?.defaultColor,
        )
        assertEquals(activity.getColor(R.color.duckAiWidgetDynamicTextColor), row.findViewById<TextView>(R.id.chatsItemTitle).currentTextColor)
    }

    @Test
    @Config(sdk = [34])
    fun whenDynamicColorsFlagOffThenChatRowsTintedWithStaticColors() = runTest {
        val row = populateAndApplyFirstChatRow(dynamicColors = false)

        assertEquals(
            activity.getColor(R.color.duckAiWidgetContainerColor),
            row.findViewById<ImageView>(R.id.chatsItemIcon).backgroundTintList?.defaultColor,
        )
        assertEquals(
            activity.getColor(R.color.duckAiWidgetOnContainerColor),
            row.findViewById<ImageView>(R.id.chatsItemIcon).imageTintList?.defaultColor,
        )
        assertEquals(activity.getColor(R.color.duckAiWidgetTextColor), row.findViewById<TextView>(R.id.chatsItemTitle).currentTextColor)
    }

    private suspend fun populateAndApplyFirstChatRow(dynamicColors: Boolean): View {
        stubDuckAi(enabled = true, historyAvailable = true)
        duckChatFeature.duckAiWidgetDynamicColors().setRawStoredState(State(enable = dynamicColors))
        whenever(chatStore.getChats()).thenReturn(listOf(chat("a")))
        whenever(duckChat.buildChatUrl(any())).thenReturn(CHAT_URL)

        testee.populateRemoteViews(activity, APP_WIDGET_ID, remoteViews)

        val captor = argumentCaptor<RemoteViews.RemoteCollectionItems>()
        verify(remoteViews).setRemoteAdapter(eq(R.id.chatsList), captor.capture())
        return captor.firstValue.getItemView(0).apply(activity, FrameLayout(activity))
    }

    @Test
    fun whenHeightBelowThresholdThenChatsHiddenAndNotWired() = runTest {
        stubDuckAi(enabled = true, historyAvailable = true)

        testee.populateRemoteViews(activity, APP_WIDGET_ID, remoteViews, heightDp = MIN_CHATS_HEIGHT_DP - 1)

        verify(remoteViews).setViewVisibility(R.id.chatsSection, View.GONE)
        verify(remoteViews, never()).setOnClickPendingIntent(eq(R.id.chatsViewAll), any())
        verify(remoteViews).setOnClickPendingIntent(eq(R.id.actionVoice), any())
    }

    @Test
    fun whenHeightAtThresholdThenChatsShown() = runTest {
        stubDuckAi(enabled = true, historyAvailable = true)

        testee.populateRemoteViews(activity, APP_WIDGET_ID, remoteViews, heightDp = MIN_CHATS_HEIGHT_DP)

        verify(remoteViews).setViewVisibility(R.id.chatsSection, View.VISIBLE)
    }

    @Test
    fun whenPrefOffAndLargeHeightThenChatsHidden() = runTest {
        stubDuckAi(enabled = true, historyAvailable = true)
        whenever(widgetPreferences.showRecentChats(APP_WIDGET_ID)).thenReturn(false)

        testee.populateRemoteViews(activity, APP_WIDGET_ID, remoteViews, heightDp = 400)

        verify(remoteViews).setViewVisibility(R.id.chatsSection, View.GONE)
    }

    @Test
    @Config(sdk = [30])
    fun whenBelowApi31ThenSingleVariantFollowsHeight() = runTest {
        stubDuckAi(enabled = true, historyAvailable = true)

        assertEquals(View.GONE, chatsSectionVisibility(testee.createRemoteViews(activity, APP_WIDGET_ID, MIN_CHATS_HEIGHT_DP - 1)))
        assertEquals(View.VISIBLE, chatsSectionVisibility(testee.createRemoteViews(activity, APP_WIDGET_ID, MIN_CHATS_HEIGHT_DP)))
    }

    @Test
    @Config(sdk = [34])
    fun whenApi31AndPrefOnThenSizeMapHasWidthAndHeightVariantsAllWired() = runTest {
        stubDuckAi(enabled = true, historyAvailable = true)

        val views = testee.createRemoteViews(activity, APP_WIDGET_ID, heightDp = 50)

        val variants = sizedVariantsByKey(views)
        assertEquals(
            setOf(
                SizeF(180f, 110f),
                SizeF(180f, MIN_CHATS_HEIGHT_DP.toFloat()),
                SizeF(MIN_LABELS_WIDTH_DP.toFloat(), 110f),
                SizeF(MIN_LABELS_WIDTH_DP.toFloat(), MIN_CHATS_HEIGHT_DP.toFloat()),
            ),
            variants.keys,
        )
        variants.forEach { (size, variant) ->
            val root = variant.apply(activity, FrameLayout(activity))
            val expectedLabels = if (size.width >= MIN_LABELS_WIDTH_DP) View.VISIBLE else View.GONE
            val expectedChats = if (size.height >= MIN_CHATS_HEIGHT_DP) View.VISIBLE else View.GONE
            assertEquals(expectedChats, root.findViewById<View>(R.id.chatsSection).visibility)
            labelIds.forEach { assertEquals(expectedLabels, root.findViewById<View>(it).visibility) }
            listOf(R.id.promptBarContainer, R.id.actionVoice, R.id.actionCreateImage, R.id.actionTakePhoto).forEach { id ->
                assertTrue(root.findViewById<View>(id).hasOnClickListeners())
            }
        }
    }

    @Test
    @Config(sdk = [34])
    fun whenDynamicColorsFlagOnThenEverySizeMapVariantIsTinted() = runTest {
        stubDuckAi(enabled = true, historyAvailable = true)
        duckChatFeature.duckAiWidgetDynamicColors().setRawStoredState(State(enable = true))

        val variants = sizedVariantsByKey(testee.createRemoteViews(activity, APP_WIDGET_ID, heightDp = 50))

        assertEquals(4, variants.size)
        val expectedBackground = activity.getColorStateList(R.color.duckAiWidgetDynamicBackgroundColor)
        val expectedLabel = activity.getColorStateList(R.color.duckAiWidgetDynamicOnContainerColor)
        variants.values.forEach { variant ->
            val root = variant.apply(activity, FrameLayout(activity))
            assertEquals(expectedBackground, root.findViewById<View>(R.id.duckAiWidgetContainer).backgroundTintList)
            labelIds.forEach { assertEquals(expectedLabel, root.findViewById<TextView>(it).textColors) }
        }
    }

    @Test
    @Config(sdk = [34])
    fun whenApi31AndPrefOffThenOnlyNarrowAndWideCompactVariants() = runTest {
        stubDuckAi(enabled = true, historyAvailable = true)
        whenever(widgetPreferences.showRecentChats(APP_WIDGET_ID)).thenReturn(false)

        val views = testee.createRemoteViews(activity, APP_WIDGET_ID, heightDp = 400)

        val variants = sizedVariantsByKey(views)
        assertEquals(setOf(SizeF(180f, 110f), SizeF(MIN_LABELS_WIDTH_DP.toFloat(), 110f)), variants.keys)
        variants.values.forEach { assertEquals(View.GONE, chatsSectionVisibility(it)) }
    }

    @Test
    fun whenWidthBelowThresholdThenLabelsHiddenAndPillsDescribed() = runTest {
        stubDuckAi(enabled = true, historyAvailable = false)

        testee.populateRemoteViews(activity, APP_WIDGET_ID, remoteViews, widthDp = MIN_LABELS_WIDTH_DP - 1)

        labelIds.forEach { verify(remoteViews).setViewVisibility(it, View.GONE) }
        verify(remoteViews).setContentDescription(R.id.actionVoice, "Voice")
        verify(remoteViews).setContentDescription(R.id.actionCreateImage, "Image")
        verify(remoteViews).setContentDescription(R.id.actionTakePhoto, "Camera")
        verify(remoteViews).setOnClickPendingIntent(eq(R.id.actionVoice), any())
    }

    @Test
    fun whenWidthAtThresholdThenLabelsShownWithoutPillDescription() = runTest {
        stubDuckAi(enabled = true, historyAvailable = false)

        testee.populateRemoteViews(activity, APP_WIDGET_ID, remoteViews, widthDp = MIN_LABELS_WIDTH_DP)

        labelIds.forEach { verify(remoteViews).setViewVisibility(it, View.VISIBLE) }
        verify(remoteViews).setContentDescription(R.id.actionVoice, null)
        verify(remoteViews).setContentDescription(R.id.actionCreateImage, null)
        verify(remoteViews).setContentDescription(R.id.actionTakePhoto, null)
    }

    @Test
    @Config(sdk = [30])
    fun whenBelowApi31ThenSingleVariantFollowsWidth() = runTest {
        stubDuckAi(enabled = true, historyAvailable = true)

        val narrow = testee.createRemoteViews(activity, APP_WIDGET_ID, 400, MIN_LABELS_WIDTH_DP - 1).apply(activity, FrameLayout(activity))
        val wide = testee.createRemoteViews(activity, APP_WIDGET_ID, 400, MIN_LABELS_WIDTH_DP).apply(activity, FrameLayout(activity))

        labelIds.forEach {
            assertEquals(View.GONE, narrow.findViewById<View>(it).visibility)
            assertEquals(View.VISIBLE, wide.findViewById<View>(it).visibility)
        }
    }

    private val labelIds = listOf(R.id.labelVoice, R.id.labelCreateImage, R.id.labelTakePhoto)

    private fun chatsSectionVisibility(views: RemoteViews) = views.apply(
        activity,
        FrameLayout(activity),
    ).findViewById<View>(R.id.chatsSection).visibility

    @Suppress("UNCHECKED_CAST")
    private fun sizedVariants(views: RemoteViews): List<RemoteViews> {
        val field = RemoteViews::class.java.getDeclaredField("mSizedRemoteViews").apply { isAccessible = true }
        return (field.get(views) as List<RemoteViews>?).orEmpty()
    }

    private fun sizedVariantsByKey(views: RemoteViews): Map<SizeF, RemoteViews> =
        sizedVariants(views).associateBy { variant ->
            val field = RemoteViews::class.java.getDeclaredField("mIdealSize").apply { isAccessible = true }
            field.get(variant) as SizeF
        }

    @Test
    fun whenAllTargetsBuiltThenRequestCodesAreDistinct() {
        val requestCodes = DuckAiWidgetTarget.entries.map { it.requestCode }

        assertEquals(requestCodes.size, requestCodes.toSet().size)
    }

    private suspend fun stubDuckAi(
        enabled: Boolean,
        historyAvailable: Boolean,
    ) {
        whenever(duckChat.isEnabled()).thenReturn(enabled)
        whenever(duckChat.isChatHistoryAvailable()).thenReturn(historyAvailable)
        if (historyAvailable) whenever(chatStore.getChats()).thenReturn(emptyList())
    }

    private suspend fun populateAndCaptureClickIntent(viewId: Int): Intent {
        testee.populateRemoteViews(activity, APP_WIDGET_ID, remoteViews)
        val captor = argumentCaptor<PendingIntent>()
        verify(remoteViews).setOnClickPendingIntent(eq(viewId), captor.capture())
        return shadowOf(captor.firstValue).savedIntent
    }

    private fun chat(
        id: String,
        pinned: Boolean = false,
        lastEdit: String = "2026-01-01T00:00:00.000Z",
        isImageGeneration: Boolean = false,
        isVoice: Boolean = false,
    ) = DuckAiChat(
        chatId = id,
        title = "Title $id",
        model = "model",
        lastEdit = lastEdit,
        pinned = pinned,
        isImageGeneration = isImageGeneration,
        isVoice = isVoice,
    )

    private companion object {
        const val APP_WIDGET_ID = 7
        const val BACKGROUND_TINT = "setBackgroundTintList"
        const val IMAGE_TINT = "setImageTintList"
        const val TEXT_COLOR = "setTextColor"
        const val CHAT_URL = "https://duck.ai/?chatID=abc"
    }
}
