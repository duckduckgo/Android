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

package com.duckduckgo.app.dev.settings.tabs

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityNewTabTransitionOriginDevSettingsBinding
import com.duckduckgo.app.browser.databinding.ItemNewTabTransitionSliderBinding
import com.duckduckgo.app.browser.tabs.NewTabOrigin
import com.duckduckgo.app.browser.tabs.NewTabTransitionConfig
import com.duckduckgo.app.browser.tabs.NewTabTransitionSettings
import com.duckduckgo.app.browser.tabs.OriginProfile
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject

/** The motion values for one [NewTabOrigin]. */
@InjectWith(ActivityScope::class)
class NewTabTransitionOriginDevSettingsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var newTabTransitionSettings: NewTabTransitionSettings

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    private val binding: ActivityNewTabTransitionOriginDevSettingsBinding by viewBinding()

    private val origin: NewTabOrigin by lazy {
        intent.getStringExtra(EXTRA_ORIGIN)?.let { NewTabOrigin.valueOf(it) } ?: NewTabOrigin.RIGHT_OF_CENTER
    }

    private val sliders = mutableListOf<SliderBinding>()

    private var profile: OriginProfile
        get() = newTabTransitionSettings.config.profile(origin)
        set(value) {
            val config = newTabTransitionSettings.config
            newTabTransitionSettings.config = when (origin) {
                NewTabOrigin.BELOW_CENTER -> config.copy(belowCenter = value)
                NewTabOrigin.RIGHT_OF_CENTER -> config.copy(rightOfCenter = value)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableTransparentEdgeToEdge()
        setContentView(binding.root)
        configureEdgeToEdgeInsets()
        setupToolbar(binding.includeToolbar.toolbar)
        setTitle(origin.label())

        val offsetRange = when (origin) {
            NewTabOrigin.BELOW_CENTER -> NewTabTransitionConfig.BELOW_CENTER_OFFSET_RANGE
            NewTabOrigin.RIGHT_OF_CENTER -> NewTabTransitionConfig.RIGHT_OF_CENTER_OFFSET_RANGE
        }
        sliders += listOf(
            SliderBinding(
                binding.startSize,
                R.string.newTabTransitionDevSettingsStartSize,
                NewTabTransitionConfig.START_SIZE_RANGE,
                { it.startSizePercent },
                { profile, value -> profile.copy(startSizePercent = value) },
                ::percent,
            ),
            SliderBinding(
                binding.offset,
                R.string.newTabTransitionDevSettingsOffset,
                offsetRange,
                { it.offsetPercent },
                { profile, value -> profile.copy(offsetPercent = value) },
                ::offsetLabel,
            ),
            SliderBinding(
                binding.scaleBack,
                R.string.newTabTransitionDevSettingsScaleBack,
                NewTabTransitionConfig.SCALE_BACK_RANGE,
                { it.scaleBackPercent },
                { profile, value -> profile.copy(scaleBackPercent = value) },
                ::percent,
            ),
            SliderBinding(
                binding.dim,
                R.string.newTabTransitionDevSettingsDim,
                NewTabTransitionConfig.DIM_RANGE,
                { it.dimPercent },
                { profile, value -> profile.copy(dimPercent = value) },
                ::percent,
            ),
            SliderBinding(
                binding.duration,
                R.string.newTabTransitionDevSettingsDuration,
                NewTabTransitionConfig.DURATION_RANGE,
                { it.durationMs },
                { profile, value -> profile.copy(durationMs = value) },
                { getString(R.string.newTabTransitionDevSettingsMilliseconds, it) },
                step = NewTabTransitionConfig.DURATION_STEP,
            ),
        )
        if (origin == NewTabOrigin.RIGHT_OF_CENTER) {
            sliders += SliderBinding(
                binding.slideLeft,
                R.string.newTabTransitionDevSettingsSlideLeft,
                NewTabTransitionConfig.SLIDE_LEFT_RANGE,
                { it.slideLeftPercent },
                { profile, value -> profile.copy(slideLeftPercent = value) },
                ::percent,
            )
        }
        binding.slideLeft.root.isVisible = origin == NewTabOrigin.RIGHT_OF_CENTER
        sliders.forEach { it.bind() }
        binding.resetButton.setOnClickListener {
            profile = NewTabTransitionConfig.DEFAULT.profile(origin)
            render()
        }
        render()
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout)
        edgeToEdgeHandler.applyNavigationBarInsets(binding.contentLayout, drawBehindGestureNav = false)
    }

    private fun render() {
        val profile = profile
        sliders.forEach { it.render(profile) }
    }

    private fun percent(value: Int): String = getString(R.string.newTabTransitionDevSettingsPercent, value)

    private fun offsetLabel(value: Int): String = when {
        value == 0 -> getString(R.string.newTabTransitionDevSettingsOffsetCentered)
        origin == NewTabOrigin.RIGHT_OF_CENTER -> getString(R.string.newTabTransitionDevSettingsOffsetRight, value)
        value > 0 -> getString(R.string.newTabTransitionDevSettingsOffsetBelow, value)
        else -> getString(R.string.newTabTransitionDevSettingsOffsetAbove, -value)
    }

    private inner class SliderBinding(
        private val row: ItemNewTabTransitionSliderBinding,
        @StringRes private val label: Int,
        private val range: IntRange,
        private val read: (OriginProfile) -> Int,
        private val write: (OriginProfile, Int) -> OriginProfile,
        private val format: (Int) -> String,
        private val step: Int = 1,
    ) {
        fun bind() {
            row.label.setText(label)
            row.slider.valueFrom = range.first.toFloat()
            row.slider.valueTo = range.last.toFloat()
            row.slider.stepSize = step.toFloat()
            row.slider.addOnChangeListener { _, value, fromUser ->
                row.value.text = format(value.toInt())
                if (fromUser) profile = write(profile, value.toInt())
            }
        }

        fun render(profile: OriginProfile) {
            val value = read(profile)
            row.slider.value = value.toFloat()
            row.value.text = format(value)
        }
    }

    companion object {
        private const val EXTRA_ORIGIN = "EXTRA_ORIGIN"

        fun intent(
            context: Context,
            origin: NewTabOrigin,
        ): Intent = Intent(context, NewTabTransitionOriginDevSettingsActivity::class.java).putExtra(EXTRA_ORIGIN, origin.name)
    }
}

@StringRes
internal fun NewTabOrigin.label(): Int = when (this) {
    NewTabOrigin.BELOW_CENTER -> R.string.newTabTransitionDevSettingsBelowCenter
    NewTabOrigin.RIGHT_OF_CENTER -> R.string.newTabTransitionDevSettingsRightOfCenter
}
