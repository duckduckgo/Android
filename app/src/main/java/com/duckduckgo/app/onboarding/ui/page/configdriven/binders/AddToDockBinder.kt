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

package com.duckduckgo.app.onboarding.ui.page.configdriven.binders

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.view.View
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.IncludeBrandDesignAddToDockBinding
import com.duckduckgo.app.onboarding.ui.page.configdriven.BindScope
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentHandle
import com.duckduckgo.app.onboarding.ui.page.configdriven.DialogBinder
import com.duckduckgo.common.utils.extensions.preventWidows

class AddToDockBinder(
    private val binding: IncludeBrandDesignAddToDockBinding,
) : DialogBinder<ContentConfig.AddToDock> {

    override val view: View = binding.root

    private var videoPlayer: MediaPlayer? = null

    override fun bind(content: ContentConfig.AddToDock, scope: BindScope): ContentHandle = with(binding) {
        val context = root.context

        addToDockBody.text = content.body.resolve(context).preventWidows()
        setUpVideo()

        addToDockTitle.setTitle(content.title.resolve(context))

        ContentHandle(
            title = addToDockTitle,
            fadeTargets = listOf(addToDockBody, addToDockMedia),
            unbind = { releaseVideo() },
        )
    }

    private fun setUpVideo() = with(binding) {
        addToDockPreviewVideo.setVideoSize(VIDEO_WIDTH, VIDEO_HEIGHT)
        addToDockPreviewVideo.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int,
            ) {
                playVideo(surface)
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int,
            ) = Unit

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                releaseVideo()
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        }
    }

    private fun playVideo(surfaceTexture: SurfaceTexture) {
        releaseVideo()
        videoPlayer = MediaPlayer().apply {
            setSurface(Surface(surfaceTexture))
            binding.root.context.resources.openRawResourceFd(R.raw.onboarding_add_to_home_screen_tutorial).use { setDataSource(it) }
            isLooping = true
            setVolume(0f, 0f)
            setOnVideoSizeChangedListener { _, width, height -> binding.addToDockPreviewVideo.setVideoSize(width, height) }
            setOnPreparedListener { it.start() }
            prepareAsync()
        }
    }

    private fun releaseVideo() {
        videoPlayer?.release()
        videoPlayer = null
    }

    private companion object {
        // Seeds AspectRatioTextureView's first measurement before MediaPlayer reports the real size: at
        // wrap_content a zero height never produces a surface, so playback would never start.
        const val VIDEO_WIDTH = 1080
        const val VIDEO_HEIGHT = 944
    }
}
