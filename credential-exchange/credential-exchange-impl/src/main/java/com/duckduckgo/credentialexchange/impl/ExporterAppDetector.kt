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

package com.duckduckgo.credentialexchange.impl

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.LogPriority.VERBOSE
import logcat.logcat
import javax.inject.Inject

/**
 * Finds apps that can export credentials over the OS credential transfer flow.
 *
 * The platform gives the importing side no way to ask directly, so this reads the exporting side of the
 * contract instead: an exporting app declares an activity for the IMPORT_CREDENTIALS action with a content scheme.
 * It's possible an app declares that but doesn't actually offer any credentials when asked at runtime, so this is a hint.
 */
interface ExporterAppDetector {
    /**
     * @return List of Android package names which declare themselves as credential exporters
     */
    suspend fun exporterApps(): List<String>
}

@ContributesBinding(AppScope::class)
class RealExporterAppDetector @Inject constructor(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
) : ExporterAppDetector {

    override suspend fun exporterApps(): List<String> = withContext(dispatchers.io()) {
        runCatching {
            // Google Play services declares a mime type as well as the content scheme, and an intent
            // only matches a filter if it has both. Other exporters may declare the scheme alone,
            // which matches only an intent with no type, so both shapes are asked for.
            val exporters = (findExportersForMime(MIME_TYPE_EXPORT) + findExportersForMime(null)).distinct()
            logcat(VERBOSE) { "${LOG_PREFIX}${exporters.size} apps can export credentials: $exporters" }
            exporters
        }.getOrElse {
            logcat(ERROR) { "${LOG_PREFIX}could not query exporter apps: ${it.message}" }
            emptyList()
        }
    }

    private fun findExportersForMime(mimeType: String?): List<String> {
        val intent = Intent(ACTION_IMPORT_CREDENTIALS).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            setDataAndType(CONTENT_PLACEHOLDER.toUri(), mimeType)
        }
        return context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo?.packageName }
            .filterNot { it == context.packageName }
            .distinct()
    }

    companion object {
        private const val ACTION_IMPORT_CREDENTIALS = "androidx.identitycredentials.action.IMPORT_CREDENTIALS"
        private const val MIME_TYPE_EXPORT = "application/octet-stream"
        private const val CONTENT_PLACEHOLDER = "content://placeholder"
    }
}
