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

package com.duckduckgo.mobile.android.vpn.ui.newtab

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.text.HtmlCompat
import androidx.core.view.children
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.ui.view.InfoPanel
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.common.utils.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.common.utils.plugins.view_model.ViewViewModelFactoryPluginPoint
import com.duckduckgo.di.android.AndroidInjector
import com.duckduckgo.di.android.HasInjector
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.DISABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.ENABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.REVOKED
import com.duckduckgo.mobile.android.vpn.ui.privacyreport.PrivacyReportViewModel
import com.duckduckgo.mobile.android.vpn.ui.privacyreport.PrivacyReportViewModel.PrivacyReportView.TrackersBlocked
import com.duckduckgo.mobile.android.vpn.ui.privacyreport.PrivacyReportViewModel.PrivacyReportView.ViewState
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.DeviceShieldTrackerActivity
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import com.duckduckgo.mobile.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class AppTrackingProtectionStateViewTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private lateinit var activity: TestActivity

    @Test
    fun whenAreaIsOffThenDisabledStateKeepsExistingCard() {
        forEachTheme(false) { context ->
            val view = createView(context)
            activity.emitState(disabledState)
            assertFalse(view.children.any { it is InfoPanel })
            assertEquals(View.VISIBLE, view.findViewById<View>(R.id.deviceShieldCtaLayout).visibility)
            assertEquals(context.getString(R.string.atp_NewTabDisabled), view.findViewById<TextView>(R.id.deviceShieldCtaHeader).text.toString())
            assertEquals(
                CommonR.drawable.exclamation_recolorable_24,
                shadowOf(view.findViewById<ImageView>(R.id.deviceShieldCtaImage).drawable).createdFromResId,
            )
        }
    }

    @Test
    fun whenAreaIsOnThenDisabledStateUsesAlertPanelWithSameTextMarginsAndClickBehaviour() {
        forEachTheme(true) { context ->
            val view = createView(context)
            activity.emitState(disabledState)
            val card = view.findViewById<View>(R.id.deviceShieldCtaLayout)
            val panel = view.children.filterIsInstance<InfoPanel>().single()
            assertEquals(View.GONE, card.visibility)
            assertEquals(View.VISIBLE, panel.visibility)
            assertEquals(context.getString(R.string.atp_NewTabDisabled), panel.findViewById<TextView>(CommonR.id.infoPanelText).text.toString())
            val panelText = panel.findViewById<TextView>(CommonR.id.infoPanelText).text
            assertTrue(panelText is Spanned)
            assertTrue((panelText as Spanned).getSpans(0, panelText.length, StyleSpan::class.java).any { it.style == Typeface.BOLD })
            assertEquals(
                CommonR.drawable.exclamation_recolorable_invert_16,
                shadowOf(panel.findViewById<ImageView>(CommonR.id.infoPanelImage).drawable).createdFromResId,
            )
            assertEquals(context.getColorFromAttr(CommonR.attr.daxColorSurfaceSecondary), (panel.background as GradientDrawable).color!!.defaultColor)
            assertTrue(panel.foreground is RippleDrawable)
            assertTrue(panel.clipToOutline)
            panel.layout(0, 0, 300, 100)
            panel.background.setBounds(0, 0, 300, 100)
            val outline = Outline()
            panel.outlineProvider.getOutline(panel, outline)
            assertEquals((panel.background as GradientDrawable).cornerRadius, outline.radius, 0f)
            val margins = panel.layoutParams as FrameLayout.LayoutParams
            val originalMargins = card.layoutParams as FrameLayout.LayoutParams
            assertEquals(
                listOf(originalMargins.leftMargin, originalMargins.topMargin, originalMargins.rightMargin, originalMargins.bottomMargin),
                listOf(margins.leftMargin, margins.topMargin, margins.rightMargin, margins.bottomMargin),
            )
            var clicked = false
            card.setOnClickListener { clicked = true }
            panel.performClick()
            assertTrue(clicked)
        }
    }

    @Test
    fun whenStateChangesThenPanelUpdatesAndRevokedKeepsCardAndHiddenFeatureHidesBoth() {
        forEachTheme(true) { context ->
            val view = createView(context)
            activity.emitState(disabledState)
            val panel = view.children.filterIsInstance<InfoPanel>().single()
            val card = view.findViewById<View>(R.id.deviceShieldCtaLayout)
            activity.emitState(disabledState.copy(vpnState = VpnState(ENABLED)))
            assertEquals(View.VISIBLE, panel.visibility)
            assertEquals(View.GONE, card.visibility)
            assertEquals(context.getString(R.string.atp_NewTabEnabled), panel.findViewById<TextView>(CommonR.id.infoPanelText).text.toString())
            activity.emitState(disabledState.copy(vpnState = VpnState(DISABLED, REVOKED)))
            assertEquals(View.GONE, panel.visibility)
            assertEquals(View.VISIBLE, card.visibility)
            assertEquals(context.getString(R.string.atp_NewTabRevoked), view.findViewById<TextView>(R.id.deviceShieldCtaHeader).text.toString())
            activity.emitState(disabledState)
            assertEquals(View.VISIBLE, panel.visibility)
            assertEquals(context.getString(R.string.atp_NewTabDisabled), panel.findViewById<TextView>(CommonR.id.infoPanelText).text.toString())
            assertEquals(
                CommonR.drawable.exclamation_recolorable_invert_16,
                shadowOf(panel.findViewById<ImageView>(CommonR.id.infoPanelImage).drawable).createdFromResId,
            )
            activity.emitState(disabledState.copy(isFeatureEnabled = false))
            assertEquals(View.GONE, panel.visibility)
            assertEquals(View.GONE, card.visibility)
        }
    }

    @Test
    fun whenAreaIsOnThenEnabledStateUsesProtectionsPanelAndOpensTrackerActivity() {
        forEachTheme(true) { themedContext ->
            val startedIntents = mutableListOf<Intent>()
            val context = object : ContextWrapper(themedContext) {
                override fun startActivity(intent: Intent) {
                    startedIntents += intent
                }
            }
            val view = createView(context)
            activity.emitState(disabledState.copy(vpnState = VpnState(ENABLED)))
            val panel = view.children.filterIsInstance<InfoPanel>().single()
            assertEquals(View.VISIBLE, panel.visibility)
            assertEquals(View.GONE, view.findViewById<View>(R.id.deviceShieldCtaLayout).visibility)
            assertEquals(context.getString(R.string.atp_NewTabEnabled), panel.findViewById<TextView>(CommonR.id.infoPanelText).text.toString())
            assertEquals(
                CommonR.drawable.shield_check_recolorable_16,
                shadowOf(panel.findViewById<ImageView>(CommonR.id.infoPanelImage).drawable).createdFromResId,
            )
            assertTrue(panel.foreground is RippleDrawable)
            assertTrue(panel.clipToOutline)
            panel.performClick()
            verify(view.deviceShieldPixels).didPressNewTabSummary()
            assertEquals(DeviceShieldTrackerActivity::class.java.name, startedIntents.single().component!!.className)
        }
    }

    @Test
    fun whenEnabledWithTrackersThenBothRenderersPreserveStyledText() {
        listOf(false, true).forEach { enabled ->
            forEachTheme(enabled) { context ->
                val view = createView(context)
                activity.emitState(
                    disabledState.copy(
                        vpnState = VpnState(ENABLED),
                        trackersBlocked = TrackersBlocked(latestApp = "Example App", otherAppsSize = 2, trackers = 12),
                    ),
                )
                val text = if (enabled) {
                    val panel = view.children.filterIsInstance<InfoPanel>().single()
                    assertEquals(View.VISIBLE, panel.visibility)
                    panel.findViewById<TextView>(CommonR.id.infoPanelText).text
                } else {
                    assertFalse(view.children.any { it is InfoPanel })
                    assertEquals(View.VISIBLE, view.findViewById<View>(R.id.deviceShieldCtaLayout).visibility)
                    assertEquals(
                        CommonR.drawable.shield_check_recolorable_24,
                        shadowOf(view.findViewById<ImageView>(R.id.deviceShieldCtaImage).drawable).createdFromResId,
                    )
                    view.findViewById<TextView>(R.id.deviceShieldCtaHeader).text
                }
                val expected = HtmlCompat.fromHtml(
                    context.getString(R.string.atp_DailyLastCompanyBlockedHomeTabOtherTimesMoreOtherApps, 12, "Example App", 2),
                    HtmlCompat.FROM_HTML_MODE_LEGACY,
                )
                assertEquals(expected.toString(), text.toString())
                assertTrue(text is Spanned)
                val spans = (text as Spanned).getSpans(0, text.length, StyleSpan::class.java)
                assertTrue(spans.any { it.style == Typeface.BOLD })
                expected.getSpans(0, expected.length, StyleSpan::class.java).forEach { expectedSpan ->
                    assertTrue(
                        spans.any {
                            it.style == expectedSpan.style && text.getSpanStart(it) == expected.getSpanStart(expectedSpan) &&
                                text.getSpanEnd(it) == expected.getSpanEnd(expectedSpan)
                        },
                    )
                }
            }
        }
    }

    @Test
    fun whenAreaIsOffThenEnabledStateKeepsExistingCard() {
        forEachTheme(false) { context ->
            val view = createView(context)
            activity.emitState(disabledState.copy(vpnState = VpnState(ENABLED)))
            assertFalse(view.children.any { it is InfoPanel })
            assertEquals(View.VISIBLE, view.findViewById<View>(R.id.deviceShieldCtaLayout).visibility)
            assertEquals(context.getString(R.string.atp_NewTabEnabled), view.findViewById<TextView>(R.id.deviceShieldCtaHeader).text.toString())
            assertEquals(
                CommonR.drawable.shield_check_recolorable_24,
                shadowOf(view.findViewById<ImageView>(R.id.deviceShieldCtaImage).drawable).createdFromResId,
            )
        }
    }

    @Test
    fun whenAreaIsOffThenStateTransitionsKeepTheLegacyCardAndNeverCreateAnInfoPanel() {
        forEachTheme(false) { context ->
            val view = createView(context)
            val states = listOf(
                disabledState to R.string.atp_NewTabDisabled,
                disabledState.copy(vpnState = VpnState(DISABLED, REVOKED)) to R.string.atp_NewTabRevoked,
                disabledState.copy(vpnState = VpnState(ENABLED)) to R.string.atp_NewTabEnabled,
            )
            states.forEach { (state, text) ->
                activity.emitState(state)
                assertEquals(View.VISIBLE, view.findViewById<View>(R.id.deviceShieldCtaLayout).visibility)
                assertEquals(context.getString(text), view.findViewById<TextView>(R.id.deviceShieldCtaHeader).text.toString())
                assertFalse(view.children.any { it is InfoPanel })
            }
            activity.emitState(disabledState.copy(isFeatureEnabled = false))
            assertEquals(View.GONE, view.findViewById<View>(R.id.deviceShieldCtaLayout).visibility)
            assertFalse(view.children.any { it is InfoPanel })
        }
    }

    private val disabledState = ViewState(VpnState(DISABLED), TrackersBlocked("", 0, 0), true)

    private fun createView(context: Context): AppTrackingProtectionStateView =
        AppTrackingProtectionStateView(context).also { activity.setContentView(it) }

    private fun forEachTheme(enabled: Boolean, assertion: (ContextThemeWrapper) -> Unit) {
        listOf(CommonR.style.Theme_DuckDuckGo_Light, CommonR.style.Theme_DuckDuckGo_Dark).forEach { theme ->
            val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
            activity = controller.get()
            try {
                val context = ContextThemeWrapper(activity, theme)
                if (enabled) context.theme.applyStyle(CommonR.style.ThemeOverlay_Rebrand_InfoPanels, true)
                assertion(context)
            } finally {
                controller.pause().stop().destroy()
            }
        }
    }

    class TestActivity :
        ComponentActivity(),
        HasInjector,
        AndroidInjector.Factory<AppTrackingProtectionStateView, TestActivity>,
        AndroidInjector<AppTrackingProtectionStateView> {

        private val states = MutableStateFlow(ViewState(VpnState(DISABLED), TrackersBlocked("", 0, 0), true))

        fun emitState(state: ViewState) {
            states.value = state
        }

        override fun injectorFactoryFor(key: Class<*>): AndroidInjector.Factory<*, *> = this

        override fun create(instance: AppTrackingProtectionStateView): TestActivity = this

        override fun inject(instance: AppTrackingProtectionStateView) {
            val viewModel = mock<PrivacyReportViewModel>()
            whenever(viewModel.viewStateFlow).thenReturn(states)
            val plugin = mock<ViewModelFactoryPlugin>()
            whenever(plugin.create(PrivacyReportViewModel::class.java)).thenReturn(viewModel)
            instance.viewModelFactory = ViewViewModelFactory(ViewViewModelFactoryPluginPoint(setOf(plugin)))
            instance.deviceShieldPixels = mock<DeviceShieldPixels>()
            instance.dispatchers = mock()
        }
    }
}
