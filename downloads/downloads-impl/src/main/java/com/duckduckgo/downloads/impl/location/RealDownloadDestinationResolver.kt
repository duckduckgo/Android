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

package com.duckduckgo.downloads.impl.location

import android.net.Uri
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.downloads.api.DownloadDestination
import com.duckduckgo.downloads.api.DownloadDestinationResolver
import com.duckduckgo.downloads.api.DownloadLocationRepository
import com.duckduckgo.downloads.api.ResolvedDownloadDestination
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import logcat.logcat
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealDownloadDestinationResolver @Inject constructor(
    private val downloadLocationRepository: DownloadLocationRepository,
    private val safDownloadStorage: SafDownloadStorage,
) : DownloadDestinationResolver {

    override suspend fun resolve(): ResolvedDownloadDestination {
        val customLocation = downloadLocationRepository.getCustomLocation() ?: return defaultDestination()

        val treeUri = Uri.parse(customLocation.treeUri)
        if (!safDownloadStorage.isTreeAccessible(treeUri)) {
            logcat { "Custom download folder unavailable, falling back to default: ${customLocation.treeUri}" }
            return ResolvedDownloadDestination(
                destination = DownloadDestination.Default,
                usedFallback = true,
            )
        }

        return ResolvedDownloadDestination(
            destination = DownloadDestination.CustomTree(
                treeUri = customLocation.treeUri,
                displayLabel = customLocation.pathLabel,
            ),
        )
    }

    private fun defaultDestination() = ResolvedDownloadDestination(destination = DownloadDestination.Default)
}
