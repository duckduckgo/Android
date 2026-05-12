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

package com.duckduckgo.duckchat.impl.helper

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

data class PendingNativeImage(
    val base64Data: String,
    val format: String,
)

data class PendingNativeFile(
    val base64Data: String,
    val fileName: String,
    val mimeType: String,
)

data class PendingNativePrompt(
    val prompt: String,
    val modelId: String?,
    val images: List<PendingNativeImage> = emptyList(),
    val files: List<PendingNativeFile> = emptyList(),
)

interface PendingNativePromptStore {
    fun store(
        prompt: String,
        modelId: String?,
        images: List<PendingNativeImage> = emptyList(),
        files: List<PendingNativeFile> = emptyList(),
    )
    fun consume(): PendingNativePrompt?
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealPendingNativePromptStore @Inject constructor() : PendingNativePromptStore {

    private val pending = AtomicReference<PendingNativePrompt?>(null)

    override fun store(
        prompt: String,
        modelId: String?,
        images: List<PendingNativeImage>,
        files: List<PendingNativeFile>,
    ) {
        pending.set(PendingNativePrompt(prompt, modelId, images, files))
    }

    override fun consume(): PendingNativePrompt? {
        return pending.getAndSet(null)
    }
}
