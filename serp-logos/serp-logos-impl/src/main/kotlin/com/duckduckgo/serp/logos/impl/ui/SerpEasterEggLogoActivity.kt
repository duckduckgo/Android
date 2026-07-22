/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.serp.logos.impl.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.serp.logos.api.SerpLogoScreens.EasterEggLogoScreen
import com.duckduckgo.serp.logos.impl.R
import com.duckduckgo.serp.logos.impl.databinding.ActivitySerpEasterEggLogoBinding
import com.duckduckgo.serp.logos.impl.ui.SerpEasterEggLogoViewModel.Command.CloseScreen
import com.duckduckgo.serp.logos.impl.ui.SerpEasterEggLogoViewModel.ViewState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(EasterEggLogoScreen::class, screenName = "easterEggLogo")
class SerpEasterEggLogoActivity : DuckDuckGoActivity() {

    private val viewModel: SerpEasterEggLogoViewModel by bindViewModel()
    private lateinit var binding: ActivitySerpEasterEggLogoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_DuckDuckGo_DynamicLogo)
        binding = ActivitySerpEasterEggLogoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportPostponeEnterTransition()

        val params = intent.getActivityParams(EasterEggLogoScreen::class.java)
            ?: throw IllegalArgumentException("EasterEggLogoScreen params are required")

        val logoUrl = params.logoUrl
        val transitionName = params.transitionName

        viewModel.setLogoUrl(logoUrl)
        observeViewModel()

        ViewCompat.setTransitionName(binding.enlargedLogoImage, transitionName)

        window.returnTransition?.setDuration(100)

        Glide.with(this)
            .load(logoUrl)
            .dontAnimate()
            .dontTransform()
            .onlyRetrieveFromCache(true)
            .listener(
                object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable?>,
                        isFirstResource: Boolean,
                    ): Boolean {
                        supportStartPostponedEnterTransition()
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable?>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean,
                    ): Boolean {
                        supportStartPostponedEnterTransition()

                        AnimatorSet().apply {
                            playTogether(
                                ObjectAnimator.ofFloat(binding.discoveryText, View.ALPHA, 1f),
                                ObjectAnimator.ofFloat(binding.closeIcon, View.ALPHA, 1f),
                                ObjectAnimator.ofFloat(binding.favouriteButton, View.ALPHA, 1f),
                            )
                            startDelay = 150
                            duration = 150
                            start()
                        }

                        return false
                    }
                },
            )
            .into(binding.enlargedLogoImage)

        binding.root.setOnClickListener {
            viewModel.onBackgroundClicked()
        }

        binding.favouriteButton.setOnClickListener {
            viewModel.onFavouriteButtonClicked()
        }
    }

    private fun observeViewModel() {
        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { renderViewState(it) }
            .launchIn(lifecycleScope)

        viewModel
            .commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun renderViewState(viewState: ViewState) {
        with(binding) {
            favouriteButton.isVisible = viewState.isSetFavouriteEnabled
            favouriteButton.text = if (viewState.isFavourite) {
                getString(R.string.serpLogoResetToDefault)
            } else {
                getString(R.string.serpLogoSetAsFavourite)
            }
        }
    }

    private fun processCommand(command: SerpEasterEggLogoViewModel.Command) {
        when (command) {
            CloseScreen -> {
                animateBackgroundDimFadeOut()
                supportFinishAfterTransition()
            }
        }
    }

    private fun animateBackgroundDimFadeOut() {
        ValueAnimator.ofFloat(window.attributes.dimAmount, 0f).apply {
            duration = 500
            addUpdateListener { animator ->
                val dimAmount = animator.animatedValue as Float
                window.setDimAmount(dimAmount)
                window.attributes = window.attributes.apply {
                    this.dimAmount = dimAmount
                }
                window.attributes = window.attributes
            }
            start()
        }
    }
}
