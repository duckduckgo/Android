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
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.io.IOException
import javax.inject.Inject

interface DuckPlayerLocalFilesPath {
    val assetsPath: List<String>
}

@ContributesBinding(AppScope::class)
class RealDuckPlayerLocalFilesPath @Inject constructor(private val assetManager: AssetManager) : DuckPlayerLocalFilesPath {

    override val assetsPath: List<String> = getAllAssetFilePaths("duckplayer")

    private fun getAllAssetFilePaths(directory: String): List<String> {
        val filePaths = mutableListOf<String>()
        val files = assetManager.list(directory) ?: return emptyList()

        files.forEach {
            val fullPath = "$directory/$it"
            try {
                assetManager.open(fullPath)
                filePaths.add(fullPath.removePrefix("duckplayer/"))
            } catch (e: IOException) {
                filePaths.addAll(getAllAssetFilePaths(fullPath))
            }
        }
        return filePaths
    }
}
