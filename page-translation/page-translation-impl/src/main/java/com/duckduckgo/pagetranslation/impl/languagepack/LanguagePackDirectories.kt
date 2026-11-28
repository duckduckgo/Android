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
import com.duckduckgo.pagetranslation.impl.language.LanguagePair
import dev.zacsweers.metro.ContributesBinding
import java.io.File
import javax.inject.Inject

/** Where a pair's language pack lives: only a directory named after the pair, without a suffix, is an installed pack. */
interface LanguagePackDirectories {
    /** Where a download of [pair] is assembled before it's installed. */
    fun getStagingDirectory(pair: LanguagePair): File

    /** Where [pair]'s installed pack lives. */
    fun getPackDirectory(pair: LanguagePair): File

    /** Where [pair]'s pack waits after it was deleted, until it's restored or removed. */
    fun getOldDirectory(pair: LanguagePair): File

    fun getInstalledPairs(): Set<LanguagePair>

    fun getPairsWithOldDirectory(): Set<LanguagePair>
}

@ContributesBinding(AppScope::class)
class RealLanguagePackDirectories @Inject constructor(
    private val context: Context,
) : LanguagePackDirectories {
    override fun getStagingDirectory(pair: LanguagePair): File = getPacksDirectory().resolve(pair.key + STAGING_SUFFIX)

    override fun getPackDirectory(pair: LanguagePair): File = getPacksDirectory().resolve(pair.key)

    override fun getOldDirectory(pair: LanguagePair): File = getPacksDirectory().resolve(pair.key + OLD_SUFFIX)

    override fun getInstalledPairs(): Set<LanguagePair> = getDirectoryNames().filter { '.' !in it }.mapNotNullTo(
        mutableSetOf(),
        LanguagePair::fromKey,
    )

    override fun getPairsWithOldDirectory(): Set<LanguagePair> =
        getDirectoryNames().filter { it.endsWith(OLD_SUFFIX) }.mapNotNullTo(mutableSetOf()) { LanguagePair.fromKey(it.removeSuffix(OLD_SUFFIX)) }

    private fun getDirectoryNames(): List<String> = getPacksDirectory().listFiles()?.filter { it.isDirectory }?.map { it.name }.orEmpty()

    private fun getPacksDirectory(): File = context.filesDir.resolve(PACKS_DIRECTORY)

    private companion object {
        const val PACKS_DIRECTORY = "page-translation/packs"
        const val STAGING_SUFFIX = ".tmp"
        const val OLD_SUFFIX = ".old"
    }
}
