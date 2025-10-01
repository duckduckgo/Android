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

package com.duckduckgo.duckplayer.impl

import android.content.res.AssetManager
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

interface DuckPlayerLocalFilesPath {
    fun assetsPath(): List<String>
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealDuckPlayerLocalFilesPath @Inject constructor(
    private val assetManager: AssetManager,
    dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    @IsMainProcess private val isMainProcess: Boolean,
) : DuckPlayerLocalFilesPath {

    private var assetsPaths: List<String> = listOf()

    init {
        if (isMainProcess) {
            appCoroutineScope.launch(dispatcherProvider.io()) {
                assetsPaths = getAllAssetFilePaths("duckplayer")
            }
        }
    }

    override fun assetsPath(): List<String> = assetsPaths

    private fun getAllAssetFilePaths(directory: String): List<String> {
        val filePaths = mutableListOf<String>()
        val files = runCatching { assetManager.list(directory) }.getOrNull() ?: return emptyList()

        files.forEach {
            val fullPath = "$directory/$it"
            if (runCatching { assetManager.list(fullPath)?.isNotEmpty() }.getOrDefault(false) == true) {
                filePaths.addAll(getAllAssetFilePaths(fullPath))
            } else {
                filePaths.add(fullPath.removePrefix("duckplayer/"))
            }
        }
        return filePaths
    }
}
