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

package com.duckduckgo.subscriptions.impl.onboarding.features

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.GridLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.withStyledAttributes
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.databinding.ViewPlatformIconItemBinding

class PlatformIconsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : GridLayout(context, attrs, defStyleAttr) {

    enum class Platform(
        val flag: Int,
        @DrawableRes val iconRes: Int,
        @StringRes val labelRes: Int,
    ) {
        IOS(flag = 1, iconRes = R.drawable.platform_apple_24, labelRes = R.string.subscriptionOnboardingPlatformIos),
        ANDROID(flag = 2, iconRes = R.drawable.platform_android_24, labelRes = R.string.subscriptionOnboardingPlatformAndroid),
        MAC(flag = 4, iconRes = R.drawable.platform_macos_24, labelRes = R.string.subscriptionOnboardingPlatformMac),
        WINDOWS(flag = 8, iconRes = R.drawable.platform_windows_24, labelRes = R.string.subscriptionOnboardingPlatformWindows),
    }

    init {
        columnCount = COLUMN_COUNT
        var visible = 0
        context.withStyledAttributes(attrs, R.styleable.PlatformIconsView) {
            visible = getInt(R.styleable.PlatformIconsView_platformsVisible, 0)
        }
        render(visible)
    }

    private fun render(flags: Int) {
        removeAllViews()
        Platform.entries
            .filter { flags and it.flag != 0 }
            .forEach { platform ->
                val cell = ViewPlatformIconItemBinding.inflate(LayoutInflater.from(context), this, false)
                cell.platformIcon.setImageResource(platform.iconRes)
                cell.platformName.setText(platform.labelRes)
                cell.root.layoutParams = LayoutParams().apply { setMargins(0, 0, cellSpacing, cellSpacing) }
                addView(cell.root)
            }
    }

    private val cellSpacing: Int get() = 12.toPx()

    fun setPlatforms(flags: Int) = render(flags)

    private companion object {
        private const val COLUMN_COUNT = 2
    }
}
