/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.easteregglogos

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.duckduckgo.app.browser.databinding.ActivityEnlargedEasterEggLogoBinding

class EnlargedEasterEggLogoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEnlargedEasterEggLogoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEnlargedEasterEggLogoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportPostponeEnterTransition()

        val logoUrl = intent.getStringExtra(EXTRA_LOGO_URL)
        val transitionName = intent.getStringExtra(EXTRA_TRANSITION_NAME)

        ViewCompat.setTransitionName(binding.enlargedLogoImage, transitionName)

        if (logoUrl != null) {
            Glide.with(this)
                .load(logoUrl)
                .dontAnimate()
                .dontTransform()
                .onlyRetrieveFromCache(true)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable?>,
                        isFirstResource: Boolean
                    ): Boolean {
                        supportStartPostponedEnterTransition()
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable?>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        supportStartPostponedEnterTransition()
                        return false
                    }
                })
                .into(binding.enlargedLogoImage)
        }

        binding.root.setOnClickListener {
            supportFinishAfterTransition()
        }
    }

    companion object {
        private const val EXTRA_LOGO_URL = "EXTRA_LOGO_URL"
        private const val EXTRA_TRANSITION_NAME = "EXTRA_TRANSITION_NAME"

        fun intent(context: Context, logoUrl: String, transitionName: String): Intent {
            val intent = Intent(context, EnlargedEasterEggLogoActivity::class.java)
            intent.putExtra(EXTRA_LOGO_URL, logoUrl)
            intent.putExtra(EXTRA_TRANSITION_NAME, transitionName)
            return intent
        }
    }
}
