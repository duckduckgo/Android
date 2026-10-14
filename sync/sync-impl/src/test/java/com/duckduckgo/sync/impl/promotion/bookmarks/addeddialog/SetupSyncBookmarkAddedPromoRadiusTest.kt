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

package com.duckduckgo.sync.impl.promotion.bookmarks.addeddialog

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import androidx.appcompat.app.AppCompatActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.ui.store.AppBrandDesignUpdateToggles
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.api.SyncStateMonitor
import com.duckduckgo.sync.impl.promotion.SyncPromotions
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import com.duckduckgo.mobile.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class SetupSyncBookmarkAddedPromoRadiusTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    @Test
    fun whenRadiusToggleIsDisabledThenPromoBackgroundAndRippleKeep8DpCorners() = runTest {
        assertPromoRadii(radiusEnabled = false, expectedDp = 8f)
    }

    @Test
    @Config(qualifiers = "xhdpi")
    fun whenRadiusToggleIsEnabledThenPromoBackgroundAndRippleUse16DpIndependentlyOfMenuRadius() = runTest {
        assertPromoRadii(radiusEnabled = true, expectedDp = 16f)
    }

    private suspend fun assertPromoRadii(radiusEnabled: Boolean, expectedDp: Float) {
        val toggles = FakeFeatureToggleFactory.create(AppBrandDesignUpdateToggles::class.java)
        toggles.self().setRawStoredState(State(enable = true))
        toggles.radius().setRawStoredState(State(enable = radiusEnabled))
        val syncPromotions = mock<SyncPromotions>()
        whenever(syncPromotions.canShowBookmarkAddedDialogPromotion()).thenReturn(true)
        val syncStateMonitor = mock<SyncStateMonitor>()
        whenever(syncStateMonitor.syncState()).thenReturn(emptyFlow())

        listOf(
            CommonR.style.Theme_DuckDuckGo_Light,
            CommonR.style.Theme_DuckDuckGo_Dark,
            CommonR.style.Theme_DuckDuckGo_Light_Fire,
            CommonR.style.Theme_DuckDuckGo_Dark_Fire,
        ).forEach { themeResId ->
            val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
            val activity = controller.get().apply { setTheme(themeResId) }
            controller.setup()
            try {
                val promo = SetupSyncBookmarkAddedPromo(
                    globalActivityStarter = mock(),
                    dispatchers = coroutineTestRule.testDispatcherProvider,
                    activity = activity,
                    syncPromotions = syncPromotions,
                    syncStateMonitor = syncStateMonitor,
                    syncPixels = mock(),
                    appBrandDesignUpdateToggles = toggles,
                )
                val ripple = checkNotNull(promo.getView()).background as RippleDrawable
                val mask = ripple.findDrawableByLayerId(android.R.id.mask) as GradientDrawable
                val background = ripple.getDrawable(1) as GradientDrawable
                val expectedRadius = expectedDp * activity.resources.displayMetrics.density
                val menuRadius = activity.obtainStyledAttributes(intArrayOf(CommonR.attr.daxMenuRadius))
                try {
                    assertEquals(8f * activity.resources.displayMetrics.density, menuRadius.getDimension(0, 0f), 0f)
                } finally {
                    menuRadius.recycle()
                }

                assertEquals(expectedRadius, background.cornerRadius, 0f)
                assertEquals(expectedRadius, mask.cornerRadius, 0f)
            } finally {
                controller.pause().stop().destroy()
            }
        }
    }
}
