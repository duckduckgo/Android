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
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

interface DuckPlayerLocalFilesPath {
    fun assetsPath(): List<String>
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealDuckPlayerLocalFilesPath @Inject constructor(
    private val assetManager: AssetManager,
    dispatcherProvider: DispatcherProvider,
) : DuckPlayerLocalFilesPath {

    private val assetsPaths: List<String> = runBlocking(dispatcherProvider.io()) { getAllAssetFilePaths("duckplayer") }

    override fun assetsPath(): List<String> = assetsPaths

    private fun getAllAssetFilePaths(directory: String): List<String> {
        val filePaths = mutableListOf<String>()
        val files = assetManager.list(directory) ?: return emptyList()

        files.forEach {
            val fullPath = "$directory/$it"
            if (assetManager.list(fullPath)?.isNotEmpty() == true) {
                filePaths.addAll(getAllAssetFilePaths(fullPath))
            } else {
                filePaths.add(fullPath.removePrefix("duckplayer/"))
            }
        }
        return filePaths
    }
}
