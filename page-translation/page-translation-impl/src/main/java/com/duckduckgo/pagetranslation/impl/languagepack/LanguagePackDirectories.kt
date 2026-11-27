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

package com.duckduckgo.pagetranslation.impl.languagepack

import android.content.Context
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import java.io.File
import javax.inject.Inject

/** Where a pair's language pack lives: only a directory named after the pair, without a suffix, is an installed pack. */
interface LanguagePackDirectories {
    /** Where a download of [pair] is assembled before it's installed. */
    fun getStagingDirectory(pair: String): File

    /** Where [pair]'s installed pack lives. */
    fun getPackDirectory(pair: String): File

    /** Where [pair]'s pack waits after it was deleted, until it's restored or removed. */
    fun getOldDirectory(pair: String): File

    fun getInstalledPairs(): Set<String>

    fun getPairsWithOldDirectory(): Set<String>
}

@ContributesBinding(AppScope::class)
class RealLanguagePackDirectories @Inject constructor(
    private val context: Context,
) : LanguagePackDirectories {
    override fun getStagingDirectory(pair: String): File = getPacksDirectory().resolve(pair + STAGING_SUFFIX)

    override fun getPackDirectory(pair: String): File = getPacksDirectory().resolve(pair)

    override fun getOldDirectory(pair: String): File = getPacksDirectory().resolve(pair + OLD_SUFFIX)

    override fun getInstalledPairs(): Set<String> = getDirectoryNames().filterTo(mutableSetOf()) { '.' !in it }

    override fun getPairsWithOldDirectory(): Set<String> =
        getDirectoryNames().filter { it.endsWith(OLD_SUFFIX) }.mapTo(mutableSetOf()) { it.removeSuffix(OLD_SUFFIX) }

    private fun getDirectoryNames(): List<String> = getPacksDirectory().listFiles()?.filter { it.isDirectory }?.map { it.name }.orEmpty()

    private fun getPacksDirectory(): File = context.filesDir.resolve(PACKS_DIRECTORY)

    private companion object {
        const val PACKS_DIRECTORY = "page-translation/packs"
        const val STAGING_SUFFIX = ".tmp"
        const val OLD_SUFFIX = ".old"
    }
}
