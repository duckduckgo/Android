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

import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.core.view.ViewCompat
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
import kotlin.jvm.java

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(EasterEggLogoScreen::class, screenName = "easterEggLogo")
class SerpEasterEggLogoActivity : DuckDuckGoActivity() {

    private lateinit var binding: ActivitySerpEasterEggLogoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_DuckDuckGo_DynamicLogo)
        binding = ActivitySerpEasterEggLogoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportPostponeEnterTransition()

        val params = intent.getActivityParams(EasterEggLogoScreen::class.java)
            ?: throw IllegalArgumentException("EasterEggLogoScreen params are required")

        val logoUrl = params.logoUrl
        val transitionName = params.transitionName

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
                        
                        binding.closeIcon.animate()
                            .alpha(1f)
                            .setStartDelay(150)
                            .setDuration(150)
                            .start()

                        return false
                    }
                },
            )
            .into(binding.enlargedLogoImage)

        binding.root.setOnClickListener {
            supportFinishAfterTransition()
        }
    }
}
