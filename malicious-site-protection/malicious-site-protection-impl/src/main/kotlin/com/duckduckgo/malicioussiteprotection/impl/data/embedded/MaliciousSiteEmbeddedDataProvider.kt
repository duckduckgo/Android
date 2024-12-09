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

package com.duckduckgo.malicioussiteprotection.impl.data.embedded

import android.content.Context
import androidx.annotation.RawRes
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.malicioussiteprotection.impl.R
import com.duckduckgo.malicioussiteprotection.impl.data.FilterSetResponse
import com.duckduckgo.malicioussiteprotection.impl.data.HashPrefixResponse
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import javax.inject.Inject
import timber.log.Timber

interface MaliciousSiteProtectionEmbeddedDataProvider {
    fun loadEmbeddedPhishingFilterSet(): FilterSetResponse?
    fun loadEmbeddedPhishingHashPrefixes(): HashPrefixResponse?
    fun loadEmbeddedMalwareFilterSet(): FilterSetResponse?
    fun loadEmbeddedMalwareHashPrefixes(): HashPrefixResponse?
}

@ContributesBinding(AppScope::class)
class RealMaliciousSiteProtectionEmbeddedDataProvider @Inject constructor(
    private val context: Context,
    private val moshi: Moshi,
) : MaliciousSiteProtectionEmbeddedDataProvider {

    private fun loadEmbeddedData(@RawRes file: Int): ByteArray {
        return context.resources.openRawResource(file).use { it.readBytes() }
    }

    override fun loadEmbeddedPhishingFilterSet(): FilterSetResponse? {
        return try {
            val filterSetData = loadEmbeddedData(R.raw.phishing_filter_set)
            val adapter = moshi.adapter(FilterSetResponse::class.java)
            adapter.fromJson(String(filterSetData))
        } catch (e: Exception) {
            null
        }
    }

    override fun loadEmbeddedPhishingHashPrefixes(): HashPrefixResponse? {
        return try {
            val hashPrefixData = loadEmbeddedData(R.raw.phishing_hash_prefix)
            val adapter = moshi.adapter(HashPrefixResponse::class.java)
            adapter.fromJson(String(hashPrefixData))
        } catch (e: Exception) {
            Timber.d("\uD83D\uDD34 Failed to fetch embedded phishing hash prefixes")
            null
        }
    }

    override fun loadEmbeddedMalwareFilterSet(): FilterSetResponse? {
        return try {
            val filterSetData = loadEmbeddedData(R.raw.malware_filter_set)
            val adapter = moshi.adapter(FilterSetResponse::class.java)
            adapter.fromJson(String(filterSetData))
        } catch (e: Exception) {
            Timber.d("\uD83D\uDD34 Failed to fetch embedded malware filter set")
            null
        }
    }

    override fun loadEmbeddedMalwareHashPrefixes(): HashPrefixResponse? {
        return try {
            val hashPrefixData = loadEmbeddedData(R.raw.malware_hash_prefix)
            val adapter = moshi.adapter(HashPrefixResponse::class.java)
            adapter.fromJson(String(hashPrefixData))
        } catch (e: Exception) {
            Timber.d("\uD83D\uDD34 Cris: Failed to fetch embedded malware hash prefixes")
            null
        }
    }
}
