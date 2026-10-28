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
import androidx.core.view.children
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.ui.view.InfoPanel
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.DISABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.ENABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.REVOKED
import com.duckduckgo.mobile.android.vpn.ui.privacyreport.PrivacyReportViewModel.PrivacyReportView.TrackersBlocked
import com.duckduckgo.mobile.android.vpn.ui.privacyreport.PrivacyReportViewModel.PrivacyReportView.ViewState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import com.duckduckgo.mobile.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class AppTrackingProtectionStateViewTest {

    @Test
    fun whenAreaIsOffThenDisabledStateKeepsExistingCard() {
        forEachTheme(false) { context ->
            val view = AppTrackingProtectionStateView(context)
            view.renderViewState(disabledState)
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
            val view = AppTrackingProtectionStateView(context)
            view.renderViewState(disabledState)
            val card = view.findViewById<View>(R.id.deviceShieldCtaLayout)
            val panel = view.children.filterIsInstance<InfoPanel>().single()
            assertEquals(View.GONE, card.visibility)
            assertEquals(View.VISIBLE, panel.visibility)
            assertEquals(context.getString(R.string.atp_NewTabDisabled), panel.findViewById<TextView>(CommonR.id.infoPanelText).text.toString())
            val panelText = panel.findViewById<TextView>(CommonR.id.infoPanelText).text
            assertTrue(panelText is Spanned)
            assertTrue((panelText as Spanned).getSpans(0, panelText.length, StyleSpan::class.java).any { it.style == Typeface.BOLD })
            assertEquals(
                CommonR.drawable.ic_exclamation_recolorable_invert_16,
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
    fun whenStateChangesThenEnabledAndRevokedStatesKeepCardAndHiddenFeatureHidesBoth() {
        forEachTheme(true) { context ->
            val view = AppTrackingProtectionStateView(context)
            view.renderViewState(disabledState)
            val panel = view.children.filterIsInstance<InfoPanel>().single()
            val card = view.findViewById<View>(R.id.deviceShieldCtaLayout)
            listOf(
                disabledState.copy(vpnState = VpnState(ENABLED)) to R.string.atp_NewTabEnabled,
                disabledState.copy(vpnState = VpnState(DISABLED, REVOKED)) to R.string.atp_NewTabRevoked,
            ).forEach { (state, text) ->
                view.renderViewState(state)
                assertEquals(View.GONE, panel.visibility)
                assertEquals(View.VISIBLE, card.visibility)
                assertEquals(context.getString(text), view.findViewById<TextView>(R.id.deviceShieldCtaHeader).text.toString())
            }
            view.renderViewState(disabledState)
            assertEquals(View.VISIBLE, panel.visibility)
            view.renderViewState(disabledState.copy(isFeatureEnabled = false))
            assertEquals(View.GONE, panel.visibility)
            assertEquals(View.GONE, card.visibility)
        }
    }

    private val disabledState = ViewState(VpnState(DISABLED), TrackersBlocked("", 0, 0), true)

    private fun forEachTheme(enabled: Boolean, assertion: (ContextThemeWrapper) -> Unit) {
        listOf(CommonR.style.Theme_DuckDuckGo_Light, CommonR.style.Theme_DuckDuckGo_Dark).forEach { theme ->
            val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), theme)
            if (enabled) context.theme.applyStyle(CommonR.style.ThemeOverlay_Rebrand_InfoPanels, true)
            assertion(context)
        }
    }
}
